package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Service
public class CatalogSearchPrefetchService {

    private final CatalogSearchClientService catalogSearchClientService;

    @Value("${shopping.catalog.search-limit:5}")
    private int searchLimit;

    public CatalogSearchPrefetchService(CatalogSearchClientService catalogSearchClientService) {
        this.catalogSearchClientService = catalogSearchClientService;
    }

    public PrefetchedSearchResult prefetch(Map<String, Object> effectiveContext, String userMessage) {
        Map<String, Object> resolved = resolvedConstraints(effectiveContext);
        Map<String, Object> searchParams = buildSearchParams(resolved, userMessage);
        if (!hasCategorySignal(searchParams)) {
            return PrefetchedSearchResult.unavailable("categoryId and categoryRaw are both empty");
        }
        return catalogSearchClientService.search(searchParams);
    }

    private Map<String, Object> buildSearchParams(Map<String, Object> resolved, String userMessage) {
        Map<String, Object> params = new LinkedHashMap<>();
        copyIfPresent(params, resolved, "categoryId");
        copyIfPresent(params, resolved, "categoryRaw");

        Map<String, Object> searchHints = searchHints(resolved);
        Object brandKeyword = searchHints.get("brandKeyword");
        if (hasValue(brandKeyword)) {
            params.put("keyword", brandKeyword.toString().trim());
        }

        Map<String, Object> budget = budgetMap(resolved, searchHints);
        if (!budget.isEmpty()) {
            copyIfPresent(params, budget, "min");
            copyIfPresent(params, budget, "max");
            if (params.get("min") instanceof Number min) {
                params.put("minPrice", min.doubleValue());
            }
            if (params.get("max") instanceof Number max) {
                params.put("maxPrice", max.doubleValue());
            }
            params.remove("min");
            params.remove("max");
        }

        params.put("limit", searchLimit);
        String semanticQuery = buildSemanticQuery(resolved, budget, brandKeyword, userMessage);
        if (!semanticQuery.isBlank()) {
            params.put("semanticQuery", semanticQuery);
        }
        return params;
    }

    private String buildSemanticQuery(
            Map<String, Object> resolved,
            Map<String, Object> budget,
            Object brandKeyword,
            String userMessage
    ) {
        StringJoiner joiner = new StringJoiner("，");
        appendFirstPresent(joiner, resolved, "categoryName", "categoryRaw");
        if (!appendList(joiner, resolved.get("brandPreferences"), "品牌偏好")) {
            appendValue(joiner, brandKeyword, "品牌偏好");
        }
        appendValue(joiner, resolved.get("scene"), "使用场景");
        appendList(joiner, resolved.get("mustHave"), "必须满足");
        appendList(joiner, resolved.get("dislikes"), "不喜欢");
        appendList(joiner, resolved.get("notes"), "补充偏好");
        appendBudget(joiner, budget);
        if (userMessage != null && !userMessage.isBlank()) {
            appendPlain(joiner, userMessage.trim());
        }
        return joiner.toString();
    }

    private void appendFirstPresent(StringJoiner joiner, Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (hasValue(value)) {
                appendPlain(joiner, value.toString().trim());
                return;
            }
        }
    }

    private void appendValue(StringJoiner joiner, Object value, String label) {
        if (hasValue(value)) {
            appendPlain(joiner, label + "：" + value.toString().trim());
        }
    }

    private boolean appendList(StringJoiner joiner, Object value, String label) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        List<String> items = list.stream()
                .filter(this::hasValue)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        if (!items.isEmpty()) {
            appendPlain(joiner, label + "：" + String.join("、", items));
            return true;
        }
        return false;
    }

    private void appendBudget(StringJoiner joiner, Map<String, Object> budget) {
        if (budget == null || budget.isEmpty()) {
            return;
        }
        Object min = budget.get("min");
        Object max = budget.get("max");
        if (hasValue(min) && hasValue(max)) {
            appendPlain(joiner, "预算：" + min + "-" + max + "元");
        } else if (hasValue(min)) {
            appendPlain(joiner, "预算：" + min + "元以上");
        } else if (hasValue(max)) {
            appendPlain(joiner, "预算：" + max + "元以内");
        }
    }

    private void appendPlain(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value.trim());
        }
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> searchHints(Map<String, Object> resolved) {
        Object hints = resolved.get("searchHints");
        if (hints instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> budgetMap(Map<String, Object> resolved, Map<String, Object> searchHints) {
        Object budget = searchHints.get("budget");
        if (budget instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        budget = resolved.get("budget");
        if (budget instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean hasCategorySignal(Map<String, Object> searchParams) {
        return hasValue(searchParams.get("categoryId")) || hasValue(searchParams.get("categoryRaw"));
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (hasValue(value)) {
            target.put(key, value);
        }
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank() && !"null".equalsIgnoreCase(text);
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (hasValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
