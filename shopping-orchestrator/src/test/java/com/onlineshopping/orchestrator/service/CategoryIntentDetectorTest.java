package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryIntentDetectorTest {

    private final CategoryClientService categoryClientService = mock(CategoryClientService.class);
    private final CategoryEquivalenceChecker categoryEquivalenceChecker = mock(CategoryEquivalenceChecker.class);
    private final CategoryIntentDetector detector =
            new CategoryIntentDetector(categoryClientService, categoryEquivalenceChecker);

    @Test
    void masksCompareProductNamesBeforeInferringCategory() {
        when(categoryClientService.normalize(anyString())).thenReturn(unresolved());
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.SHOPPING_SUB_INTENT, SessionContextKeys.SUB_INTENT_COMPARE);
        patch.put(SessionContextKeys.COMPARE_TARGETS, Map.of(
                "productNames", java.util.List.of("AlphaPad Ultra", "BetaBook Air"),
                "ordinalRefs", new ArrayList<>(),
                "skuIds", new ArrayList<>()
        ));

        detector.reconcileCategoryPatch(
                "AlphaPadUltra and BetaBookAir compare",
                Map.of(),
                patch
        );

        assertThat(patch).doesNotContainKey(SessionContextKeys.CATEGORY_RAW);
        verify(categoryClientService, never()).normalize(argThat(text ->
                text != null && (text.contains("AlphaPad") || text.contains("BetaBook"))
        ));
    }

    @Test
    void stillInfersCategoryWhenCategoryAppearsOutsideCompareProductNames() {
        when(categoryClientService.normalize(anyString())).thenReturn(unresolved());
        when(categoryClientService.normalize(argThat(text -> text != null && text.contains("电脑"))))
                .thenReturn(resolved("cat_computer", "电脑"));
        Map<String, Object> patch = new HashMap<>();
        patch.put(SessionContextKeys.SHOPPING_SUB_INTENT, SessionContextKeys.SUB_INTENT_COMPARE);
        patch.put(SessionContextKeys.COMPARE_TARGETS, Map.of(
                "productNames", java.util.List.of("AlphaPad Ultra", "BetaBook Air"),
                "ordinalRefs", new ArrayList<>(),
                "skuIds", new ArrayList<>()
        ));

        detector.reconcileCategoryPatch(
                "AlphaPad Ultra 和 BetaBook Air 这两台电脑比一下",
                Map.of(),
                patch
        );

        assertThat(patch).containsEntry(SessionContextKeys.CATEGORY_RAW, "电脑");
        assertThat(patch).containsEntry(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_RULE);
    }

    private Map<String, Object> unresolved() {
        return Map.of("status", CategoryResolutionResult.STATUS_UNRESOLVED);
    }

    private Map<String, Object> resolved(String categoryId, String categoryName) {
        return Map.of(
                "status", CategoryResolutionResult.STATUS_RESOLVED,
                "categoryId", categoryId,
                "categoryName", categoryName,
                "confidence", 1.0
        );
    }
}
