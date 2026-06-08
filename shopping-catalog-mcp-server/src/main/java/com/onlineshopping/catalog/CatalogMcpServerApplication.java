package com.onlineshopping.catalog;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.onlineshopping.catalog.vector.VectorStoreProperties;

@SpringBootApplication
@EnableConfigurationProperties(VectorStoreProperties.class)
public class CatalogMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogMcpServerApplication.class, args);
    }

    @Bean
    ToolCallbackProvider catalogTools(CatalogMcpTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }
}
