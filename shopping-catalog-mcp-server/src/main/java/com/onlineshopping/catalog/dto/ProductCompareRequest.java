package com.onlineshopping.catalog.dto;

import java.util.List;

public record ProductCompareRequest(
        List<String> skuIds,
        List<String> focusDimensions
) {
}
