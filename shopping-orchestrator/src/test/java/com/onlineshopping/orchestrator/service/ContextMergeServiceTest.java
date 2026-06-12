package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.MergeSessionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextMergeServiceTest {

    private final ContextMergeService contextMergeService = new ContextMergeService(
            new ConstraintResolver(new BrandSearchKeywordResolver()),
            new AlwaysDifferentCategoryEquivalenceChecker()
    );

    @Test
    void categoryReplaceClearsOldBudgetAndDerivedCategoryFields() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "电脑",
                SessionContextKeys.CATEGORY_ID, "cat_computer",
                SessionContextKeys.CATEGORY_NAME, "电脑",
                SessionContextKeys.CATEGORY_CONFIDENCE, 1.0,
                SessionContextKeys.CATEGORY_RESOLUTION, "resolved",
                SessionContextKeys.RESOLVED_CATEGORY_RAW, "电脑",
                SessionContextKeys.BUDGET, Map.of("min", 2700, "max", 3300, "certainty", "FLEXIBLE"),
                SessionContextKeys.SCENE, "办公",
                SessionContextKeys.MUST_HAVE, List.of("轻薄"),
                "brandPreferences", List.of("Lenovo")
        );
        Map<String, Object> patch = Map.of(
                SessionContextKeys.CATEGORY_RAW, "手机",
                SessionContextKeys.INTENT_TYPE, "shopping"
        );

        MergeSessionResult result = contextMergeService.mergeSessionPatch(current, patch);

        assertThat(result.categoryReplaced()).isTrue();
        assertThat(result.sessionContext())
                .containsEntry(SessionContextKeys.CATEGORY_RAW, "手机")
                .containsEntry("brandPreferences", List.of("Lenovo"))
                .doesNotContainKeys(
                        SessionContextKeys.CATEGORY_ID,
                        SessionContextKeys.CATEGORY_NAME,
                        SessionContextKeys.CATEGORY_CONFIDENCE,
                        SessionContextKeys.CATEGORY_RESOLUTION,
                        SessionContextKeys.RESOLVED_CATEGORY_RAW,
                        SessionContextKeys.BUDGET,
                        SessionContextKeys.SCENE,
                        SessionContextKeys.MUST_HAVE
                );
    }

    @Test
    void categoryReplaceUsesNewBudgetWhenPatchProvidesOne() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "电脑",
                SessionContextKeys.CATEGORY_ID, "cat_computer",
                SessionContextKeys.BUDGET, Map.of("min", 2700, "max", 3300, "certainty", "FLEXIBLE")
        );
        Map<String, Object> newBudget = Map.of("min", 4500, "max", 5500, "certainty", "STRICT");
        Map<String, Object> patch = Map.of(
                SessionContextKeys.CATEGORY_RAW, "手机",
                SessionContextKeys.BUDGET, newBudget
        );

        MergeSessionResult result = contextMergeService.mergeSessionPatch(current, patch);

        assertThat(result.categoryReplaced()).isTrue();
        assertThat(result.sessionContext())
                .containsEntry(SessionContextKeys.CATEGORY_RAW, "手机")
                .containsEntry(SessionContextKeys.BUDGET, newBudget);
    }

    @Test
    void resetsCompareSubIntentToDiscoverWhenPatchHasNoCompareSignals() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.SHOPPING_SUB_INTENT, SessionContextKeys.SUB_INTENT_COMPARE,
                SessionContextKeys.INTENT_TYPE, "shopping",
                SessionContextKeys.CATEGORY_RAW, "手机"
        );
        Map<String, Object> patch = Map.of(
                SessionContextKeys.CATEGORY_RAW, "耳机",
                SessionContextKeys.INTENT_TYPE, "shopping"
        );

        MergeSessionResult result = contextMergeService.mergeSessionPatch(current, patch);

        assertThat(result.sessionContext())
                .containsEntry(SessionContextKeys.SHOPPING_SUB_INTENT, SessionContextKeys.SUB_INTENT_DISCOVER);
    }

    private static class AlwaysDifferentCategoryEquivalenceChecker extends CategoryEquivalenceChecker {
        private AlwaysDifferentCategoryEquivalenceChecker() {
            super(null);
        }

        @Override
        public boolean isSameCategoryAsSession(Map<String, Object> sessionContext, String newCategoryRaw) {
            return false;
        }
    }
}
