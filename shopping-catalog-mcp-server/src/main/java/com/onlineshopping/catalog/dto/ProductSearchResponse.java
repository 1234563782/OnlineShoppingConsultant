package com.onlineshopping.catalog.dto;

import java.util.List;
import java.util.Map;

public record ProductSearchResponse(
        String matchType,
        String message,
        Map<String, Object> categoryNormalization,
        String brandKeyword,
        List<Map<String, Object>> products
) {
}
