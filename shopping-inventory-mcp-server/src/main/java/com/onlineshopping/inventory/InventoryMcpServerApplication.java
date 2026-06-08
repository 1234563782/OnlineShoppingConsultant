package com.onlineshopping.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.onlineshopping.inventory.mapper")
public class InventoryMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryMcpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider inventoryTools(InventoryMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
