package com.onlineshopping.catalog.config;

import com.onlineshopping.catalog.dto.CategoryNormalizeResponse;
import com.onlineshopping.catalog.service.CategoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class CategoryApiRouterConfig {

    @Bean
    RouterFunction<ServerResponse> categoryNormalizeRoute(CategoryService categoryService) {
        return RouterFunctions.route()
                .GET("/api/v1/categories/normalize", request -> {
                    String raw = request.queryParam("raw").orElse("").trim();
                    if (raw.isBlank()) {
                        return ServerResponse.ok().bodyValue(CategoryNormalizeResponse.unresolved(""));
                    }
                    CategoryNormalizeResponse body = categoryService.normalize(raw)
                            .map(match -> CategoryNormalizeResponse.resolved(
                                    match.categoryId(),
                                    match.categoryName(),
                                    match.categoryRaw(),
                                    match.confidence(),
                                    match.confidence() >= 1.0 ? "exact_name" : "alias"
                            ))
                            .orElseGet(() -> CategoryNormalizeResponse.unresolved(raw));
                    return ServerResponse.ok().bodyValue(body);
                })
                .build();
    }
}
