package com.onlineshopping.promotion;

import com.onlineshopping.promotion.model.PromotionEntity;
import com.onlineshopping.promotion.service.PromotionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromotionMcpTools {

    private final PromotionService promotionService;

    public PromotionMcpTools(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Tool(name = "getPromotions", description = "按 skuId 查询可用优惠")
    public String getPromotions(
            @ToolParam(description = "商品 skuId") String skuId
    ) {
        List<PromotionEntity> list = promotionService.findBySkuId(skuId);
        if (list.isEmpty()) {
            return "{\"skuId\":\"" + skuId + "\",\"promotions\":[]}";
        }
        String promoJson = list.stream()
                .map(this::asJson)
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"skuId\":\"" + skuId + "\",\"promotions\":" + promoJson + "}";
    }

    private String asJson(PromotionEntity row) {
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"type\":\"").append(row.getType()).append("\"");
        builder.append(",\"label\":\"").append(row.getLabel()).append("\"");
        if (row.getDiscount() != null) {
            builder.append(",\"discount\":").append(row.getDiscount());
        }
        if (row.getDiscountRate() != null) {
            builder.append(",\"discountRate\":").append(row.getDiscountRate());
        }
        builder.append("}");
        return builder.toString();
    }
}
