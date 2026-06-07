package com.onlineshopping.catalog.controller;

import com.onlineshopping.catalog.dto.CategoryNormalizeResponse;
import com.onlineshopping.catalog.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/normalize")
    public CategoryNormalizeResponse normalize(@RequestParam("raw") String raw) {
        if (raw == null || raw.isBlank()) {
            return CategoryNormalizeResponse.unresolved("");
        }
        return categoryService.normalize(raw.trim())
                .map(match -> CategoryNormalizeResponse.resolved(
                        match.categoryId(),
                        match.categoryName(),
                        match.categoryRaw(),
                        match.confidence(),
                        match.confidence() >= 1.0 ? "exact_name" : "alias"
                ))
                .orElseGet(() -> CategoryNormalizeResponse.unresolved(raw.trim()));
    }
}
