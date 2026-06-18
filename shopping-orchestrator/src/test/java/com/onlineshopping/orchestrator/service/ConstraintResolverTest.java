package com.onlineshopping.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstraintResolverTest {

    private final BrandSearchKeywordResolver brandSearchKeywordResolver = mock(BrandSearchKeywordResolver.class);
    private final ConstraintResolver constraintResolver = new ConstraintResolver(brandSearchKeywordResolver);

    @Test
    void keepsNonBudgetProfileFallbackWhileBlockingBudgetFallback() {
        when(brandSearchKeywordResolver.resolve(anyMap())).thenReturn(Optional.empty());

        Map<String, Object> session = new HashMap<>();
        session.put("categoryId", "cat_phone");
        session.put("categoryName", "手机");
        session.put("categoryRaw", "手机");
        session.put("intentType", "shopping");
        session.put("userUncertain", true);

        Map<String, Object> profile = Map.of(
                "budgetMin", 2000,
                "budgetMax", 3000,
                "scene", "通勤"
        );

        Map<String, Object> resolved = constraintResolver.resolve(session, profile, true, false);

        assertThat(resolved).doesNotContainKey("budget");
        assertThat(resolved).containsEntry("scene", "通勤");
    }
}
