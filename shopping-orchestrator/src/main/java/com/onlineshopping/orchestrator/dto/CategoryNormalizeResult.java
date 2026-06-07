package com.onlineshopping.orchestrator.dto;

public record CategoryNormalizeResult(
        String categoryId,
        String categoryName,
        String categoryRaw,
        double confidence,
        String resolution,
        String matchedBy
) {
    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(resolution);
    }

    public boolean isLowConfidence() {
        return "LOW_CONFIDENCE".equalsIgnoreCase(resolution);
    }

    public boolean isUnresolved() {
        return resolution == null
                || resolution.isBlank()
                || "UNRESOLVED".equalsIgnoreCase(resolution);
    }
}
