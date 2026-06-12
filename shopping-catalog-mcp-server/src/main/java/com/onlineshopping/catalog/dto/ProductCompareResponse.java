package com.onlineshopping.catalog.dto;

import java.util.List;
import java.util.Map;

public record ProductCompareResponse(
        String status,
        List<String> skuIds,
        List<Map<String, Object>> products,
        List<String> compareDimensions,
        boolean crossCategory,
        String message
) {
    public static final String STATUS_OK = "ok";
    public static final String STATUS_INSUFFICIENT_TARGETS = "insufficient_targets";
    public static final String STATUS_SKU_NOT_FOUND = "sku_not_found";
    public static final String STATUS_UNAVAILABLE = "unavailable";
}
