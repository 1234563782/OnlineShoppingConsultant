package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRecallExcludePlannerTest {

    private final MemoryRecallExcludePlanner planner = new MemoryRecallExcludePlanner();

    @Test
    void excludesBudgetWhenSessionAlreadyHasBudget() {
        Map<String, Object> session = Map.of(
                SessionContextKeys.BUDGET, Map.of("min", 2700, "max", 3300, "certainty", "STRICT")
        );

        assertThat(planner.plan(session)).containsExactly("budget");
    }

    @Test
    void excludesBrandsWhenSessionHasBrandPreferences() {
        Map<String, Object> session = Map.of(
                "brandPreferences", List.of("Apple")
        );

        assertThat(planner.plan(session)).containsExactly("brands");
    }

    @Test
    void returnsEmptyExcludeForFreshSession() {
        assertThat(planner.plan(Map.of())).isEmpty();
    }
}
