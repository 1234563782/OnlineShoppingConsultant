package com.onlineshopping.orchestrator.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrandSearchKeywordResolverTest {

    private final BrandSearchKeywordResolver resolver = new BrandSearchKeywordResolver();

    @Test
    void resolvesFromBrandPreferences() {
        Map<String, Object> constraints = Map.of("brandPreferences", List.of("小米"));

        assertThat(resolver.resolve(constraints)).contains("小米");
    }

    @Test
    void resolvesFromMustHaveWhenBrandPreferencesMissing() {
        Map<String, Object> constraints = Map.of("mustHave", List.of("小米手机"));

        assertThat(resolver.resolve(constraints)).contains("小米");
    }
}
