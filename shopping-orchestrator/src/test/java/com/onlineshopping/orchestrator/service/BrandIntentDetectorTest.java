package com.onlineshopping.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrandIntentDetectorTest {

    private final BrandIntentDetector detector = new BrandIntentDetector();

    @Test
    void detectsXiaomiFromUserMessage() {
        Map<String, Object> patch = new HashMap<>();
        detector.reconcileBrandPatch("我想买小米手机，预算3000", patch);

        assertThat(patch.get("brandPreferences")).isEqualTo(List.of("小米"));
    }

    @Test
    void doesNotDetectBrandFromSceneOnlyMessage() {
        Map<String, Object> patch = new HashMap<>();
        detector.reconcileBrandPatch("学习", patch);

        assertThat(patch).doesNotContainKey("brandPreferences");
    }
}
