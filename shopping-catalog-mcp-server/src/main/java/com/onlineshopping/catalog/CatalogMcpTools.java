package com.onlineshopping.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.catalog.dto.ProductSearchRequest;
import com.onlineshopping.catalog.dto.ProductSearchResponse;
import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.service.CatalogService;
import com.onlineshopping.catalog.service.ProductSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class CatalogMcpTools {

    private final CatalogService catalogService;
    private final ProductSearchService productSearchService;
    private final ObjectMapper objectMapper;

    public CatalogMcpTools(
            CatalogService catalogService,
            ProductSearchService productSearchService,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.productSearchService = productSearchService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "searchProduct", description = "根据标准类目、品牌关键词、价格区间搜索商品；有品牌时先同品牌其他价位，再同价位其他品牌")
    public String searchProduct(
            @ToolParam(description = "标准类目ID，如 cat_phone；没有则为空") String categoryId,
            @ToolParam(description = "用户原始品类词，如智能电视/运动手表；没有则为空") String categoryRaw,
            @ToolParam(description = "品牌或补充关键词，如小米/华为；没有则为空") String keyword,
            @ToolParam(description = "最低价，允许为空") Double minPrice,
            @ToolParam(description = "最高价，允许为空") Double maxPrice,
            @ToolParam(description = "返回条数上限，默认5") Integer limit,
            @ToolParam(description = "用户原话或检索语义句，用于向量排序；没有则为空") String semanticQuery
    ) {
        ProductSearchResponse response = productSearchService.search(new ProductSearchRequest(
                categoryId,
                categoryRaw,
                keyword,
                minPrice,
                maxPrice,
                limit,
                semanticQuery
        ));
        return toJson(response);
    }

    @Tool(name = "getProductDetail", description = "根据 skuId 获取商品详情")
    public String getProductDetail(@ToolParam(description = "商品skuId") String skuId) {
        return catalogService.findBySkuId(skuId)
                .map(this::toProductJson)
                .orElse("{\"message\":\"商品不存在\"}");
    }

    private String toJson(ProductSearchResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchType", response.matchType());
        result.put("message", response.message());
        result.put("categoryNormalization", response.categoryNormalization());
        if (response.brandKeyword() != null && !response.brandKeyword().isBlank()) {
            result.put("brandKeyword", response.brandKeyword());
        }
        result.put("products", response.products());
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

    private String toProductJson(ProductEntity product) {
        try {
            return objectMapper.writeValueAsString(toProductMap(product));
        } catch (JsonProcessingException e) {
            return "{\"message\":\"商品结果序列化失败\"}";
        }
    }
}
