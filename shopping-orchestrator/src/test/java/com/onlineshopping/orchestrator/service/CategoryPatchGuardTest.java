package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CategoryPatchGuardTest {

    private final CategoryIntentDetector categoryIntentDetector = mock(CategoryIntentDetector.class);
    private final CategoryPatchGuard categoryPatchGuard = new CategoryPatchGuard(categoryIntentDetector);

    @Test
    void removesUnsupportedLlmCategoryReplaceFromSceneAnswer() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "手机",
                SessionContextKeys.CATEGORY_ID, "cat_phone"
        );
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.CATEGORY_RAW, "电脑");
        patch.put(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_LLM);
        patch.put(SessionContextKeys.SCENE, "学习");

        when(categoryIntentDetector.isSameCategoryAsSession(current, "电脑")).thenReturn(false);
        when(categoryIntentDetector.isCategorySupportedByUserMessage("学习", "电脑", current)).thenReturn(false);

        categoryPatchGuard.removeUnsupportedCategoryReplace("学习", current, patch);

        assertThat(patch)
                .doesNotContainKeys(SessionContextKeys.CATEGORY_RAW, SessionContextKeys.CATEGORY_SOURCE)
                .containsEntry(SessionContextKeys.SCENE, "学习");
    }

    @Test
    void keepsRuleDetectedCategoryReplace() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "电脑",
                SessionContextKeys.CATEGORY_ID, "cat_computer"
        );
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.CATEGORY_RAW, "手机");
        patch.put(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_RULE);

        categoryPatchGuard.removeUnsupportedCategoryReplace("我想看看手机", current, patch);

        assertThat(patch)
                .containsEntry(SessionContextKeys.CATEGORY_RAW, "手机")
                .containsEntry(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_RULE);
        verifyNoInteractions(categoryIntentDetector);
    }

    @Test
    void keepsSupportedLlmCategoryReplace() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "电脑",
                SessionContextKeys.CATEGORY_ID, "cat_computer"
        );
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.CATEGORY_RAW, "手机");
        patch.put(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_LLM);

        when(categoryIntentDetector.isSameCategoryAsSession(current, "手机")).thenReturn(false);
        when(categoryIntentDetector.isCategorySupportedByUserMessage("我想看看手机", "手机", current)).thenReturn(true);

        categoryPatchGuard.removeUnsupportedCategoryReplace("我想看看手机", current, patch);

        assertThat(patch)
                .containsEntry(SessionContextKeys.CATEGORY_RAW, "手机")
                .containsEntry(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_LLM);
    }

    @Test
    void keepsSameSessionCategoryFromLlm() {
        Map<String, Object> current = Map.of(
                SessionContextKeys.CATEGORY_RAW, "手机",
                SessionContextKeys.CATEGORY_ID, "cat_phone"
        );
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.CATEGORY_RAW, "手机");
        patch.put(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_LLM);

        when(categoryIntentDetector.isSameCategoryAsSession(current, "手机")).thenReturn(true);

        categoryPatchGuard.removeUnsupportedCategoryReplace("学习", current, patch);

        assertThat(patch)
                .containsEntry(SessionContextKeys.CATEGORY_RAW, "手机")
                .containsEntry(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_LLM);
    }
}
