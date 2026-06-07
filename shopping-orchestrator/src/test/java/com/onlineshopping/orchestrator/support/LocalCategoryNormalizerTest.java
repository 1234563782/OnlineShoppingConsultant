package com.onlineshopping.orchestrator.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCategoryNormalizerTest {

    @Test
    void normalizeHeadphoneByName() {
        Map<String, Object> result = LocalCategoryNormalizer.normalize("耳机");
        assertEquals("RESOLVED", result.get("status"));
        assertEquals("cat_headphone", result.get("categoryId"));
    }

    @Test
    void normalizeHeadphoneByAlias() {
        Map<String, Object> result = LocalCategoryNormalizer.normalize("入耳式耳机");
        assertEquals("RESOLVED", result.get("status"));
        assertEquals("cat_headphone", result.get("categoryId"));
    }

    @Test
    void normalizeUnknown() {
        Map<String, Object> result = LocalCategoryNormalizer.normalize("微波炉");
        assertEquals("UNRESOLVED", result.get("status"));
        assertTrue(result.get("categoryId") == null || !result.containsKey("categoryId"));
    }
}
