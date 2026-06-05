package com.onlineshopping.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.service.CategoryService;
import com.onlineshopping.catalog.service.CatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogMcpTools {

    private final CatalogService catalogService;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    public CatalogMcpTools(CatalogService catalogService, CategoryService categoryService, ObjectMapper objectMapper) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "searchProduct", description = "根据标准类目或用户原始品类词、关键词、价格区间搜索商品候选；若无精确结果，会返回同品类其他价格段或其他品类替代候选")
    public String searchProduct(
            @ToolParam(description = "标准类目ID，如 cat_phone；没有则为空") String categoryId,
            @ToolParam(description = "用户原始品类词，如智能电视/运动手表；没有则为空") String categoryRaw,
            @ToolParam(description = "补充关键词，如品牌名/功能点；没有则为空") String keyword,
            @ToolParam(description = "最低价，允许为空") Double minPrice,
            @ToolParam(description = "最高价，允许为空") Double maxPrice,
            @ToolParam(description = "返回条数上限，默认5") Integer limit
    ) {
        int max = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        CategoryService.CategoryMatch category = resolveCategory(categoryId, categoryRaw);
        String normalizedCategoryId = category == null ? normalizeCategoryId(categoryId) : category.categoryId();
        String searchKeyword = keyword == null || keyword.isBlank() ? null : keyword;

        List<ProductEntity> exact = catalogService.search(normalizedCategoryId, searchKeyword, minPrice, maxPrice, max);
        if (!exact.isEmpty()) {
            return toJson("exact", "命中用户指定品类和预算范围。", category, exact);
        }

        List<ProductEntity> sameCategoryOtherPrice = catalogService.search(normalizedCategoryId, searchKeyword, null, null, max);
        if (!sameCategoryOtherPrice.isEmpty()) {
            return toJson("same_keyword_other_price", "指定预算范围内没有命中，但找到了同品类的其他价格段商品。", category, sameCategoryOtherPrice);
        }

        List<ProductEntity> alternativeSameBudget = catalogService.search(null, searchKeyword, minPrice, maxPrice, max);
        if (!alternativeSameBudget.isEmpty()) {
            return toJson("alternative_category_same_budget", "当前目录没有该品类商品，返回预算范围内的其他品类替代候选。", category, alternativeSameBudget);
        }

        List<ProductEntity> alternatives = catalogService.search(null, null, null, null, max);
        return toJson("alternative_category_any_price", "当前目录没有该品类商品，且预算范围内也没有替代候选，返回其他品类商品供参考。", category, alternatives);
    }

    @Tool(name = "getProductDetail", description = "根据 skuId 获取商品详情")
    public String getProductDetail(@ToolParam(description = "商品skuId") String skuId) {
        return catalogService.findBySkuId(skuId)
                .map(this::toProductJson)
                .orElse("{\"message\":\"商品不存在\"}");
    }

    private CategoryService.CategoryMatch resolveCategory(String categoryId, String categoryRaw) {
        String normalizedCategoryId = normalizeCategoryId(categoryId);
        if (normalizedCategoryId != null) {
            return new CategoryService.CategoryMatch(normalizedCategoryId, null, categoryRaw, 1.0);
        }
        return categoryService.normalize(categoryRaw).orElse(null);
    }

    private String normalizeCategoryId(String categoryId) {
        return categoryId == null || categoryId.isBlank() ? null : categoryId.trim();
    }

    private String toJson(String matchType, String message, CategoryService.CategoryMatch category, List<ProductEntity> products) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchType", matchType);
        result.put("message", message);
        result.put("categoryNormalization", category == null ? Map.of() : categoryMap(category));
        result.put("products", products.stream().map(this::toProductMap).toList());
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"matchType\":\"error\",\"message\":\"商品结果序列化失败\",\"products\":[]}";
        }
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

    private String toProductJson(ProductEntity product) {
        try {
            return objectMapper.writeValueAsString(toProductMap(product));
        } catch (JsonProcessingException e) {
            return "{\"message\":\"商品结果序列化失败\"}";
        }
    }
}
