package com.onlineshopping.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import com.onlineshopping.prompt.PromptTemplateService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ProfileReconcileService {

    private static final int MAX_ITEMS_PER_FIELD = 10;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    public ProfileReconcileService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService
    ) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * Reconciles brandPreferences, dislikes and notes across existing profile and incoming patch (P2).
     */
    public Map<String, Object> reconcile(
            Map<String, Object> existingProfile,
            Map<String, Object> incomingPatch,
            String userMessage
    ) {
        if (!ProfileListNormalizer.hasPreferenceIncoming(incomingPatch)) {
            return Map.of();
        }

        Map<String, Object> existing = existingProfile == null ? Map.of() : existingProfile;
        List<String> candidateBrands = ProfileListNormalizer.union(
                ProfileListNormalizer.normalizeList(existing.get("brandPreferences")),
                ProfileListNormalizer.normalizeList(incomingPatch.get("brandPreferences"))
        );
        List<String> candidateDislikes = ProfileListNormalizer.union(
                ProfileListNormalizer.normalizeList(existing.get("dislikes")),
                ProfileListNormalizer.normalizeList(incomingPatch.get("dislikes"))
        );
        List<String> candidateNotes = ProfileListNormalizer.union(
                ProfileListNormalizer.normalizeList(existing.get("notes")),
                ProfileListNormalizer.normalizeList(incomingPatch.get("notes"))
        );

        try {
            Map<String, Object> llmResult = reconcileWithLlm(
                    existing,
                    incomingPatch,
                    candidateBrands,
                    candidateDislikes,
                    candidateNotes,
                    userMessage
            );
            return toReplacePatch(
                    ProfileListNormalizer.normalizeList(llmResult.get("brandPreferences")),
                    ProfileListNormalizer.normalizeList(llmResult.get("dislikes")),
                    ProfileListNormalizer.normalizeList(llmResult.get("notes"))
            );
        } catch (Exception ignored) {
            return toReplacePatch(candidateBrands, candidateDislikes, candidateNotes);
        }
    }

    private Map<String, Object> reconcileWithLlm(
            Map<String, Object> existingProfile,
            Map<String, Object> incomingPatch,
            List<String> candidateBrands,
            List<String> candidateDislikes,
            List<String> candidateNotes,
            String userMessage
    ) throws Exception {
        String prompt = promptTemplateService.render(
                "profile-reconcile",
                Map.of(
                        "maxItemsPerField", MAX_ITEMS_PER_FIELD,
                        "existingProfile", existingProfile,
                        "incomingPatch", incomingPatch,
                        "candidateBrands", candidateBrands,
                        "candidateDislikes", candidateDislikes,
                        "candidateNotes", candidateNotes,
                        "userMessage", userMessage
                )
        ).content();

        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        Map<String, Object> parsed = parseJsonObject(content);
        enforceCandidateBoundaries(parsed, candidateBrands, candidateDislikes, candidateNotes, userMessage);
        return parsed;
    }

    private void enforceCandidateBoundaries(
            Map<String, Object> parsed,
            List<String> candidateBrands,
            List<String> candidateDislikes,
            List<String> candidateNotes,
            String userMessage
    ) {
        parsed.put("brandPreferences", filterAllowedItems(
                ProfileListNormalizer.normalizeList(parsed.get("brandPreferences")),
                candidateBrands,
                userMessage
        ));
        parsed.put("dislikes", filterAllowedItems(
                ProfileListNormalizer.normalizeList(parsed.get("dislikes")),
                candidateDislikes,
                userMessage
        ));
        parsed.put("notes", filterAllowedItems(
                ProfileListNormalizer.normalizeList(parsed.get("notes")),
                candidateNotes,
                userMessage
        ));
    }

    private List<String> filterAllowedItems(List<String> selected, List<String> candidates, String userMessage) {
        if (selected.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : selected) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String trimmed = item.trim();
            if (matchesCandidates(trimmed, candidates) || ProfileListNormalizer.containsIgnoreCase(userMessage, trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean matchesCandidates(String item, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(item)
                    || ProfileListNormalizer.containsIgnoreCase(candidate, item)
                    || ProfileListNormalizer.containsIgnoreCase(item, candidate)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> toReplacePatch(
            List<String> brandPreferences,
            List<String> dislikes,
            List<String> notes
    ) {
        Map<String, Object> patch = new HashMap<>();
        patch.put("brandPreferences", trimSize(dedupePreserveOrder(brandPreferences)));
        patch.put("dislikes", trimSize(dedupePreserveOrder(dislikes)));
        patch.put("notes", trimSize(dedupePreserveOrder(notes)));
        patch.put(ProfileListNormalizer.RECONCILE_REPLACE_KEY, true);
        return patch;
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

    private List<String> trimSize(List<String> values) {
        if (values.size() <= MAX_ITEMS_PER_FIELD) {
            return values;
        }
        return new ArrayList<>(values.subList(0, MAX_ITEMS_PER_FIELD));
    }

    private Map<String, Object> parseJsonObject(String content) throws Exception {
        if (content == null || content.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(content, new TypeReference<>() {
            });
        } catch (Exception first) {
            Matcher matcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
            if (matcher.find()) {
                return objectMapper.readValue(matcher.group(), new TypeReference<>() {
                });
            }
            throw first;
        }
    }
}
