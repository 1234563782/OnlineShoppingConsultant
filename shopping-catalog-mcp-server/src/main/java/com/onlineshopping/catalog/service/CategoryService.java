package com.onlineshopping.catalog.service;

import com.onlineshopping.catalog.model.CategoryEntity;
import com.onlineshopping.catalog.repo.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Service
public class CategoryService {

    private static final double MIN_MATCH_SCORE = 40.0;

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Optional<CategoryMatch> normalize(String categoryRaw) {
        String raw = normalizeText(categoryRaw);
        if (raw.isBlank()) {
            return Optional.empty();
        }

        CategoryMatch best = null;
        double bestScore = 0.0;
        for (CategoryEntity category : categoryRepository.findByEnabledTrue()) {
            ScoredMatch scored = scoreMatch(raw, category);
            if (scored.score() > bestScore) {
                bestScore = scored.score();
                best = new CategoryMatch(
                        category.getCategoryId(),
                        category.getName(),
                        categoryRaw,
                        scored.confidence(),
                        scored.matchedBy()
                );
            }
        }
        if (best == null || bestScore < MIN_MATCH_SCORE) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private ScoredMatch scoreMatch(String raw, CategoryEntity category) {
        String name = normalizeText(category.getName());
        if (raw.equals(name)) {
            return new ScoredMatch(100.0, 1.0, "exact_name");
        }

        String aliases = category.getAliases();
        if (aliases != null && !aliases.isBlank()) {
            for (String alias : aliases.split(",")) {
                String normalizedAlias = normalizeText(alias);
                if (normalizedAlias.isBlank()) {
                    continue;
                }
                if (raw.equals(normalizedAlias)) {
                    return new ScoredMatch(95.0, 0.95, "exact_alias");
                }
            }
        }

        if (name.length() >= 2 && raw.contains(name)) {
            return new ScoredMatch(70.0 + name.length(), 0.88, "raw_contains_name");
        }
        if (raw.length() >= 2 && name.contains(raw)) {
            return new ScoredMatch(60.0 + raw.length(), 0.85, "name_contains_raw");
        }

        if (aliases != null && !aliases.isBlank()) {
            ScoredMatch bestAlias = ScoredMatch.none();
            for (String alias : aliases.split(",")) {
                String normalizedAlias = normalizeText(alias);
                if (normalizedAlias.length() < 2) {
                    continue;
                }
                if (raw.contains(normalizedAlias)) {
                    double score = 50.0 + normalizedAlias.length();
                    if (score > bestAlias.score()) {
                        bestAlias = new ScoredMatch(score, 0.82, "raw_contains_alias");
                    }
                }
                if (raw.length() >= 2 && normalizedAlias.contains(raw)) {
                    double score = 40.0 + raw.length();
                    if (score > bestAlias.score()) {
                        bestAlias = new ScoredMatch(score, 0.80, "alias_contains_raw");
                    }
                }
            }
            if (bestAlias.score() > 0) {
                return bestAlias;
            }
        }

        return ScoredMatch.none();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredMatch(double score, double confidence, String matchedBy) {
        private static ScoredMatch none() {
            return new ScoredMatch(0.0, 0.0, "none");
        }
    }

    public record CategoryMatch(
            String categoryId,
            String categoryName,
            String categoryRaw,
            double confidence,
            String matchedBy
    ) {
    }
}
