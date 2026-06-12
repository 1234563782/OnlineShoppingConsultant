package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompareTargetResolver {

    private static final Pattern SKU_PATTERN = Pattern.compile("SKU\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ORDINAL_ONLY_NAME = Pattern.compile("^第[一二三四五六1-6](?:个|款|台|项|种)?$");

    private final CatalogSearchClientService catalogSearchClientService;
    private final CompareIntentDetector compareIntentDetector;

    public CompareTargetResolver(
            CatalogSearchClientService catalogSearchClientService,
            CompareIntentDetector compareIntentDetector
    ) {
        this.catalogSearchClientService = catalogSearchClientService;
        this.compareIntentDetector = compareIntentDetector;
    }

    public ResolvedCompareTargets resolve(
            Map<String, Object> sessionContext,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        Map<String, Object> compareTargets = mergedCompareTargets(sessionContext, extractedPatch, userMessage);
        List<Map<String, Object>> lastRecommendations = lastRecommendations(sessionContext);

        Set<String> skuIds = new LinkedHashSet<>();
        skuIds.addAll(normalizeStringList(compareTargets.get("skuIds")));
        skuIds.addAll(extractSkuIdsFromMessage(userMessage));
        skuIds.addAll(resolveOrdinalRefs(lastRecommendations, compareTargets));
        skuIds.addAll(resolveProductNames(sessionContext, lastRecommendations, compareTargets));
        skuIds.addAll(resolveNamesMentionedInMessage(userMessage, lastRecommendations));

        List<String> focusDimensions = extractFocusDimensions(extractedPatch, sessionContext);
        return new ResolvedCompareTargets(new ArrayList<>(skuIds), focusDimensions);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergedCompareTargets(
            Map<String, Object> sessionContext,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("productNames", new ArrayList<String>());
        merged.put("ordinalRefs", new ArrayList<Integer>());
        merged.put("skuIds", new ArrayList<String>());

        mergeCompareTargetList(merged, compareTargetsMap(sessionContext));
        mergeCompareTargetList(merged, compareTargetsMap(extractedPatch));

        List<Integer> messageOrdinals = compareIntentDetector.extractOrdinalRefsFromMessage(userMessage);
        mergeOrdinalRefs(merged, messageOrdinals);

        List<String> messageNames = compareIntentDetector.extractProductNamesFromMessage(userMessage);
        mergeProductNames(merged, messageNames);

        return merged;
    }

    private List<String> resolveNamesMentionedInMessage(
            String userMessage,
            List<Map<String, Object>> lastRecommendations
    ) {
        if (userMessage == null || userMessage.isBlank() || lastRecommendations.isEmpty()) {
            return List.of();
        }
        String normalizedMessage = normalizeToken(userMessage);
        List<String> resolved = new ArrayList<>();
        for (Map<String, Object> item : lastRecommendations) {
            Object name = item.get("name");
            Object skuId = item.get("skuId");
            if (name == null || skuId == null) {
                continue;
            }
            String normalizedName = normalizeToken(name.toString());
            if (normalizedName.length() >= 4 && normalizedMessage.contains(normalizedName)) {
                resolved.add(skuId.toString());
            }
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compareTargetsMap(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        Object compareTargets = source.get(SessionContextKeys.COMPARE_TARGETS);
        if (compareTargets instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private void mergeCompareTargetList(Map<String, Object> target, Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        List<String> productNames = (List<String>) target.get("productNames");
        for (String name : normalizeStringList(incoming.get("productNames"))) {
            if (!productNames.contains(name)) {
                productNames.add(name);
            }
        }
        List<Integer> ordinalRefs = (List<Integer>) target.get("ordinalRefs");
        for (Integer ordinal : normalizeOrdinalList(incoming.get("ordinalRefs"))) {
            if (!ordinalRefs.contains(ordinal)) {
                ordinalRefs.add(ordinal);
            }
        }
        List<String> skuIds = (List<String>) target.get("skuIds");
        for (String skuId : normalizeStringList(incoming.get("skuIds"))) {
            if (!skuIds.contains(skuId)) {
                skuIds.add(skuId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeOrdinalRefs(Map<String, Object> target, List<Integer> incoming) {
        List<Integer> ordinalRefs = (List<Integer>) target.get("ordinalRefs");
        for (Integer ordinal : incoming) {
            if (ordinal != null && ordinal > 0 && !ordinalRefs.contains(ordinal)) {
                ordinalRefs.add(ordinal);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeProductNames(Map<String, Object> target, List<String> incoming) {
        List<String> productNames = (List<String>) target.get("productNames");
        for (String name : incoming) {
            if (name != null && !name.isBlank() && !productNames.contains(name)) {
                productNames.add(name.trim());
            }
        }
    }

    private List<String> resolveOrdinalRefs(
            List<Map<String, Object>> lastRecommendations,
            Map<String, Object> compareTargets
    ) {
        List<Integer> ordinalRefs = normalizeOrdinalList(compareTargets.get("ordinalRefs"));
        if (ordinalRefs.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (Integer ordinal : ordinalRefs) {
            if (ordinal == null || ordinal < 1) {
                continue;
            }
            int index = ordinal - 1;
            if (index >= lastRecommendations.size()) {
                continue;
            }
            Object skuId = lastRecommendations.get(index).get("skuId");
            if (skuId != null && !skuId.toString().isBlank()) {
                resolved.add(skuId.toString().trim());
            }
        }
        return resolved;
    }

    private List<String> resolveProductNames(
            Map<String, Object> sessionContext,
            List<Map<String, Object>> lastRecommendations,
            Map<String, Object> compareTargets
    ) {
        List<String> productNames = normalizeStringList(compareTargets.get("productNames"));
        if (productNames.isEmpty()) {
            return List.of();
        }

        String categoryId = stringValue(sessionContext == null ? null : sessionContext.get(SessionContextKeys.CATEGORY_ID));
        List<String> resolved = new ArrayList<>();
        for (String productName : productNames) {
            if (isOrdinalOnlyName(productName)) {
                continue;
            }
            String skuId = resolveProductNameToSku(productName, categoryId, lastRecommendations);
            if (skuId != null) {
                resolved.add(skuId);
            }
        }
        return resolved;
    }

    private String resolveProductNameToSku(
            String productName,
            String categoryId,
            List<Map<String, Object>> lastRecommendations
    ) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        String fromLast = matchLastRecommendations(productName, lastRecommendations);
        if (fromLast != null) {
            return fromLast;
        }

        Map<String, Object> searchParams = new LinkedHashMap<>();
        if (categoryId != null && !categoryId.isBlank()) {
            searchParams.put("categoryId", categoryId);
        }
        searchParams.put("keyword", productName.trim());
        searchParams.put("semanticQuery", productName.trim());
        searchParams.put("limit", 8);
        var searchResult = catalogSearchClientService.search(searchParams);
        if (searchResult == null || !searchResult.isUsable() || searchResult.products() == null || searchResult.products().isEmpty()) {
            return null;
        }

        String normalizedQuery = normalizeToken(productName);
        String bestSku = null;
        int bestScore = -1;
        for (Map<String, Object> product : searchResult.products()) {
            Object name = product.get("name");
            if (name == null) {
                continue;
            }
            int score = nameMatchScore(normalizedQuery, normalizeToken(name.toString()));
            if (score > bestScore) {
                bestScore = score;
                Object skuId = product.get("skuId");
                bestSku = skuId == null ? null : skuId.toString();
            }
        }
        return bestScore > 0 ? bestSku : null;
    }

    private String matchLastRecommendations(String productName, List<Map<String, Object>> lastRecommendations) {
        String normalizedQuery = normalizeToken(productName);
        String bestSku = null;
        int bestScore = -1;
        for (Map<String, Object> item : lastRecommendations) {
            Object name = item.get("name");
            Object skuId = item.get("skuId");
            if (name == null || skuId == null) {
                continue;
            }
            int score = nameMatchScore(normalizedQuery, normalizeToken(name.toString()));
            if (score > bestScore) {
                bestScore = score;
                bestSku = skuId.toString();
            }
        }
        return bestScore > 0 ? bestSku : null;
    }

    private int nameMatchScore(String query, String candidate) {
        if (query.isBlank() || candidate.isBlank()) {
            return 0;
        }
        if (query.equals(candidate)) {
            return 100;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            return 80;
        }
        int overlap = longestCommonSubstringLength(query, candidate);
        if (overlap >= Math.min(4, Math.min(query.length(), candidate.length()))) {
            return 60 + overlap;
        }
        return 0;
    }

    private int longestCommonSubstringLength(String a, String b) {
        int max = 0;
        for (int i = 0; i < a.length(); i++) {
            for (int j = 0; j < b.length(); j++) {
                int len = 0;
                while (i + len < a.length() && j + len < b.length() && a.charAt(i + len) == b.charAt(j + len)) {
                    len++;
                }
                max = Math.max(max, len);
            }
        }
        return max;
    }

    private List<String> extractFocusDimensions(Map<String, Object> extractedPatch, Map<String, Object> sessionContext) {
        List<String> focus = normalizeStringList(
                extractedPatch == null ? null : extractedPatch.get(SessionContextKeys.COMPARE_FOCUS)
        );
        if (!focus.isEmpty()) {
            return focus;
        }
        return normalizeStringList(sessionContext == null ? null : sessionContext.get(SessionContextKeys.COMPARE_FOCUS));
    }

    private List<String> extractSkuIdsFromMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        Matcher matcher = SKU_PATTERN.matcher(userMessage);
        List<String> skuIds = new ArrayList<>();
        while (matcher.find()) {
            skuIds.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return skuIds;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> lastRecommendations(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return List.of();
        }
        Object raw = sessionContext.get(SessionContextKeys.LAST_RECOMMENDATIONS);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String value = item.toString().trim();
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private List<Integer> normalizeOrdinalList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> ordinals = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                ordinals.add(number.intValue());
            } else if (item != null) {
                try {
                    ordinals.add(Integer.parseInt(item.toString().trim()));
                } catch (NumberFormatException ignored) {
                    // skip invalid ordinal
                }
            }
        }
        return ordinals;
    }

    private String normalizeToken(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean isOrdinalOnlyName(String value) {
        return value != null && ORDINAL_ONLY_NAME.matcher(value.trim()).matches();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record ResolvedCompareTargets(List<String> skuIds, List<String> focusDimensions) {
    }
}
