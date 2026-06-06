package com.onlineshopping.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
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

    public ProfileReconcileService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
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
        String prompt = """
                你是电商用户长期画像整理器。请整理 brandPreferences、dislikes、notes 三个字段。
                
                规则：
                1. 只能在“已有画像 + 本轮新增 + 候选合并列表”范围内选择或改写，禁止编造用户未表达过的偏好。
                2. 保留互不矛盾的条目；语义矛盾时以用户本轮原话为准，删除被推翻的旧条目。
                3. 需要跨字段检查矛盾，例如：
                   - notes 说“喜欢/偏好入耳式”，dislikes 含“入耳式” → 去掉矛盾项，以本轮表达为准
                   - brandPreferences 与 dislikes 出现同一品牌 → 保留更符合用户本轮原话的一侧
                   - 两条 notes 语义矛盾 → 保留较新、更明确的一条
                4. notes 必须是独立短句数组，每条只表达一个稳定偏好或注意事项，不要写本次预算/本次临时场景。
                5. 每个字段最多保留 %d 条；超出时保留最重要、与用户本轮最相关的条目。
                6. 只输出严格 JSON，不要 markdown，不要解释。
                
                JSON schema:
                {
                  "brandPreferences":["string"],
                  "dislikes":["string"],
                  "notes":["string"],
                  "removedItems":{
                    "brandPreferences":["string"],
                    "dislikes":["string"],
                    "notes":["string"]
                  }
                }
                
                已有长期画像：
                %s
                
                本轮新增 patch：
                %s
                
                候选合并列表（可增删，不可凭空新增）：
                {
                  "brandPreferences": %s,
                  "dislikes": %s,
                  "notes": %s
                }
                
                用户本轮原话：
                %s
                """.formatted(
                MAX_ITEMS_PER_FIELD,
                existingProfile,
                incomingPatch,
                candidateBrands,
                candidateDislikes,
                candidateNotes,
                userMessage
        );

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
