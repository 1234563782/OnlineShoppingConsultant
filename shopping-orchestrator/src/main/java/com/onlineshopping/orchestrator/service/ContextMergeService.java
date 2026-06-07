package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContextMergeService {

    private final ConstraintResolver constraintResolver;

    public ContextMergeService(ConstraintResolver constraintResolver) {
        this.constraintResolver = constraintResolver;
    }

    public Map<String, Object> mergeSessionPatch(
            Map<String, Object> currentSessionContext,
            Map<String, Object> extractedPatch
    ) {
        Map<String, Object> current = currentSessionContext == null ? Map.of() : currentSessionContext;
        Map<String, Object> merged = new HashMap<>(isCategoryChanged(current, extractedPatch) ? Map.of() : current);
        mergeIntentType(merged, extractedPatch);
        copyIfPresent(merged, extractedPatch, "categoryRaw");
        copyIfPresent(merged, extractedPatch, "categoryId");
        copyIfPresent(merged, extractedPatch, "categoryName");
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
        Map<String, Object> session = sessionContext == null ? Map.of() : sessionContext;
        Map<String, Object> effective = new HashMap<>(session);
        Map<String, Object> resolvedConstraints = constraintResolver.resolve(
                session,
                longTermProfile,
                allowLongTermFallback
        );
        effective.put("resolvedConstraints", resolvedConstraints);
        effective.put("longTermFallbackUsed", allowLongTermFallback);
        effective.put("missingFields", missingFields(effective, resolvedConstraints));
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
        List<String> notes = ProfileListNormalizer.normalizeList(rawPatch.get("notes"));
        if (!notes.isEmpty()) {
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

    private void mergeIntentType(Map<String, Object> target, Map<String, Object> source) {
        Object value = source == null ? null : source.get("intentType");
        if (!hasValue(value)) {
            return;
        }
        String incoming = value.toString();
        boolean hasShoppingContext = hasCategoryValue(target)
                || hasBudgetValue(target.get("budget"))
                || hasValue(target.get("scene"));
        if (hasShoppingContext
                && ("small_talk".equalsIgnoreCase(incoming) || "non_shopping".equalsIgnoreCase(incoming))) {
            target.put("intentType", "shopping");
            return;
        }
        target.put("intentType", incoming);
    }

    private boolean isCategoryChanged(Map<String, Object> current, Map<String, Object> extractedPatch) {
        Object oldCategory = categoryValue(current);
        Object newCategory = categoryValue(extractedPatch);
        return hasValue(oldCategory)
                && hasValue(newCategory)
                && !oldCategory.toString().equalsIgnoreCase(newCategory.toString());
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

    private List<String> normalizeList(Object value) {
        return ProfileListNormalizer.normalizeList(value);
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

    private boolean hasCategoryValue(Map<String, Object> context) {
        return hasValue(categoryValue(context));
    }

    private Object categoryValue(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        if (hasValue(context.get("categoryId"))) {
            return context.get("categoryId");
        }
        if (hasValue(context.get("categoryName"))) {
            return context.get("categoryName");
        }
        if (hasValue(context.get("categoryRaw"))) {
            return context.get("categoryRaw");
        }
        return context.get("category");
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

    private List<String> missingFields(Map<String, Object> effective, Map<String, Object> resolvedConstraints) {
        List<String> missing = new ArrayList<>();
        String categoryResolution = stringValue(effective.get("categoryResolution"));
        boolean hasCategoryId = hasValue(effective.get("categoryId"));
        boolean hasCategoryRaw = hasValue(effective.get("categoryRaw"));
        List<String> askedFields = normalizeList(effective.get("askedFields"));

        if (!hasCategoryRaw && !hasCategoryId) {
            missing.add("category");
        } else if (CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE.equals(categoryResolution)) {
            missing.add("category");
        } else if (CategoryResolutionResult.STATUS_UNRESOLVED.equals(categoryResolution)) {
            missing.add("category");
        } else if (CategoryResolutionResult.STATUS_LOW_CONFIDENCE.equals(categoryResolution)
                && !askedFields.contains("categoryConfirm")) {
            missing.add("categoryConfirm");
        } else if (!hasCategoryId) {
            missing.add("category");
        }

        if (!hasBudgetValue(resolvedConstraints.get("budget"))) {
            missing.add("budget");
        }
        if (!hasValue(resolvedConstraints.get("scene"))) {
            missing.add("scene");
        }
        return missing;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }
}
