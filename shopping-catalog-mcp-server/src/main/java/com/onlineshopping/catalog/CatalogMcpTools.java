package com.onlineshopping.catalog;

import com.onlineshopping.catalog.model.ProductEntity;
import com.onlineshopping.catalog.service.CatalogService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CatalogMcpTools {

    private final CatalogService catalogService;

    public CatalogMcpTools(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Tool(name = "searchProduct", description = "根据关键词、价格区间搜索商品候选")
    public String searchProduct(
            @ToolParam(description = "关键词，如手机/耳机/品牌名") String keyword,
            @ToolParam(description = "最低价，允许为空") Double minPrice,
            @ToolParam(description = "最高价，允许为空") Double maxPrice,
            @ToolParam(description = "返回条数上限，默认5") Integer limit
    ) {
        int max = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        List<ProductEntity> filtered = catalogService.search(keyword, minPrice, maxPrice, max);
        return toJsonLike(filtered);
    }

    @Tool(name = "getProductDetail", description = "根据 skuId 获取商品详情")
    public String getProductDetail(@ToolParam(description = "商品skuId") String skuId) {
        return catalogService.findBySkuId(skuId)
                .map(this::toJsonLikeSingle)
                .orElse("{\"message\":\"商品不存在\"}");
    }

    private String toJsonLike(List<ProductEntity> list) {
        return list.stream().map(this::toJsonLikeSingle).collect(Collectors.joining(",", "[", "]"));
    }

    private String toJsonLikeSingle(ProductEntity product) {
        return String.format(Locale.ROOT,
                "{\"skuId\":\"%s\",\"category\":\"%s\",\"name\":\"%s\",\"brand\":\"%s\",\"price\":%d,\"description\":\"%s\"}",
                product.getSkuId(),
                product.getCategory(),
                product.getName(),
                product.getBrand(),
                product.getPrice(),
                product.getDescription());
    }
}
