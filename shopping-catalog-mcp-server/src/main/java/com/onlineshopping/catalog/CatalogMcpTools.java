package com.onlineshopping.catalog;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogMcpTools {

    private static final List<Map<String, Object>> PRODUCTS = List.of(
            product("SKU1001", "手机", "小米 14", "Xiaomi", 3999, "旗舰直屏，徕卡影像"),
            product("SKU1002", "手机", "iPhone 15", "Apple", 5999, "A16 芯片，生态完善"),
            product("SKU1003", "手机", "荣耀 200", "Honor", 2699, "轻薄长续航"),
            product("SKU2001", "耳机", "索尼 WH-1000XM5", "Sony", 2299, "头戴降噪旗舰"),
            product("SKU2002", "耳机", "AirPods Pro 2", "Apple", 1899, "主动降噪，通透模式"),
            product("SKU2003", "耳机", "漫步者 NeoBuds Pro", "Edifier", 799, "入门降噪"),
            product("SKU3001", "电脑", "联想小新 Pro 14", "Lenovo", 5699, "轻薄高性能"),
            product("SKU3002", "电脑", "MacBook Air M3", "Apple", 8999, "续航优秀"),
            product("SKU3003", "电脑", "机械革命 无界 14X", "MECHREVO", 4299, "高性价比"),
            product("SKU4001", "平板", "iPad Air", "Apple", 4799, "学习办公通用"),
            product("SKU4002", "平板", "小米平板 6S Pro", "Xiaomi", 3299, "大屏娱乐"),
            product("SKU5001", "手表", "华为 WATCH GT 4", "Huawei", 1488, "健康监测"),
            product("SKU5002", "手表", "Apple Watch S9", "Apple", 2999, "iOS 生态搭配")
    );

    @Tool(name = "searchProduct", description = "根据关键词、价格区间搜索商品候选")
    public String searchProduct(
            @ToolParam(description = "关键词，如手机/耳机/品牌名") String keyword,
            @ToolParam(description = "最低价，允许为空") Double minPrice,
            @ToolParam(description = "最高价，允许为空") Double maxPrice,
            @ToolParam(description = "返回条数上限，默认5") Integer limit
    ) {
        String kw = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        int max = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        List<Map<String, Object>> filtered = PRODUCTS.stream()
                .filter(p -> kw.isBlank() || stringify(p).contains(kw))
                .filter(p -> minPrice == null || ((Number) p.get("price")).doubleValue() >= minPrice)
                .filter(p -> maxPrice == null || ((Number) p.get("price")).doubleValue() <= maxPrice)
                .limit(max)
                .collect(Collectors.toCollection(ArrayList::new));
        return toJsonLike(filtered);
    }

    @Tool(name = "getProductDetail", description = "根据 skuId 获取商品详情")
    public String getProductDetail(@ToolParam(description = "商品skuId") String skuId) {
        return PRODUCTS.stream()
                .filter(p -> p.get("skuId").equals(skuId))
                .findFirst()
                .map(this::toJsonLikeSingle)
                .orElse("{\"message\":\"商品不存在\"}");
    }

    private static Map<String, Object> product(
            String skuId, String category, String name, String brand, int price, String description
    ) {
        return Map.of(
                "skuId", skuId,
                "category", category,
                "name", name,
                "brand", brand,
                "price", price,
                "description", description
        );
    }

    private String stringify(Map<String, Object> p) {
        return (p.get("category") + " " + p.get("name") + " " + p.get("brand")).toLowerCase(Locale.ROOT);
    }

    private String toJsonLike(List<Map<String, Object>> list) {
        return list.stream().map(this::toJsonLikeSingle).collect(Collectors.joining(",", "[", "]"));
    }

    private String toJsonLikeSingle(Map<String, Object> p) {
        return String.format(Locale.ROOT,
                "{\"skuId\":\"%s\",\"category\":\"%s\",\"name\":\"%s\",\"brand\":\"%s\",\"price\":%s,\"description\":\"%s\"}",
                p.get("skuId"), p.get("category"), p.get("name"), p.get("brand"), p.get("price"), p.get("description"));
    }
}
