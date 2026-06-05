package com.onlineshopping.orchestrator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContextMergeService {

    public Map<String, Object> mergeSessionPatch(
            Map<String, Object> currentSessionContext,
            Map<String, Object> extractedPatch
    ) {
        Map<String, Object> current = currentSessionContext == null ? Map.of() : currentSessionContext;
        Map<String, Object> merged = new HashMap<>(isCategoryChanged(current, extractedPatch) ? Map.of() : current);
        copyIfPresent(merged, extractedPatch, "intentType");
        copyIfPresent(merged, extractedPatch, "category");
        copyIfPresent(merged, extractedPatch, "scene");
        copyIfPresent(merged, extractedPatch, "notes");
        copyIfPresent(merged, extractedPatch, "userUncertain");

        Object budget = extractedPatch.get("budget");
        if (budget instanceof Map<?, ?> budgetMap && hasBudgetValue(budgetMap)) {
            merged.put("budget", budgetMap);
        }
        mergeStringList(merged, extractedPatch, "brandPreferences");
        mergeStringList(merged, extractedPatch, "dislikes");
        mergeStringList(merged, extractedPatch, "mustHave");
        return merged;
    }

    public Map<String, Object> buildEffectiveContext(
            Map<String, Object> sessionContext,
            Map<String, Object> longTermProfile
    ) {
        return buildEffectiveContext(sessionContext, longTermProfile, true);
    }

    public Map<String, Object> buildEffectiveContext(
            Map<String, Object> sessionContext,
            Map<String, Object> longTermProfile,
            boolean allowLongTermFallback
    ) {
        Map<String, Object> effective = new HashMap<>(sessionContext == null ? Map.of() : sessionContext);
        Map<String, Object> profile = longTermProfile == null ? Map.of() : longTermProfile;
        effective.put("longTermProfileReference", profile);

        if (allowLongTermFallback && !hasBudgetValue(effective.get("budget"))) {
            Map<String, Object> profileBudget = profileBudget(profile);
            if (!profileBudget.isEmpty()) {
                effective.put("budget", profileBudget);
                effective.put("budgetSource", "long_term_profile");
            }
        } else {
            effective.put("budgetSource", "session_context");
        }

        if (allowLongTermFallback) {
            fillIfMissing(effective, profile, "scene");
            mergeProfileListIfMissing(effective, profile, "brandPreferences");
            mergeProfileListIfMissing(effective, profile, "dislikes");
        }
        if (allowLongTermFallback && !hasValue(effective.get("notes")) && hasValue(profile.get("notes"))) {
            effective.put("notes", profile.get("notes"));
        }
        effective.put("longTermFallbackUsed", allowLongTermFallback);
        effective.put("missingFields", missingFields(effective));
        return effective;
    }

    public Map<String, Object> toMemoryPatch(Map<String, Object> sessionContext) {
        Map<String, Object> patch = new HashMap<>();
        Object budget = sessionContext.get("budget");
        if (budget instanceof Map<?, ?> budgetMap && hasBudgetValue(budgetMap)) {
            Object min = budgetMap.get("min");
            Object max = budgetMap.get("max");
            if (min != null) {
                patch.put("budgetMin", min);
            }
            if (max != null) {
                patch.put("budgetMax", max);
            }
        }
        copyIfPresent(patch, sessionContext, "scene");
        copyIfPresent(patch, sessionContext, "brandPreferences");
        copyIfPresent(patch, sessionContext, "dislikes");
        copyIfPresent(patch, sessionContext, "notes");
        return patch;
    }

    public Map<String, Object> toLongTermMemoryPatch(Map<String, Object> extractedPatch) {
        if (extractedPatch == null || !(extractedPatch.get("longTermMemoryPatch") instanceof Map<?, ?> rawPatch)) {
            return Map.of();
        }
        Map<String, Object> patch = new HashMap<>();
        List<String> brandPreferences = normalizeList(rawPatch.get("brandPreferences"));
        if (!brandPreferences.isEmpty()) {
            patch.put("brandPreferences", brandPreferences);
        }
        List<String> dislikes = normalizeList(rawPatch.get("dislikes"));
        if (!dislikes.isEmpty()) {
            patch.put("dislikes", dislikes);
        }
        Object notes = rawPatch.get("notes");
        if (hasValue(notes)) {
            patch.put("notes", notes);
        }
        return patch;
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (hasValue(value)) {
            target.put(key, value);
        }
    }

    private boolean isCategoryChanged(Map<String, Object> current, Map<String, Object> extractedPatch) {
        Object oldCategory = current.get("category");
        Object newCategory = extractedPatch == null ? null : extractedPatch.get("category");
        return hasValue(oldCategory)
                && hasValue(newCategory)
                && !oldCategory.toString().equalsIgnoreCase(newCategory.toString());
    }

    private void fillIfMissing(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!hasValue(target.get(key)) && hasValue(source.get(key))) {
            target.put(key, source.get(key));
        }
    }

    private void mergeStringList(Map<String, Object> target, Map<String, Object> source, String key) {
        List<String> incoming = normalizeList(source == null ? null : source.get(key));
        if (incoming.isEmpty()) {
            return;
        }
        Set<String> merged = new LinkedHashSet<>(normalizeList(target.get(key)));
        merged.addAll(incoming);
        target.put(key, new ArrayList<>(merged));
    }

    private void mergeProfileListIfMissing(Map<String, Object> target, Map<String, Object> profile, String key) {
        if (normalizeList(target.get(key)).isEmpty()) {
            List<String> profileValues = normalizeList(profile.get(key));
            if (!profileValues.isEmpty()) {
                target.put(key, profileValues);
            }
        }
    }

    private List<String> normalizeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString().trim());
            }
        }
        return result;
    }

    private boolean hasAnyValue(Map<?, ?> map) {
        for (Object value : map.values()) {
            if (hasValue(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBudgetValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        return hasValue(map.get("min")) || hasValue(map.get("max"));
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"null".equalsIgnoreCase(s);
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return hasAnyValue(map);
        }
        return true;
    }

    private Map<String, Object> profileBudget(Map<String, Object> profile) {
        Map<String, Object> budget = new HashMap<>();
        if (profile.get("budgetMin") != null) {
            budget.put("min", profile.get("budgetMin"));
        }
        if (profile.get("budgetMax") != null) {
            budget.put("max", profile.get("budgetMax"));
        }
        if (!budget.isEmpty()) {
            budget.put("certainty", "FLEXIBLE");
        }
        return budget;
    }

    private List<String> missingFields(Map<String, Object> effective) {
        List<String> missing = new ArrayList<>();
        if (!hasValue(effective.get("category"))) {
            missing.add("category");
        }
        if (!hasBudgetValue(effective.get("budget"))) {
            missing.add("budget");
        }
        if (!hasValue(effective.get("scene"))) {
            missing.add("scene");
        }
        return missing;
    }
}
