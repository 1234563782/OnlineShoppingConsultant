package com.onlineshopping.orchestrator.dto;

import java.util.Map;

public record SessionProcessResult(
        Map<String, Object> extractedPatch,
        Map<String, Object> sessionContext,
        Map<String, Object> effectiveContext,
        String intentType,
        CategoryResolutionResult categoryResolution,
        boolean categoryReplaced,
        String categoryReplaceReason,
        Map<String, Object> stateDebug
) {
}
