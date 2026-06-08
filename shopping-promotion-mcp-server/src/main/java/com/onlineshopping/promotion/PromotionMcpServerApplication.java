package com.onlineshopping.promotion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.onlineshopping.promotion.mapper")
public class PromotionMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionMcpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider promotionTools(PromotionMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
