package com.onlineshopping.orchestrator.dto;

import java.util.Map;

public record ChatPreparedContext(
        String userId,
        String sessionId,
        SessionState sessionState,
        Map<String, Object> profile,
        Map<String, Object> extractedPatch,
        Map<String, Object> sessionContext,
        Map<String, Object> effectiveContext,
        String intentType,
        CategoryResolutionResult categoryResolution,
        Map<String, Object> stateDebug,
        TurnDecision turnDecision,
        PrefetchedSearchResult prefetchedSearch
) {
}
