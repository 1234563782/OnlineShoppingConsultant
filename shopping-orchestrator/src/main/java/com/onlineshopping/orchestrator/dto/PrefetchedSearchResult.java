package com.onlineshopping.orchestrator.dto;

import com.onlineshopping.prompt.PromptTemplateService;

import java.util.List;
import java.util.Map;

/**
 * Product search outcome prefetched by orchestrator before consult-agent runs.
 * This is the authorized product set for the current turn.
 */
public record PrefetchedSearchResult(
        String status,
        String matchType,
        String message,
        List<Map<String, Object>> products,
        Map<String, Object> searchParams,
        String error
) implements PromptTemplateService.PrefetchedSearchView {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_UNAVAILABLE = "unavailable";

    public static PrefetchedSearchResult skipped() {
        return new PrefetchedSearchResult(STATUS_SKIPPED, null, null, List.of(), Map.of(), null);
    }

    public static PrefetchedSearchResult unavailable(String error) {
        return new PrefetchedSearchResult(
                STATUS_UNAVAILABLE,
                null,
                null,
                List.of(),
                Map.of(),
                error == null ? "catalog unavailable" : error
        );
    }

    public static PrefetchedSearchResult ok(
            String matchType,
            String message,
            List<Map<String, Object>> products,
            Map<String, Object> searchParams
    ) {
        List<Map<String, Object>> safeProducts = products == null ? List.of() : List.copyOf(products);
        Map<String, Object> safeParams = searchParams == null ? Map.of() : Map.copyOf(searchParams);
        return new PrefetchedSearchResult(
                STATUS_OK,
                matchType,
                message,
                safeProducts,
                safeParams,
                null
        );
    }

    public boolean isUsable() {
        return STATUS_OK.equals(status);
    }

    public Map<String, Object> toDebugMap() {
        Map<String, Object> debug = new java.util.LinkedHashMap<>();
        debug.put("status", status);
        debug.put("matchType", matchType);
        debug.put("message", message);
        debug.put("productCount", products == null ? 0 : products.size());
        debug.put("searchParams", searchParams);
        if (error != null) {
            debug.put("error", error);
        }
        if (products != null && !products.isEmpty()) {
            debug.put(
                    "skuIds",
                    products.stream()
                            .map(product -> product.get("skuId"))
                            .filter(java.util.Objects::nonNull)
                            .map(Object::toString)
                            .toList()
            );
        }
        return debug;
    }
}
