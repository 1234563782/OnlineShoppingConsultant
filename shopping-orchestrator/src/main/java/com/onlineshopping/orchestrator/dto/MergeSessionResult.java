package com.onlineshopping.orchestrator.dto;

import java.util.Map;

public record MergeSessionResult(
        Map<String, Object> sessionContext,
        boolean categoryReplaced,
        String categoryReplaceReason
) {
}
