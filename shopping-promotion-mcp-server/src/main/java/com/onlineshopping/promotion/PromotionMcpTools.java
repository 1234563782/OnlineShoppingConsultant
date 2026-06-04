package com.onlineshopping.promotion;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PromotionMcpTools {

    private static final Map<String, List<Map<String, Object>>> PROMOTIONS = Map.of(
            "SKU1001", List.of(Map.of("type", "coupon", "label", "满3000减200", "discount", 200)),
            "SKU1002", List.of(Map.of("type", "installment", "label", "12期免息", "discount", 0)),
            "SKU2001", List.of(Map.of("type", "coupon", "label", "满2000减150", "discount", 150)),
            "SKU2002", List.of(Map.of("type", "member", "label", "会员95折", "discountRate", 0.95)),
            "SKU3001", List.of(Map.of("type", "coupon", "label", "满5000减300", "discount", 300)),
            "SKU4002", List.of(Map.of("type", "coupon", "label", "满3000减100", "discount", 100))
    );

    @Tool(name = "getPromotions", description = "按 skuId 查询可用优惠")
    public String getPromotions(
            @ToolParam(description = "商品 skuId") String skuId
    ) {
        List<Map<String, Object>> list = PROMOTIONS.getOrDefault(skuId, List.of());
        if (list.isEmpty()) {
            return "{\"skuId\":\"" + skuId + "\",\"promotions\":[]}";
        }
        String promoJson = list.stream()
                .map(this::asJson)
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"skuId\":\"" + skuId + "\",\"promotions\":" + promoJson + "}";
    }

    private String asJson(Map<String, Object> row) {
        return row.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + jsonValue(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String jsonValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return "\"" + value + "\"";
    }
}
