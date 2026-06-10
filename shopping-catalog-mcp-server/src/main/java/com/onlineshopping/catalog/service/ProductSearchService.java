package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.dto.ProductSearchRequest;
import com.onlineshopping.catalog.dto.ProductSearchResponse;
import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.search.ProductSearchFallback;
import com.onlineshopping.catalog.search.ProductSearchFallback.SearchOutcome;
import com.onlineshopping.catalog.vector.ProductEmbeddingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private final CatalogService catalogService;
    private final CategoryService categoryService;
    private final ObjectProvider<ProductEmbeddingService> embeddingServiceProvider;

    public ProductSearchService(
            CatalogService catalogService,
            CategoryService categoryService,
            ObjectProvider<ProductEmbeddingService> embeddingServiceProvider
    ) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
        this.embeddingServiceProvider = embeddingServiceProvider;
    }

    public ProductSearchResponse search(ProductSearchRequest request) {
        int max = resolveLimit(request.limit());
        CategoryService.CategoryMatch category = resolveCategory(request.categoryId(), request.categoryRaw());
        String normalizedCategoryId = category == null
                ? normalizeCategoryId(request.categoryId())
                : category.categoryId();
        String searchKeyword = request.keyword() == null || request.keyword().isBlank()
                ? null
                : request.keyword().trim();

        if (searchKeyword != null) {
            SearchOutcome outcome = ProductSearchFallback.search(
                    catalogService,
                    normalizedCategoryId,
                    searchKeyword,
                    request.minPrice(),
                    request.maxPrice(),
                    max
            );
            return toResponse(outcome.matchType(), outcome.message(), category, outcome.products(), searchKeyword);
        }

        List<ProductEntity> exact = tryVectorFirst(
                normalizedCategoryId,
                request.minPrice(),
                request.maxPrice(),
                request.semanticQuery(),
                max
        );
        if (exact != null && !exact.isEmpty()) {
            exact = mergeVectorWithMysql(
                    exact,
                    normalizedCategoryId,
                    null,
                    request.minPrice(),
                    request.maxPrice(),
                    max
            );
            return toResponse("exact", "命中用户指定品类和预算范围。", category, exact, null);
        }

        SearchOutcome outcome = ProductSearchFallback.search(
                catalogService,
                normalizedCategoryId,
                null,
                request.minPrice(),
                request.maxPrice(),
                max
        );
        return toResponse(outcome.matchType(), outcome.message(), category, outcome.products(), null);
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 5;
        }
        return Math.min(limit, 10);
    }

    private List<ProductEntity> tryVectorFirst(
            String normalizedCategoryId,
            Double minPrice,
            Double maxPrice,
            String semanticQuery,
            int max
    ) {
        ProductEmbeddingService svc = embeddingServiceProvider.getIfAvailable();
        if (svc == null || semanticQuery == null || semanticQuery.isBlank() || normalizedCategoryId == null) {
            return null;
        }
        List<String> skus = svc.searchNearestSkuIds(normalizedCategoryId, minPrice, maxPrice, semanticQuery.trim(), max);
        if (skus.isEmpty()) {
            return null;
        }
        List<ProductEntity> list = catalogService.findBySkuIdsPreserveOrder(skus);
        return list.isEmpty() ? null : list;
    }

    private List<ProductEntity> mergeVectorWithMysql(
            List<ProductEntity> vectorFirst,
            String normalizedCategoryId,
            String searchKeyword,
            Double minPrice,
            Double maxPrice,
            int max
    ) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ProductEntity> out = new ArrayList<>();
        for (ProductEntity product : vectorFirst) {
            if (product != null && product.getSkuId() != null && seen.add(product.getSkuId())) {
                out.add(product);
            }
            if (out.size() >= max) {
                return out;
            }
        }
        List<ProductEntity> more = catalogService.search(
                normalizedCategoryId,
                searchKeyword,
                minPrice,
                maxPrice,
                max
        );
        for (ProductEntity product : more) {
            if (out.size() >= max) {
                break;
            }
            if (product != null && product.getSkuId() != null && seen.add(product.getSkuId())) {
                out.add(product);
            }
        }
        return out;
    }

    private CategoryService.CategoryMatch resolveCategory(String categoryId, String categoryRaw) {
        String normalizedCategoryId = normalizeCategoryId(categoryId);
        if (normalizedCategoryId != null) {
            return new CategoryService.CategoryMatch(normalizedCategoryId, null, categoryRaw, 1.0, "category_id");
        }
        return categoryService.normalize(categoryRaw).orElse(null);
    }

    private String normalizeCategoryId(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? null : categoryId.trim();
    }

    private ProductSearchResponse toResponse(
            String matchType,
            String message,
            CategoryService.CategoryMatch category,
            List<ProductEntity> products,
            String brandKeyword
    ) {
        Map<String, Object> categoryNormalization = category == null
                ? Map.of()
                : categoryMap(category);
        List<Map<String, Object>> productMaps = products.stream().map(this::toProductMap).toList();
        return new ProductSearchResponse(
                matchType,
                message,
                categoryNormalization,
                brandKeyword == null || brandKeyword.isBlank() ? null : brandKeyword,
                productMaps
        );
    }

    private Map<String, Object> toProductMap(ProductEntity product) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("skuId", product.getSkuId());
        item.put("categoryId", product.getCategoryId());
        item.put("categoryName", product.getCategoryName() == null ? product.getCategory() : product.getCategoryName());
        item.put("name", product.getName());
        item.put("brand", product.getBrand());
        item.put("price", product.getPrice());
        item.put("description", product.getDescription());
        return item;
    }

    private Map<String, Object> categoryMap(CategoryService.CategoryMatch category) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("categoryId", category.categoryId());
        item.put("categoryName", category.categoryName());
        item.put("categoryRaw", category.categoryRaw());
        item.put("confidence", category.confidence());
        return item;
    }
}
