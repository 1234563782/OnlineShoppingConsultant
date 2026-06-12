package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based compare intent and target extraction when LLM output is incomplete.
 */
@Component
public class CompareIntentDetector {

    private static final Pattern COMPARE_MESSAGE_PATTERN = Pattern.compile(
            "对比|比较|哪个好|哪个更好|哪个划算|选哪个|哪款好|哪款更|区别|差异|比一下|对比一下"
    );

    private static final Pattern VAGUE_COMPARE_PATTERN = Pattern.compile(
            "^(帮)?(我)?(都|一下)?(对比|比较)(一下|看看|下)?[。！？!?]*$"
    );

    private static final Pattern ORDINAL_CN_PATTERN = Pattern.compile(
            "第([一二三四五六1-6])(?:个|款|台|项|种)?"
    );

    private static final Map<Character, Integer> CN_DIGITS = Map.of(
            '一', 1, '二', 2, '三', 3, '四', 4, '五', 5, '六', 6
    );

    private static final Pattern PRODUCT_PAIR_PATTERN = Pattern.compile(
            "(.+?)[和与](.+?)(?:哪个好|哪个更好|哪个划算|谁更好|谁更|怎么选|选哪个|比(?:较|一下)|对比)"
    );

    public void reconcileComparePatch(
            String userMessage,
            Map<String, Object> extractedPatch,
            Map<String, Object> sessionContext
    ) {
        if (extractedPatch == null || userMessage == null || userMessage.isBlank()) {
            return;
        }
        boolean compareMessage = isCompareMessage(userMessage, sessionContext);
        if (!compareMessage) {
            return;
        }

        extractedPatch.put(SessionContextKeys.SHOPPING_SUB_INTENT, SessionContextKeys.SUB_INTENT_COMPARE);
        Map<String, Object> compareTargets = ensureCompareTargets(extractedPatch);

        mergeOrdinalRefs(compareTargets, extractOrdinalRefsFromMessage(userMessage));
        mergeProductNames(compareTargets, extractProductNamesFromMessage(userMessage));

        if (isVagueCompare(userMessage) && lastRecommendationCount(sessionContext) >= 2) {
            mergeOrdinalRefs(compareTargets, defaultOrdinalRefs(sessionContext, 2));
        }
    }

    public boolean isCompareMessage(String userMessage, Map<String, Object> sessionContext) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String trimmed = userMessage.trim();
        if (COMPARE_MESSAGE_PATTERN.matcher(trimmed).find()) {
            return true;
        }
        if (isVagueCompare(trimmed) && lastRecommendationCount(sessionContext) >= 2) {
            return true;
        }
        return PRODUCT_PAIR_PATTERN.matcher(trimmed).find();
    }

    @SuppressWarnings("unchecked")
    public List<Integer> extractOrdinalRefsFromMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        Set<Integer> ordinals = new LinkedHashSet<>();
        Matcher matcher = ORDINAL_CN_PATTERN.matcher(userMessage);
        while (matcher.find()) {
            parseOrdinalToken(matcher.group(1)).ifPresent(ordinals::add);
        }
        return new ArrayList<>(ordinals);
    }

    public List<String> extractProductNamesFromMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        String trimmed = userMessage.trim();
        List<String> names = new ArrayList<>();

        Matcher pairMatcher = PRODUCT_PAIR_PATTERN.matcher(trimmed);
        if (pairMatcher.find()) {
            addNameIfPresent(names, pairMatcher.group(1));
            addNameIfPresent(names, pairMatcher.group(2));
            return names;
        }

        if (trimmed.contains("和") || trimmed.contains("与")) {
            String cleaned = trimmed.replaceAll("(哪个好|哪个更好|哪个划算|谁更好|怎么选|选哪个|比(?:较|一下)|对比).*$", "");
            String[] parts = cleaned.split("[和与]");
            for (String part : parts) {
                addNameIfPresent(names, part);
            }
        }
        return names;
    }

    private boolean isVagueCompare(String userMessage) {
        return VAGUE_COMPARE_PATTERN.matcher(userMessage.trim()).matches()
                || "对比一下".equals(userMessage.trim())
                || "比较一下".equals(userMessage.trim())
                || "对比".equals(userMessage.trim())
                || "比较".equals(userMessage.trim());
    }

    private List<Integer> defaultOrdinalRefs(Map<String, Object> sessionContext, int count) {
        int available = lastRecommendationCount(sessionContext);
        int limit = Math.min(count, available);
        List<Integer> ordinals = new ArrayList<>();
        for (int i = 1; i <= limit; i++) {
            ordinals.add(i);
        }
        return ordinals;
    }

    @SuppressWarnings("unchecked")
    private int lastRecommendationCount(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return 0;
        }
        Object raw = sessionContext.get(SessionContextKeys.LAST_RECOMMENDATIONS);
        if (!(raw instanceof List<?> list)) {
            return 0;
        }
        return list.size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureCompareTargets(Map<String, Object> extractedPatch) {
        Object existing = extractedPatch.get(SessionContextKeys.COMPARE_TARGETS);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> compareTargets = new HashMap<>();
        compareTargets.put("productNames", new ArrayList<String>());
        compareTargets.put("ordinalRefs", new ArrayList<Integer>());
        compareTargets.put("skuIds", new ArrayList<String>());
        extractedPatch.put(SessionContextKeys.COMPARE_TARGETS, compareTargets);
        return compareTargets;
    }

    @SuppressWarnings("unchecked")
    private void mergeOrdinalRefs(Map<String, Object> compareTargets, List<Integer> incoming) {
        List<Integer> target = (List<Integer>) compareTargets.computeIfAbsent("ordinalRefs", key -> new ArrayList<>());
        for (Integer ordinal : incoming) {
            if (ordinal != null && ordinal > 0 && !target.contains(ordinal)) {
                target.add(ordinal);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeProductNames(Map<String, Object> compareTargets, List<String> incoming) {
        List<String> target = (List<String>) compareTargets.computeIfAbsent("productNames", key -> new ArrayList<>());
        for (String name : incoming) {
            if (name != null && !name.isBlank() && !target.contains(name)) {
                target.add(name.trim());
            }
        }
    }

    private void addNameIfPresent(List<String> names, String raw) {
        if (raw == null) {
            return;
        }
        String cleaned = raw.trim()
                .replaceAll("^[，,。！？!?\\s]+", "")
                .replaceAll("[，,。！？!?\\s]+$", "");
        if (!cleaned.isBlank() && cleaned.length() >= 2 && !isOrdinalOnlyName(cleaned)) {
            names.add(cleaned);
        }
    }

    private boolean isOrdinalOnlyName(String value) {
        return value != null && Pattern.compile("^第[一二三四五六1-6](?:个|款|台|项|种)?$").matcher(value.trim()).matches();
    }

    private java.util.Optional<Integer> parseOrdinalToken(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        if (token.length() == 1 && Character.isDigit(token.charAt(0))) {
            return java.util.Optional.of(token.charAt(0) - '0');
        }
        if (token.length() == 1 && CN_DIGITS.containsKey(token.charAt(0))) {
            return java.util.Optional.of(CN_DIGITS.get(token.charAt(0)));
        }
        try {
            return java.util.Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }
}
