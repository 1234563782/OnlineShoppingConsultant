package com.onlineshopping.orchestrator.dto;

import java.util.List;
import java.util.Map;

/**
 * Product comparison outcome prefetched by orchestrator before compare_agent runs.
 */
public record PrefetchedCompareResult(
        String status,
        List<String> skuIds,
        List<Map<String, Object>> products,
        List<String> compareDimensions,
        boolean crossCategory,
        String message,
        String error
) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_UNAVAILABLE = "unavailable";
    public static final String STATUS_INSUFFICIENT_TARGETS = "insufficient_targets";

    public static PrefetchedCompareResult skipped() {
        return new PrefetchedCompareResult(STATUS_SKIPPED, List.of(), List.of(), List.of(), false, null, null);
    }

    public static PrefetchedCompareResult unavailable(String error) {
        return new PrefetchedCompareResult(
                STATUS_UNAVAILABLE,
                List.of(),
                List.of(),
                List.of(),
                false,
                null,
                error == null ? "compare unavailable" : error
        );
    }

    public static PrefetchedCompareResult insufficient(String message) {
        return new PrefetchedCompareResult(
                STATUS_INSUFFICIENT_TARGETS,
                List.of(),
                List.of(),
                List.of(),
                false,
                message,
                null
        );
    }

    public static PrefetchedCompareResult fromResponse(
            String status,
            List<String> skuIds,
            List<Map<String, Object>> products,
            List<String> compareDimensions,
            boolean crossCategory,
            String message
    ) {
        return new PrefetchedCompareResult(
                status,
                skuIds == null ? List.of() : List.copyOf(skuIds),
                products == null ? List.of() : List.copyOf(products),
                compareDimensions == null ? List.of() : List.copyOf(compareDimensions),
                crossCategory,
                message,
                null
        );
    }

    public boolean isUsable() {
        return STATUS_OK.equals(status);
    }

    public Map<String, Object> toDebugMap() {
        Map<String, Object> debug = new java.util.LinkedHashMap<>();
        debug.put("status", status);
        debug.put("skuIds", skuIds);
        debug.put("productCount", products == null ? 0 : products.size());
        debug.put("compareDimensions", compareDimensions);
        debug.put("crossCategory", crossCategory);
        if (message != null) {
            debug.put("message", message);
        }
        if (error != null) {
            debug.put("error", error);
        }
        return debug;
    }
}
