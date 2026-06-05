package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.model.CategoryEntity;
import com.onlineshopping.catalog.repo.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Optional<CategoryMatch> normalize(String categoryRaw) {
        String raw = normalizeText(categoryRaw);
        if (raw.isBlank()) {
            return Optional.empty();
        }
        return categoryRepository.findByEnabledTrue().stream()
                .filter(category -> matches(raw, category))
                .findFirst()
                .map(category -> new CategoryMatch(
                        category.getCategoryId(),
                        category.getName(),
                        categoryRaw,
                        raw.equals(normalizeText(category.getName())) ? 1.0 : 0.9
                ));
    }

    private boolean matches(String raw, CategoryEntity category) {
        String name = normalizeText(category.getName());
        if (raw.equals(name) || raw.contains(name) || name.contains(raw)) {
            return true;
        }
        String aliases = category.getAliases();
        if (aliases == null || aliases.isBlank()) {
            return false;
        }
        return Arrays.stream(aliases.split(","))
                .map(this::normalizeText)
                .anyMatch(alias -> raw.equals(alias) || raw.contains(alias) || alias.contains(raw));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CategoryMatch(String categoryId, String categoryName, String categoryRaw, double confidence) {
    }
}
