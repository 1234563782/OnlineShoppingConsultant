package com.onlineshopping.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompareIntentDetectorTest {

    private final CompareIntentDetector detector = new CompareIntentDetector();

    @Test
    void setsCompareSubIntentWhenMessageContainsCompareKeyword() {
        Map<String, Object> patch = new HashMap<>();
        patch.put("intentType", "shopping");
        patch.put("shoppingSubIntent", "discover");

        detector.reconcileComparePatch("小米14和iPhone15哪个好", patch, Map.of());

        assertEquals("compare", patch.get("shoppingSubIntent"));
    }

    @Test
    void extractsOrdinalRefsFromChineseMessage() {
        List<Integer> ordinals = detector.extractOrdinalRefsFromMessage("第一款和第二款哪个好");

        assertEquals(List.of(1, 2), ordinals);
    }

    @Test
    void extractsProductNamesFromPairMessage() {
        List<String> names = detector.extractProductNamesFromMessage("华为 Vision 智慧屏 5和海信 E8N Pro 85哪个好");

        assertEquals(2, names.size());
        assertTrue(names.get(0).contains("华为"));
        assertTrue(names.get(1).contains("海信"));
    }

    @Test
    void vagueCompareUsesLastRecommendations() {
        Map<String, Object> session = Map.of(
                "lastRecommendations", List.of(
                        Map.of("rank", 1, "skuId", "SKU6006", "name", "华为 Vision 智慧屏 5"),
                        Map.of("rank", 2, "skuId", "SKU6004", "name", "海信 E8N Pro 85")
                )
        );
        Map<String, Object> patch = new HashMap<>();
        patch.put("intentType", "shopping");

        detector.reconcileComparePatch("对比一下", patch, session);

        assertEquals("compare", patch.get("shoppingSubIntent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> targets = (Map<String, Object>) patch.get("compareTargets");
        @SuppressWarnings("unchecked")
        List<Integer> ordinals = (List<Integer>) targets.get("ordinalRefs");
        assertEquals(List.of(1, 2), ordinals);
    }
}
