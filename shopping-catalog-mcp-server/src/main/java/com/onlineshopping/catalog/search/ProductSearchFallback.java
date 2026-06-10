package com.onlineshopping.catalog.search;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.service.CatalogService;

import java.util.List;

/**
 * Tiered product search fallback.
 * When a brand keyword is present:
 * 1) category + brand + budget
 * 2) category + brand (other prices)
 * 3) category + budget (other brands)
 * 4) category only (other brands, any price)
 */
public final class ProductSearchFallback {

    private ProductSearchFallback() {
    }

    public static SearchOutcome search(
            CatalogService catalogService,
            String categoryId,
            String brandKeyword,
            Double minPrice,
            Double maxPrice,
            int limit
    ) {
        boolean hasBrand = brandKeyword != null && !brandKeyword.isBlank();
        if (hasBrand) {
            return searchWithBrand(catalogService, categoryId, brandKeyword, minPrice, maxPrice, limit);
        }
        return searchWithoutBrand(catalogService, categoryId, minPrice, maxPrice, limit);
    }

    private static SearchOutcome searchWithBrand(
            CatalogService catalogService,
            String categoryId,
            String brandKeyword,
            Double minPrice,
            Double maxPrice,
            int limit
    ) {
        List<ProductEntity> exact = catalogService.search(categoryId, brandKeyword, minPrice, maxPrice, limit);
        if (!exact.isEmpty()) {
            return new SearchOutcome(
                    "exact",
                    "命中指定品类、品牌与预算范围。",
                    exact
            );
        }

        List<ProductEntity> sameBrandOtherPrice = catalogService.search(categoryId, brandKeyword, null, null, limit);
        if (!sameBrandOtherPrice.isEmpty()) {
            return new SearchOutcome(
                    "same_brand_other_price",
                    "指定预算内没有该品牌商品，但找到了同品牌其他价格段商品。",
                    sameBrandOtherPrice
            );
        }

        List<ProductEntity> otherBrandSameBudget = catalogService.search(categoryId, null, minPrice, maxPrice, limit);
        if (!otherBrandSameBudget.isEmpty()) {
            return new SearchOutcome(
                    "same_category_other_brand_same_budget",
                    "目录中没有该品牌商品，返回同品类同预算范围内的其他品牌候选。",
                    otherBrandSameBudget
            );
        }

        List<ProductEntity> otherBrandAnyPrice = catalogService.search(categoryId, null, null, null, limit);
        if (!otherBrandAnyPrice.isEmpty()) {
            return new SearchOutcome(
                    "same_category_other_brand_any_price",
                    "目录中没有该品牌商品，返回同品类其他品牌商品供参考。",
                    otherBrandAnyPrice
            );
        }

        return emptyOutcome();
    }

    private static SearchOutcome searchWithoutBrand(
            CatalogService catalogService,
            String categoryId,
            Double minPrice,
            Double maxPrice,
            int limit
    ) {
        List<ProductEntity> exact = catalogService.search(categoryId, null, minPrice, maxPrice, limit);
        if (!exact.isEmpty()) {
            return new SearchOutcome(
                    "exact",
                    "命中用户指定品类和预算范围。",
                    exact
            );
        }

        List<ProductEntity> sameCategoryOtherPrice = catalogService.search(categoryId, null, null, null, limit);
        if (!sameCategoryOtherPrice.isEmpty()) {
            return new SearchOutcome(
                    "same_category_other_price",
                    "指定预算范围内没有命中，但找到了同品类的其他价格段商品。",
                    sameCategoryOtherPrice
            );
        }

        List<ProductEntity> otherCategorySameBudget = catalogService.search(null, null, minPrice, maxPrice, limit);
        if (!otherCategorySameBudget.isEmpty()) {
            return new SearchOutcome(
                    "alternative_category_same_budget",
                    "当前目录没有该品类商品，返回预算范围内的其他品类替代候选。",
                    otherCategorySameBudget
            );
        }

        List<ProductEntity> alternatives = catalogService.search(null, null, null, null, limit);
        if (!alternatives.isEmpty()) {
            return new SearchOutcome(
                    "alternative_category_any_price",
                    "当前目录没有该品类商品，且预算范围内也没有替代候选，返回其他品类商品供参考。",
                    alternatives
            );
        }

        return emptyOutcome();
    }

    private static SearchOutcome emptyOutcome() {
        return new SearchOutcome("no_match", "目录中没有符合条件的商品。", List.of());
    }

    public record SearchOutcome(String matchType, String message, List<ProductEntity> products) {
    }
}
