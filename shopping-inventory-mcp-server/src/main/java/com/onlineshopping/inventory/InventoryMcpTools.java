package com.onlineshopping.inventory;

import com.onlineshopping.inventory.service.InventoryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class InventoryMcpTools {

    private final InventoryService inventoryService;

    public InventoryMcpTools(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Tool(name = "checkInventory", description = "按 skuId 查询库存状态")
    public String checkInventory(
            @ToolParam(description = "商品 skuId") String skuId
    ) {
        return inventoryService.findBySkuId(skuId)
                .map(row -> {
                    boolean available = row.getQuantity() > 0;
                    return "{\"skuId\":\"" + skuId + "\",\"available\":" + available
                            + ",\"quantity\":" + row.getQuantity() + "}";
                })
                .orElse("{\"skuId\":\"" + skuId + "\",\"available\":false,\"quantity\":0,\"message\":\"sku不存在\"}");
    }
}
