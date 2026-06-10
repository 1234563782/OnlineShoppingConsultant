package com.onlineshopping.orchestrator.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MemoryRecallSupport {

    private MemoryRecallSupport() {
    }

    @SuppressWarnings("unchecked")
    public static List<String> recalledKeys(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return List.of();
        }
        Object value = sessionContext.get(SessionContextKeys.RECALLED_MEMORY_KEYS);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                keys.add(item.toString());
            }
        }
        return keys;
    }

    public static void storeRecalledKeys(Map<String, Object> sessionContext, List<String> recalledKeys) {
        if (sessionContext == null) {
            return;
        }
        if (recalledKeys == null || recalledKeys.isEmpty()) {
            sessionContext.remove(SessionContextKeys.RECALLED_MEMORY_KEYS);
            return;
        }
        sessionContext.put(SessionContextKeys.RECALLED_MEMORY_KEYS, new ArrayList<>(recalledKeys));
    }
}
