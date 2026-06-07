package com.onlineshopping.orchestrator.dto;

import java.util.Map;

public record CategoryResolutionResult(
        String status,
        String categoryId,
        String categoryName,
        String categoryRaw,
        double confidence,
        String matchedBy
) {
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_LOW_CONFIDENCE = "LOW_CONFIDENCE";
    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    public static CategoryResolutionResult skipped() {
        return new CategoryResolutionResult(STATUS_SKIPPED, null, null, null, 0.0, null);
    }

    public Map<String, Object> toDebugMap() {
        return Map.of(
                "status", status,
                "categoryId", categoryId == null ? "" : categoryId,
                "categoryName", categoryName == null ? "" : categoryName,
                "categoryRaw", categoryRaw == null ? "" : categoryRaw,
                "confidence", confidence,
                "matchedBy", matchedBy == null ? "" : matchedBy
        );
    }
}
