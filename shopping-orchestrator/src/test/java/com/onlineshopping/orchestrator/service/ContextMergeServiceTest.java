package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextMergeServiceTest {

    private final ConstraintResolver constraintResolver = mock(ConstraintResolver.class);
    private final CategoryEquivalenceChecker categoryEquivalenceChecker = mock(CategoryEquivalenceChecker.class);
    private final ContextMergeService contextMergeService =
            new ContextMergeService(constraintResolver, categoryEquivalenceChecker);

    @Test
    void clearsBudgetAndBudgetAskedFieldWhenCategoryChanges() {
        Map<String, Object> current = new HashMap<>();
        current.put(SessionContextKeys.CATEGORY_RAW, "手机");
        current.put(SessionContextKeys.CATEGORY_ID, "cat_phone");
        current.put(SessionContextKeys.CATEGORY_NAME, "手机");
        current.put(SessionContextKeys.CATEGORY_RESOLUTION, "RESOLVED");
        current.put(SessionContextKeys.RESOLVED_CATEGORY_RAW, "手机");
        current.put(SessionContextKeys.BUDGET, Map.of("min", 2000, "max", 3000, "certainty", "STRICT"));
        current.put(SessionContextKeys.ASKED_FIELDS, java.util.List.of("budget", "scene", "categoryConfirm"));
        current.put(SessionContextKeys.SCENE, "通勤");

        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.CATEGORY_RAW, "耳机");

        when(categoryEquivalenceChecker.isSameCategoryAsSession(current, "耳机")).thenReturn(false);

        var result = contextMergeService.mergeSessionPatch(current, patch);

        assertThat(result.categoryReplaced()).isTrue();
        assertThat(result.sessionContext()).doesNotContainKey(SessionContextKeys.BUDGET);
        assertThat(result.sessionContext()).doesNotContainKey(SessionContextKeys.ASKED_FIELDS);
    }

    @Test
    void treatsLongTermProfileBudgetAsStillMissingForClarificationPolicy() {
        Map<String, Object> session = Map.of(
                SessionContextKeys.CATEGORY_RAW, "phone",
                SessionContextKeys.CATEGORY_ID, "cat_phone",
                SessionContextKeys.CATEGORY_NAME, "phone",
                SessionContextKeys.CATEGORY_RESOLUTION, "RESOLVED"
        );
        Map<String, Object> resolved = Map.of(
                SessionContextKeys.CATEGORY_ID, "cat_phone",
                SessionContextKeys.CATEGORY_NAME, "phone",
                SessionContextKeys.BUDGET, Map.of("min", 2000, "max", 3000, "certainty", "FLEXIBLE"),
                "budgetSource", "long_term_profile"
        );
        when(constraintResolver.resolve(anyMap(), anyMap(), anyBoolean(), anyBoolean())).thenReturn(resolved);

        Map<String, Object> effective = contextMergeService.buildEffectiveContext(session, Map.of());

        assertThat((List<?>) effective.get("missingFields")).contains("budget");
        assertThat(effective.get("resolvedConstraints")).isEqualTo(resolved);
    }

    @Test
    void acceptsSessionBudgetAsProvided() {
        Map<String, Object> session = Map.of(
                SessionContextKeys.CATEGORY_RAW, "phone",
                SessionContextKeys.CATEGORY_ID, "cat_phone",
                SessionContextKeys.CATEGORY_NAME, "phone",
                SessionContextKeys.CATEGORY_RESOLUTION, "RESOLVED"
        );
        Map<String, Object> resolved = Map.of(
                SessionContextKeys.CATEGORY_ID, "cat_phone",
                SessionContextKeys.CATEGORY_NAME, "phone",
                SessionContextKeys.BUDGET, Map.of("max", 3000, "certainty", "FLEXIBLE"),
                "budgetSource", "session_context"
        );
        when(constraintResolver.resolve(anyMap(), anyMap(), anyBoolean(), anyBoolean())).thenReturn(resolved);

        Map<String, Object> effective = contextMergeService.buildEffectiveContext(session, Map.of());

        assertThat((List<?>) effective.get("missingFields")).doesNotContain("budget");
    }
}
