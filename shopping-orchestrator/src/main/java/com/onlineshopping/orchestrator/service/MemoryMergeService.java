package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MemoryMergeService {

    public Map<String, Object> mergeForProfile(
            Map<String, Object> extractionPatch,
            Map<String, Object> sessionPatch
    ) {
        Map<String, Object> extraction = extractionPatch == null ? Map.of() : extractionPatch;
        Map<String, Object> session = sessionPatch == null ? Map.of() : sessionPatch;
        Map<String, Object> merged = new HashMap<>();
        putMergedList(merged, extraction, session, "brandPreferences");
        putMergedList(merged, extraction, session, "dislikes");
        putMergedList(merged, extraction, session, "notes");
        return merged;
    }

    /**
     * Derives long-term preference patch from session-level mustHave / positive user message.
     */
    public Map<String, Object> deriveSessionPreferencePatch(
            Map<String, Object> effectiveContext,
            Map<String, Object> extractedPatch,
            Map<String, Object> existingProfile,
            String userMessage
    ) {
        Map<String, Object> constraints = resolvedConstraints(effectiveContext);
        Map<String, Object> patch = new HashMap<>();
        List<String> mustHave = ProfileListNormalizer.normalizeList(extractedPatch == null ? null : extractedPatch.get("mustHave"));
        if (mustHave.isEmpty()) {
            mustHave = ProfileListNormalizer.normalizeList(constraints.get("mustHave"));
        }
        List<String> positiveNotes = new ArrayList<>();
        for (String item : mustHave) {
            if (item != null && !item.isBlank()) {
                positiveNotes.add("偏好" + item.trim());
            }
        }
        List<String> profileDislikes = ProfileListNormalizer.normalizeList(
                existingProfile == null ? null : existingProfile.get("dislikes"));
        for (String dislike : profileDislikes) {
            if (userExpressesPreferenceFor(userMessage, dislike)) {
                positiveNotes.add("偏好" + dislike.trim());
            }
        }
        if (!positiveNotes.isEmpty()) {
            patch.put("notes", dedupePreserveOrder(positiveNotes));
        }
        return patch;
    }

    public boolean sessionContradictsProfile(
            Map<String, Object> existingProfile,
            Map<String, Object> effectiveContext,
            String userMessage
    ) {
        if (existingProfile == null || existingProfile.isEmpty()) {
            return false;
        }
        List<String> profileDislikes = ProfileListNormalizer.normalizeList(existingProfile.get("dislikes"));
        if (profileDislikes.isEmpty()) {
            return false;
        }
        Map<String, Object> constraints = resolvedConstraints(effectiveContext);
        List<String> mustHave = ProfileListNormalizer.normalizeList(constraints.get("mustHave"));
        for (String must : mustHave) {
            for (String dislike : profileDislikes) {
                if (textsOverlap(must, dislike)) {
                    return true;
                }
            }
        }
        for (String dislike : profileDislikes) {
            if (userExpressesPreferenceFor(userMessage, dislike)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvedConstraints(Map<String, Object> effectiveContext) {
        if (effectiveContext == null) {
            return Map.of();
        }
        Object resolved = effectiveContext.get("resolvedConstraints");
        if (resolved instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return effectiveContext;
    }

    private boolean userExpressesPreferenceFor(String userMessage, String term) {
        if (userMessage == null || userMessage.isBlank() || term == null || term.isBlank()) {
            return false;
        }
        String trimmed = term.trim();
        if (!ProfileListNormalizer.containsIgnoreCase(userMessage, trimmed)) {
            return false;
        }
        return userMessage.contains("喜欢")
                || userMessage.contains("偏好")
                || userMessage.contains("要")
                || userMessage.contains("想要")
                || userMessage.contains("倾向")
                || userMessage.contains("希望");
    }

    private List<String> dedupePreserveOrder(List<String> values) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                set.add(value.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private boolean textsOverlap(String left, String right) {
        return ProfileListNormalizer.containsIgnoreCase(left, right)
                || ProfileListNormalizer.containsIgnoreCase(right, left);
    }

    private void putMergedList(
            Map<String, Object> target,
            Map<String, Object> left,
            Map<String, Object> right,
            String key
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(ProfileListNormalizer.normalizeList(left.get(key)));
        values.addAll(ProfileListNormalizer.normalizeList(right.get(key)));
        if (!values.isEmpty()) {
            target.put(key, new ArrayList<>(values));
        }
    }
}
