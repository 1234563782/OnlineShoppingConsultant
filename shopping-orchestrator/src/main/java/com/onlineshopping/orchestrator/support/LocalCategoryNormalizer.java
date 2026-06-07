package com.onlineshopping.orchestrator.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fallback when catalog HTTP is unavailable; rules mirror catalog {@code CategoryService}.
 */
public final class LocalCategoryNormalizer {

    private record CategoryDef(String categoryId, String name, List<String> aliases) {
    }

    private static final List<CategoryDef> FALLBACK_CATEGORIES = List.of(
            def("cat_phone", "手机", "智能手机,安卓手机,iPhone,苹果手机"),
            def("cat_headphone", "耳机", "蓝牙耳机,降噪耳机,头戴耳机,无线耳机,入耳式,TWS,真无线"),
            def("cat_computer", "电脑", "笔记本,笔记本电脑,轻薄本,游戏本,台式机"),
            def("cat_tablet", "平板", "平板电脑,iPad,安卓平板"),
            def("cat_watch", "手表", "智能手表,运动手表,Apple Watch,华为手表"),
            def("cat_tv", "电视", "电视机,智能电视,大屏电视,客厅电视")
    );

    private LocalCategoryNormalizer() {
    }

    public static Map<String, Object> normalize(String categoryRaw) {
        String raw = normalizeText(categoryRaw);
        if (raw.isBlank()) {
            return Map.of("status", "UNRESOLVED", "categoryRaw", "", "confidence", 0.0, "matchedBy", "none");
        }
        for (CategoryDef category : FALLBACK_CATEGORIES) {
            if (matches(raw, category)) {
                double confidence = raw.equals(normalizeText(category.name())) ? 1.0 : 0.9;
                return Map.of(
                        "categoryId", category.categoryId(),
                        "categoryName", category.name(),
                        "categoryRaw", categoryRaw.trim(),
                        "confidence", confidence,
                        "status", "RESOLVED",
                        "matchedBy", confidence >= 1.0 ? "exact_name" : "alias",
                        "source", "local_fallback"
                );
            }
        }
        return Map.of(
                "status", "UNRESOLVED",
                "categoryRaw", categoryRaw.trim(),
                "confidence", 0.0,
                "matchedBy", "none",
                "source", "local_fallback"
        );
    }

    private static CategoryDef def(String id, String name, String aliasesCsv) {
        List<String> aliases = new ArrayList<>();
        if (aliasesCsv != null && !aliasesCsv.isBlank()) {
            aliases.addAll(Arrays.stream(aliasesCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList());
        }
        return new CategoryDef(id, name, aliases);
    }

    private static boolean matches(String raw, CategoryDef category) {
        String name = normalizeText(category.name());
        if (raw.equals(name) || raw.contains(name) || name.contains(raw)) {
            return true;
        }
        for (String alias : category.aliases()) {
            String normalizedAlias = normalizeText(alias);
            if (raw.equals(normalizedAlias) || raw.contains(normalizedAlias) || normalizedAlias.contains(raw)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
