package com.onlineshopping.orchestrator.dto;

import java.util.List;
import java.util.Map;

public record MemoryRecallResult(
        Map<String, Object> profileSegments,
        List<String> recalledKeys,
        List<String> excludeKeys
) {
}
