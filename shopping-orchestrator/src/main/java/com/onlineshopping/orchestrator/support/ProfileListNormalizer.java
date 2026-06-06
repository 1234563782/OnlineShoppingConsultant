package com.onlineshopping.orchestrator.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProfileListNormalizer {

    public static final String RECONCILE_REPLACE_KEY = "_reconcileReplace";

    private ProfileListNormalizer() {
    }

    public static List<String> normalizeList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString().trim());
                }
            }
            return result;
        }
        String text = value.toString().trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return List.of();
        }
        return List.of(text);
    }

    public static List<String> union(List<String> left, List<String> right) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        merged.addAll(left == null ? List.of() : left);
        merged.addAll(right == null ? List.of() : right);
        return new ArrayList<>(merged);
    }

    public static boolean hasPreferenceIncoming(Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return false;
        }
        return !normalizeList(patch.get("brandPreferences")).isEmpty()
                || !normalizeList(patch.get("dislikes")).isEmpty()
                || !normalizeList(patch.get("notes")).isEmpty();
    }

    public static boolean containsIgnoreCase(String text, String needle) {
        return text != null
                && needle != null
                && !needle.isBlank()
                && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
