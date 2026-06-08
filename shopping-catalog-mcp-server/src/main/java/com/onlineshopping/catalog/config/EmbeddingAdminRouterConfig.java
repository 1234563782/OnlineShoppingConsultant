package com.onlineshopping.catalog.config;

import com.onlineshopping.catalog.vector.ProductEmbeddingService;
import com.onlineshopping.catalog.vector.VectorSearchEnabledCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

@Configuration
@Conditional(VectorSearchEnabledCondition.class)
public class EmbeddingAdminRouterConfig {

    @Bean
    @ConditionalOnProperty(prefix = "shopping.vector", name = "admin-rebuild-enabled", havingValue = "true")
    public RouterFunction<ServerResponse> productEmbeddingRebuildRoute(ProductEmbeddingService embeddingService) {
        return RouterFunctions.route()
                .POST("/api/v1/catalog/product-embeddings/rebuild", request ->
                        ServerResponse.ok().bodyValue(Map.of("indexed", embeddingService.rebuildAll())))
                .build();
    }
}
