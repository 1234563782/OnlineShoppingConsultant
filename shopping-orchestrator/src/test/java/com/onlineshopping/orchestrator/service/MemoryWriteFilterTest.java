package com.onlineshopping.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryWriteFilterTest {

    private final MemoryWriteFilter memoryWriteFilter = new MemoryWriteFilter();

    @Test
    void removesTemporaryBudgetAndSceneWithoutStableSignal() {
        Map<String, Object> patch = Map.of(
                "budgetMin", 2700,
                "budgetMax", 3300,
                "scene", "学习",
                "brandPreferences", List.of("Apple")
        );

        Map<String, Object> filtered = memoryWriteFilter.filter(patch, "预算3000", false);

        assertThat(filtered)
                .containsEntry("brandPreferences", List.of("Apple"))
                .doesNotContainKeys("budgetMin", "budgetMax", "scene");
    }

    @Test
    void keepsBudgetWhenUserExpressesStablePreference() {
        Map<String, Object> patch = Map.of(
                "budgetMin", 3000,
                "budgetMax", 3000
        );

        Map<String, Object> filtered = memoryWriteFilter.filter(
                patch,
                "我以后买手机预算都是3000",
                false
        );

        assertThat(filtered)
                .containsEntry("budgetMin", 3000)
                .containsEntry("budgetMax", 3000);
    }

    @Test
    void removesSessionNotesUnlessFromLongTermExtraction() {
        Map<String, Object> patch = Map.of("notes", List.of("偏好轻薄"));

        Map<String, Object> filtered = memoryWriteFilter.filter(patch, "学习", false);

        assertThat(filtered).isEmpty();
    }

    @Test
    void keepsNotesFromLongTermExtraction() {
        Map<String, Object> patch = Map.of("notes", List.of("不喜欢入耳式"));

        Map<String, Object> filtered = memoryWriteFilter.filter(patch, "不要入耳式", true);

        assertThat(filtered).containsEntry("notes", List.of("不喜欢入耳式"));
    }
}
