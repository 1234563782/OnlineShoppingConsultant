package com.onlineshopping.inventory;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InventoryMcpTools {

    private static final Map<String, Integer> STOCK = Map.ofEntries(
            Map.entry("SKU1001", 32),
            Map.entry("SKU1002", 8),
            Map.entry("SKU1003", 0),
            Map.entry("SKU2001", 15),
            Map.entry("SKU2002", 26),
            Map.entry("SKU2003", 57),
            Map.entry("SKU3001", 5),
            Map.entry("SKU3002", 2),
            Map.entry("SKU3003", 0),
            Map.entry("SKU4001", 9),
            Map.entry("SKU4002", 23),
            Map.entry("SKU5001", 11),
            Map.entry("SKU5002", 3)
    );

    @Tool(name = "checkInventory", description = "按 skuId 查询库存状态")
    public String checkInventory(
            @ToolParam(description = "商品 skuId") String skuId
    ) {
        Integer quantity = STOCK.get(skuId);
        if (quantity == null) {
            return "{\"skuId\":\"" + skuId + "\",\"available\":false,\"quantity\":0,\"message\":\"sku不存在\"}";
        }
        boolean available = quantity > 0;
        return "{\"skuId\":\"" + skuId + "\",\"available\":" + available + ",\"quantity\":" + quantity + "}";
    }
}
