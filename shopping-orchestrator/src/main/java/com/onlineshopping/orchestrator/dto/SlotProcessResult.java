package com.onlineshopping.orchestrator.dto;

import java.util.Map;

/**
 * Session slot processing result before long-term profile is merged into effectiveContext.
 */
public record SlotProcessResult(
        Map<String, Object> sessionBefore,
        Map<String, Object> extractedPatch,
        Map<String, Object> sessionContext,
        CategoryResolutionResult categoryResolution,
        boolean categoryReplaced,
        String categoryReplaceReason
) {
}
