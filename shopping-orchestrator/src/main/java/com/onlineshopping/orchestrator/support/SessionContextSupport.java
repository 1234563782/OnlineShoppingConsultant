package com.onlineshopping.orchestrator.support;

import java.util.Map;

public final class SessionContextSupport {

    private SessionContextSupport() {
    }

    public static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"null".equalsIgnoreCase(s);
        }
        if (value instanceof java.util.List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (hasValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    public static String categoryLabel(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        if (hasValue(context.get(SessionContextKeys.CATEGORY_NAME))) {
            return context.get(SessionContextKeys.CATEGORY_NAME).toString();
        }
        if (hasValue(context.get(SessionContextKeys.CATEGORY_RAW))) {
            return context.get(SessionContextKeys.CATEGORY_RAW).toString();
        }
        if (hasValue(context.get(SessionContextKeys.CATEGORY_ID))) {
            return context.get(SessionContextKeys.CATEGORY_ID).toString();
        }
        return stringValue(context.get("category"));
    }

    public static boolean textsOverlap(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        String l = left.toLowerCase(java.util.Locale.ROOT);
        String r = right.toLowerCase(java.util.Locale.ROOT);
        return l.contains(r) || r.contains(l);
    }
}
