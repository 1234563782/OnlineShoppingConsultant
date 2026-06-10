package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds agent-facing constraints: session wins over profile; profile only fills gaps.
 */
@Service
public class ConstraintResolver {

    private final BrandSearchKeywordResolver brandSearchKeywordResolver;

    public ConstraintResolver(BrandSearchKeywordResolver brandSearchKeywordResolver) {
        this.brandSearchKeywordResolver = brandSearchKeywordResolver;
    }

    public Map<String, Object> resolve(
            Map<String, Object> sessionContext,
            Map<String, Object> longTermProfile,
            boolean allowProfileFallback
    ) {
        Map<String, Object> session = sessionContext == null ? Map.of() : sessionContext;
        Map<String, Object> profile = longTermProfile == null ? Map.of() : longTermProfile;
        Map<String, Object> resolved = new HashMap<>();

        copyIfPresent(resolved, session, "categoryId");
        copyIfPresent(resolved, session, "categoryName");
        copyIfPresent(resolved, session, "categoryRaw");
        copyIfPresent(resolved, session, "scene");
        copyIfPresent(resolved, session, "userUncertain");
        copyIfPresent(resolved, session, "intentType");

        Object sessionBudget = session.get("budget");
        if (hasBudgetValue(sessionBudget)) {
            resolved.put("budget", sessionBudget);
            resolved.put("budgetSource", "session_context");
        } else if (allowProfileFallback && shouldFallbackScalar(session, "budget")) {
            Map<String, Object> profileBudget = profileBudget(profile);
            if (!profileBudget.isEmpty()) {
                resolved.put("budget", profileBudget);
                resolved.put("budgetSource", "long_term_profile");
            }
        }

        copyList(resolved, session, "mustHave");
        copyList(resolved, session, "dislikes");
        copyList(resolved, session, "brandPreferences");
        copyList(resolved, session, "notes");

        if (allowProfileFallback) {
            if (!hasValue(resolved.get("scene")) && shouldFallbackScalar(session, "scene")) {
                fillIfMissing(resolved, profile, "scene");
            }
            applyProfilePreferenceFallback(resolved, profile, session);
        }

        attachSearchHints(resolved);
        return resolved;
    }

    private void attachSearchHints(Map<String, Object> resolved) {
        Map<String, Object> hints = new HashMap<>();
        brandSearchKeywordResolver.resolve(resolved).ifPresent(keyword -> hints.put("brandKeyword", keyword));
        Object budget = resolved.get("budget");
        if (budget instanceof Map<?, ?> budgetMap) {
            hints.put("budget", budgetMap);
        }
        hints.put(
                "fallbackPolicy",
                "有品牌时：先同品牌同预算，再同品牌其他价位，再无品牌同价位，最后同品类其他价位；无品牌时：先同品类同预算，再同品类其他价位"
        );
        if (!hints.isEmpty()) {
            resolved.put("searchHints", hints);
        }
    }

    private void applyProfilePreferenceFallback(
            Map<String, Object> resolved,
            Map<String, Object> profile,
            Map<String, Object> session
    ) {
        List<String> sessionMustHave = normalizeList(session.get("mustHave"));
        List<String> sessionDislikes = normalizeList(resolved.get("dislikes"));
        List<String> sessionBrands = normalizeList(resolved.get("brandPreferences"));
        List<String> sessionNotes = normalizeList(resolved.get("notes"));

        if (!sessionMustHave.isEmpty()) {
            return;
        }
        if (!shouldFallbackPreferences(session)) {
            return;
        }

        if (sessionBrands.isEmpty()) {
            mergeProfileListIfMissing(resolved, profile, "brandPreferences");
        }
        if (sessionDislikes.isEmpty()) {
            List<String> profileDislikes = normalizeList(profile.get("dislikes"));
            List<String> filtered = filterConflictingWithMustHave(profileDislikes, sessionMustHave);
            if (!filtered.isEmpty()) {
                resolved.put("dislikes", filtered);
            }
        }
        if (sessionNotes.isEmpty()) {
            mergeProfileListIfMissing(resolved, profile, "notes");
        }
    }

    private boolean shouldFallbackScalar(Map<String, Object> session, String field) {
        if ("budget".equals(field) && hasBudgetValue(session.get("budget"))) {
            return false;
        }
        if ("scene".equals(field) && hasValue(session.get("scene"))) {
            return false;
        }
        if (Boolean.TRUE.equals(session.get("userUncertain"))) {
            return true;
        }
        List<String> askedFields = normalizeList(session.get("askedFields"));
        return askedFields.contains(field);
    }

    private boolean shouldFallbackPreferences(Map<String, Object> session) {
        if (Boolean.TRUE.equals(session.get("userUncertain"))) {
            return true;
        }
        return shouldFallbackScalar(session, "budget") || shouldFallbackScalar(session, "scene");
    }

    private List<String> filterConflictingWithMustHave(List<String> dislikes, List<String> mustHave) {
        if (dislikes.isEmpty() || mustHave.isEmpty()) {
            return dislikes;
        }
        List<String> result = new ArrayList<>();
        for (String dislike : dislikes) {
            boolean conflicts = mustHave.stream().anyMatch(must -> textsOverlap(dislike, must));
            if (!conflicts) {
                result.add(dislike);
            }
        }
        return result;
    }

    private boolean textsOverlap(String left, String right) {
        return ProfileListNormalizer.containsIgnoreCase(left, right)
                || ProfileListNormalizer.containsIgnoreCase(right, left);
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (hasValue(value)) {
            target.put(key, value);
        }
    }

    private void copyList(Map<String, Object> target, Map<String, Object> source, String key) {
        List<String> values = normalizeList(source.get(key));
        if (!values.isEmpty()) {
            target.put(key, values);
        }
    }

    private void fillIfMissing(Map<String, Object> target, Map<String, Object> source, String key) {
        if (!hasValue(target.get(key)) && hasValue(source.get(key))) {
            target.put(key, source.get(key));
        }
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
        return ProfileListNormalizer.normalizeList(value);
    }

    private boolean hasBudgetValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        return hasValue(map.get("min")) || hasValue(map.get("max"));
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
            for (Object item : map.values()) {
                if (hasValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
}
