package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.mapper.ProductMapper;
import com.onlineshopping.catalog.model.ProductEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CatalogService {

    private final ProductMapper productMapper;

    public CatalogService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<ProductEntity> search(String categoryId, String keyword, Double minPrice, Double maxPrice, int limit) {
        String raw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String kw = raw.isBlank() ? "" : raw.replace("%", "").replace("_", "");
        return productMapper.searchByFilters(
                normalizeCategoryId(categoryId),
                kw,
                minPrice,
                maxPrice,
                limit
        );
    }

    public Optional<ProductEntity> findBySkuId(String skuId) {
        return Optional.ofNullable(productMapper.selectById(skuId));
    }

    /**
     * Load products by sku id list preserving the given order (for vector search results).
     */
    public List<ProductEntity> findBySkuIdsPreserveOrder(List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        Map<String, ProductEntity> byId = productMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(ProductEntity::getSkuId, Function.identity(), (a, b) -> a));
        List<ProductEntity> ordered = new ArrayList<>();
        for (String id : skuIds) {
            ProductEntity p = byId.get(id);
            if (p != null) {
                ordered.add(p);
            }
        }
        return ordered;
    }

    private String normalizeCategoryId(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? null : categoryId.trim();
    }
}
