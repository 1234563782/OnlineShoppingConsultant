package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.dto.MergeSessionResult;
import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContextMergeService {

    private final ConstraintResolver constraintResolver;
    private final CategoryEquivalenceChecker categoryEquivalenceChecker;

    public ContextMergeService(
            ConstraintResolver constraintResolver,
            CategoryEquivalenceChecker categoryEquivalenceChecker
    ) {
        this.constraintResolver = constraintResolver;
        this.categoryEquivalenceChecker = categoryEquivalenceChecker;
    }

    public MergeSessionResult mergeSessionPatch(
            Map<String, Object> currentSessionContext,
            Map<String, Object> extractedPatch
    ) {
        Map<String, Object> current = currentSessionContext == null ? Map.of() : currentSessionContext;
        Map<String, Object> patch = extractedPatch == null ? Map.of() : extractedPatch;

        boolean categoryReplace = shouldReplaceCategory(current, patch);
        Map<String, Object> merged = new HashMap<>();
        if (categoryReplace) {
            preserveFieldsOnCategoryReplace(merged, current);
            applyCategoryReplaceSideEffects(merged);
        } else {
            merged.putAll(current);
        }

        mergeIntentType(merged, patch);
        copyIfPresent(merged, patch, SessionContextKeys.CATEGORY_RAW);
        copyIfPresent(merged, patch, SessionContextKeys.SCENE);
        copyIfPresent(merged, patch, "notes");
        copyIfPresent(merged, patch, "userUncertain");
        copyIfPresent(merged, patch, SessionContextKeys.CATEGORY_SOURCE);

        Object budget = patch.get(SessionContextKeys.BUDGET);
        if (budget instanceof Map<?, ?> budgetMap && hasBudgetValue(budgetMap)) {
            merged.put(SessionContextKeys.BUDGET, budgetMap);
        }

        mergeStringList(merged, patch, "brandPreferences");
        mergeStringList(merged, patch, "dislikes");
        mergeStringList(merged, patch, "mustHave");

        if (categoryReplace && SessionContextSupport.hasValue(merged.get(SessionContextKeys.CATEGORY_RAW))) {
            merged.put(SessionContextKeys.CATEGORY_UPDATED_AT, OffsetDateTime.now().toString());
            if (!SessionContextSupport.hasValue(merged.get(SessionContextKeys.CATEGORY_SOURCE))) {
                merged.put(SessionContextKeys.CATEGORY_SOURCE, "llm");
            }
        }

        String reason = categoryReplace
                ? "category_raw_changed:"
                + SessionContextSupport.categoryLabel(current)
                + "->"
                + merged.get(SessionContextKeys.CATEGORY_RAW)
                : null;
        return new MergeSessionResult(merged, categoryReplace, reason);
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
        Object budget = sessionContext.get(SessionContextKeys.BUDGET);
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
        copyIfPresent(patch, sessionContext, SessionContextKeys.SCENE);
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

    private void preserveFieldsOnCategoryReplace(Map<String, Object> merged, Map<String, Object> current) {
        copyIfPresent(merged, current, "brandPreferences");
        copyIfPresent(merged, current, "dislikes");
        copyIfPresent(merged, current, "notes");
        copyIfPresent(merged, current, SessionContextKeys.INTENT_TYPE);
    }

    private void applyCategoryReplaceSideEffects(Map<String, Object> merged) {
        clearCategoryDerivedFields(merged);
        merged.remove(SessionContextKeys.SCENE);
        merged.remove(SessionContextKeys.MUST_HAVE);
        merged.remove(SessionContextKeys.PENDING_FIELD);
        merged.remove(SessionContextKeys.PENDING_QUESTION);

        List<String> askedFields = new ArrayList<>(normalizeList(merged.get(SessionContextKeys.ASKED_FIELDS)));
        askedFields.remove("categoryConfirm");
        if (askedFields.isEmpty()) {
            merged.remove(SessionContextKeys.ASKED_FIELDS);
        } else {
            merged.put(SessionContextKeys.ASKED_FIELDS, askedFields);
        }
    }

    public void clearCategoryDerivedFields(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return;
        }
        sessionContext.remove(SessionContextKeys.CATEGORY_ID);
        sessionContext.remove(SessionContextKeys.CATEGORY_NAME);
        sessionContext.remove(SessionContextKeys.CATEGORY_CONFIDENCE);
        sessionContext.remove(SessionContextKeys.CATEGORY_RESOLUTION);
        sessionContext.remove(SessionContextKeys.RESOLVED_CATEGORY_RAW);
    }

    private boolean shouldReplaceCategory(Map<String, Object> current, Map<String, Object> patch) {
        Object newCategoryRaw = patch.get(SessionContextKeys.CATEGORY_RAW);
        if (!SessionContextSupport.hasValue(newCategoryRaw)) {
            return false;
        }
        if (!hasCategoryValue(current)) {
            return false;
        }
        return !categoryEquivalenceChecker.isSameCategoryAsSession(
                current,
                newCategoryRaw.toString().trim()
        );
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (SessionContextSupport.hasValue(value)) {
            target.put(key, value);
        }
    }

    private void mergeIntentType(Map<String, Object> target, Map<String, Object> source) {
        Object value = source == null ? null : source.get(SessionContextKeys.INTENT_TYPE);
        if (!SessionContextSupport.hasValue(value)) {
            return;
        }
        String incoming = value.toString();
        boolean hasShoppingContext = hasCategoryValue(target)
                || hasBudgetValue(target.get(SessionContextKeys.BUDGET))
                || SessionContextSupport.hasValue(target.get(SessionContextKeys.SCENE));
        if (hasShoppingContext
                && ("small_talk".equalsIgnoreCase(incoming) || "non_shopping".equalsIgnoreCase(incoming))) {
            target.put(SessionContextKeys.INTENT_TYPE, "shopping");
            return;
        }
        target.put(SessionContextKeys.INTENT_TYPE, incoming);
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
            if (SessionContextSupport.hasValue(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBudgetValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        return SessionContextSupport.hasValue(map.get("min")) || SessionContextSupport.hasValue(map.get("max"));
    }

    private boolean hasCategoryValue(Map<String, Object> context) {
        return SessionContextSupport.hasValue(categoryValue(context));
    }

    private Object categoryValue(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        if (SessionContextSupport.hasValue(context.get(SessionContextKeys.CATEGORY_ID))) {
            return context.get(SessionContextKeys.CATEGORY_ID);
        }
        if (SessionContextSupport.hasValue(context.get(SessionContextKeys.CATEGORY_NAME))) {
            return context.get(SessionContextKeys.CATEGORY_NAME);
        }
        if (SessionContextSupport.hasValue(context.get(SessionContextKeys.CATEGORY_RAW))) {
            return context.get(SessionContextKeys.CATEGORY_RAW);
        }
        return context.get("category");
    }

    private List<String> missingFields(Map<String, Object> effective, Map<String, Object> resolvedConstraints) {
        List<String> missing = new ArrayList<>();
        String categoryResolution = SessionContextSupport.stringValue(effective.get(SessionContextKeys.CATEGORY_RESOLUTION));
        boolean hasCategoryId = SessionContextSupport.hasValue(effective.get(SessionContextKeys.CATEGORY_ID));
        boolean hasCategoryRaw = SessionContextSupport.hasValue(effective.get(SessionContextKeys.CATEGORY_RAW));
        List<String> askedFields = normalizeList(effective.get(SessionContextKeys.ASKED_FIELDS));

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

        if (!hasBudgetValue(resolvedConstraints.get(SessionContextKeys.BUDGET))) {
            missing.add("budget");
        }
        if (!SessionContextSupport.hasValue(resolvedConstraints.get(SessionContextKeys.SCENE))) {
            missing.add("scene");
        }
        return missing;
    }
}
