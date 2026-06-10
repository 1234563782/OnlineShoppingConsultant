package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        if (userMessage != null && !userMessage.isBlank()) {
            params.put("semanticQuery", userMessage.trim());
        }
        return params;
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
