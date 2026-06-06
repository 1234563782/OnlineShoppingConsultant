package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MemoryMergeService {

    /**
     * Filters agent memoryPatch: whitelist fields, no override of extraction, grounded check.
     */
    public Map<String, Object> sanitizeAgentPatch(
            Map<String, Object> agentPatch,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (agentPatch == null || agentPatch.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        if (!hasListValue(extractionPatch.get("brandPreferences"))) {
            List<String> grounded = filterGroundedListItems(
                    ProfileListNormalizer.normalizeList(agentPatch.get("brandPreferences")),
                    effectiveContext,
                    extractionPatch,
                    extractedPatch,
                    userMessage,
                    "brandPreferences"
            );
            if (!grounded.isEmpty()) {
                sanitized.put("brandPreferences", grounded);
            }
        }
        if (!hasListValue(extractionPatch.get("dislikes"))) {
            List<String> grounded = filterGroundedListItems(
                    ProfileListNormalizer.normalizeList(agentPatch.get("dislikes")),
                    effectiveContext,
                    extractionPatch,
                    extractedPatch,
                    userMessage,
                    "dislikes"
            );
            if (!grounded.isEmpty()) {
                sanitized.put("dislikes", grounded);
            }
        }
        if (!hasListValue(extractionPatch.get("notes"))) {
            List<String> grounded = filterGroundedListItems(
                    ProfileListNormalizer.normalizeList(agentPatch.get("notes")),
                    effectiveContext,
                    extractionPatch,
                    extractedPatch,
                    userMessage,
                    "notes"
            );
            if (!grounded.isEmpty()) {
                sanitized.put("notes", grounded);
            }
        }
        return sanitized;
    }

    /**
     * Merges orchestrator extraction patch with sanitized agent patch. Extraction wins on scalar fields.
     */
    public Map<String, Object> mergeForProfile(
            Map<String, Object> extractionPatch,
            Map<String, Object> sanitizedAgentPatch
    ) {
        Map<String, Object> extraction = extractionPatch == null ? Map.of() : extractionPatch;
        Map<String, Object> agent = sanitizedAgentPatch == null ? Map.of() : sanitizedAgentPatch;
        Map<String, Object> merged = new HashMap<>();
        putMergedList(merged, extraction, agent, "brandPreferences");
        putMergedList(merged, extraction, agent, "dislikes");
        putMergedList(merged, extraction, agent, "notes");
        return merged;
    }

    /**
     * Derives long-term preference patch from session-level mustHave / positive user message.
     * Session "我喜欢入耳式" lands in mustHave, not longTermMemoryPatch — this bridges that gap.
     */
    public Map<String, Object> deriveSessionPreferencePatch(
            Map<String, Object> effectiveContext,
            Map<String, Object> extractedPatch,
            Map<String, Object> existingProfile,
            String userMessage
    ) {
        Map<String, Object> patch = new HashMap<>();
        List<String> mustHave = ProfileListNormalizer.normalizeList(extractedPatch == null ? null : extractedPatch.get("mustHave"));
        if (mustHave.isEmpty() && effectiveContext != null) {
            mustHave = ProfileListNormalizer.normalizeList(effectiveContext.get("mustHave"));
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
        List<String> mustHave = effectiveContext == null
                ? List.of()
                : ProfileListNormalizer.normalizeList(effectiveContext.get("mustHave"));
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
            Map<String, Object> extraction,
            Map<String, Object> agent,
            String key
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(ProfileListNormalizer.normalizeList(extraction.get(key)));
        values.addAll(ProfileListNormalizer.normalizeList(agent.get(key)));
        if (!values.isEmpty()) {
            target.put(key, new ArrayList<>(values));
        }
    }

    private List<String> filterGroundedListItems(
            List<String> items,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage,
            String fieldName
    ) {
        List<String> grounded = new ArrayList<>();
        for (String item : items) {
            if (isListItemGrounded(item, effectiveContext, extractionPatch, extractedPatch, userMessage, fieldName)) {
                grounded.add(item);
            }
        }
        return grounded;
    }

    private boolean isListItemGrounded(
            String item,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage,
            String fieldName
    ) {
        if (item == null || item.isBlank()) {
            return false;
        }
        String normalizedItem = item.trim();
        if (containsIgnoreCase(userMessage, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(effectiveContext, fieldName, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(extractionPatch, fieldName, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(extractedPatch, fieldName, normalizedItem)) {
            return true;
        }
        Map<String, Object> profile = longTermProfileReference(effectiveContext);
        return listContainsIgnoreCase(profile, fieldName, normalizedItem);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> longTermProfileReference(Map<String, Object> effectiveContext) {
        if (effectiveContext == null) {
            return Map.of();
        }
        Object reference = effectiveContext.get("longTermProfileReference");
        if (reference instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean listContainsIgnoreCase(Map<String, Object> source, String fieldName, String item) {
        return ProfileListNormalizer.normalizeList(source == null ? null : source.get(fieldName)).stream()
                .anyMatch(value -> equalsIgnoreCase(value, item));
    }

    private boolean hasListValue(Object value) {
        return !ProfileListNormalizer.normalizeList(value).isEmpty();
    }

    private boolean containsIgnoreCase(String text, String needle) {
        return text != null
                && needle != null
                && !needle.isBlank()
                && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
