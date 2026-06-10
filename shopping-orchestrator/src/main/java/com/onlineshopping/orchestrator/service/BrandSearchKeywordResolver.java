package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BrandSearchKeywordResolver {

    public Optional<String> resolve(Map<String, Object> resolvedConstraints) {
        if (resolvedConstraints == null || resolvedConstraints.isEmpty()) {
            return Optional.empty();
        }
        List<String> brandPreferences = ProfileListNormalizer.normalizeList(
                resolvedConstraints.get("brandPreferences")
        );
        if (!brandPreferences.isEmpty()) {
            return Optional.of(pickKeyword(brandPreferences.get(0)));
        }
        List<String> mustHave = ProfileListNormalizer.normalizeList(resolvedConstraints.get("mustHave"));
        for (String item : mustHave) {
            Optional<String> keyword = extractBrandKeyword(item);
            if (keyword.isPresent()) {
                return keyword;
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractBrandKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.length() > 12) {
            return Optional.empty();
        }
        return Optional.of(pickKeyword(text));
    }

    private String pickKeyword(String raw) {
        String trimmed = raw.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("xiaomi") || trimmed.contains("小米")) {
            return "小米";
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("honor") || trimmed.contains("荣耀")) {
            return "荣耀";
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("huawei") || trimmed.contains("华为")) {
            return "华为";
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("apple") || trimmed.contains("苹果")) {
            return "Apple";
        }
        return trimmed;
    }
}
