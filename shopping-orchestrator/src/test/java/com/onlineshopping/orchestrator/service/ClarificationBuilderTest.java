package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationBuilderTest {

    private final ClarificationBuilder builder = new ClarificationBuilder();

    @Test
    void doesNotAskBudgetForGenericMissingBudgetRequest() {
        Map<String, Object> context = Map.of(
                SessionContextKeys.CATEGORY_NAME, "phone",
                "missingFields", List.of("budget"),
                "userUncertain", false,
                SessionContextKeys.BUDGET_UNCERTAIN, false
        );

        ClarificationBuilder.Clarification clarification = builder.buildIfNeeded(context);

        assertThat(clarification).isNull();
    }

    @Test
    void asksBudgetWhenUserExplicitlySaysBudgetIsUncertain() {
        Map<String, Object> context = Map.of(
                SessionContextKeys.CATEGORY_NAME, "phone",
                "missingFields", List.of("budget"),
                "userUncertain", true,
                SessionContextKeys.BUDGET_UNCERTAIN, true
        );

        ClarificationBuilder.Clarification clarification = builder.buildIfNeeded(context);

        assertThat(clarification).isNotNull();
        assertThat(clarification.field()).isEqualTo("budget");
        assertThat(clarification.message()).contains("phone");
    }

    @Test
    void doesNotTreatBrowseOnlyUncertaintyAsBudgetClarification() {
        Map<String, Object> context = Map.of(
                SessionContextKeys.CATEGORY_NAME, "phone",
                "missingFields", List.of("budget"),
                "userUncertain", true,
                SessionContextKeys.BUDGET_UNCERTAIN, false
        );

        ClarificationBuilder.Clarification clarification = builder.buildIfNeeded(context);

        assertThat(clarification).isNull();
    }
}
