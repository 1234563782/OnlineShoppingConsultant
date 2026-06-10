package com.onlineshopping.catalog.dto;

public record ProductSearchRequest(
        String categoryId,
        String categoryRaw,
        String keyword,
        Double minPrice,
        Double maxPrice,
        Integer limit,
        String semanticQuery
) {
}
