package com.onlineshopping.orchestrator.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Rule-based brand detection from user message to reduce LLM omission.
 */
@Service
public class BrandIntentDetector {

    private static final List<BrandAlias> BRAND_ALIASES = List.of(
            new BrandAlias("小米", List.of("小米", "xiaomi", "redmi", "红米")),
            new BrandAlias("荣耀", List.of("荣耀", "honor")),
            new BrandAlias("华为", List.of("华为", "huawei")),
            new BrandAlias("Apple", List.of("苹果", "apple", "iphone", "ipad")),
            new BrandAlias("Sony", List.of("索尼", "sony")),
            new BrandAlias("Lenovo", List.of("联想", "lenovo")),
            new BrandAlias("MECHREVO", List.of("机械革命", "mechrevo")),
            new BrandAlias("Edifier", List.of("漫步者", "edifier"))
    );

    public void reconcileBrandPatch(String userMessage, Map<String, Object> patch) {
        if (patch == null || userMessage == null || userMessage.isBlank()) {
            return;
        }
        detectBrand(userMessage).ifPresent(brand -> {
            patch.put("brandPreferences", List.of(brand));
        });
    }

    public Optional<String> detectBrand(String userMessage) {
        String text = userMessage.toLowerCase(Locale.ROOT);
        for (BrandAlias alias : BRAND_ALIASES) {
            for (String token : alias.tokens()) {
                if (text.contains(token.toLowerCase(Locale.ROOT))) {
                    return Optional.of(alias.label());
                }
            }
        }
        return Optional.empty();
    }

    private record BrandAlias(String label, List<String> tokens) {
    }
}
