package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gates long-term memory writes: session-scoped slots must not pollute profile.
 */
@Service
public class MemoryWriteFilter {

    private static final List<String> STABLE_PREFERENCE_SIGNALS = List.of(
            "以后", "平时", "一直", "每次都", "默认", "长期", "习惯", "经常",
            "always", "usually", "prefer"
    );

    public Map<String, Object> filter(
            Map<String, Object> patch,
            String userMessage,
            boolean fromLongTermExtraction
    ) {
        if (patch == null || patch.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new HashMap<>(patch);
        filtered.remove("categoryRaw");
        filtered.remove("categoryId");
        filtered.remove("categoryName");
        filtered.remove("category");
        filtered.remove("intentType");
        filtered.remove("userUncertain");

        boolean stable = expressesStablePreference(userMessage);
        if (!stable) {
            filtered.remove("budgetMin");
            filtered.remove("budgetMax");
            filtered.remove("scene");
            if (!fromLongTermExtraction) {
                filtered.remove("notes");
            }
        }

        removeEmptyLists(filtered);
        return filtered.isEmpty() ? Map.of() : filtered;
    }

    private boolean expressesStablePreference(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        for (String signal : STABLE_PREFERENCE_SIGNALS) {
            if (text.contains(signal.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void removeEmptyLists(Map<String, Object> patch) {
        List.of("brandPreferences", "dislikes", "notes").forEach(key -> {
            if (ProfileListNormalizer.normalizeList(patch.get(key)).isEmpty()) {
                patch.remove(key);
            }
        });
    }
}
