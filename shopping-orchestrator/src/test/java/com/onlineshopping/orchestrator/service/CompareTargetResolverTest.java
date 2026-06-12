package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompareTargetResolverTest {

    @Test
    void resolvesOrdinalRefsFromLastRecommendations() {
        CatalogSearchClientService catalogSearchClientService = org.mockito.Mockito.mock(CatalogSearchClientService.class);
        CompareIntentDetector compareIntentDetector = new CompareIntentDetector();
        CompareTargetResolver resolver = new CompareTargetResolver(catalogSearchClientService, compareIntentDetector);

        Map<String, Object> sessionContext = Map.of(
                "lastRecommendations", List.of(
                        Map.of("rank", 1, "skuId", "SKU6006", "name", "华为 Vision 智慧屏 5"),
                        Map.of("rank", 2, "skuId", "SKU6004", "name", "海信 E8N Pro 85")
                )
        );
        Map<String, Object> patch = Map.of(
                "compareTargets", Map.of(
                        "productNames", List.of(),
                        "ordinalRefs", List.of(1, 2),
                        "skuIds", List.of()
                )
        );

        CompareTargetResolver.ResolvedCompareTargets targets = resolver.resolve(
                sessionContext,
                patch,
                "第一款和第二款哪个好"
        );

        assertEquals(List.of("SKU6006", "SKU6004"), targets.skuIds());
    }

    @Test
    void resolvesProductNamesFromMessageAgainstLastRecommendations() {
        CatalogSearchClientService catalogSearchClientService = org.mockito.Mockito.mock(CatalogSearchClientService.class);
        CompareIntentDetector compareIntentDetector = new CompareIntentDetector();
        CompareTargetResolver resolver = new CompareTargetResolver(catalogSearchClientService, compareIntentDetector);

        Map<String, Object> sessionContext = Map.of(
                "lastRecommendations", List.of(
                        Map.of("rank", 1, "skuId", "SKU6006", "name", "华为 Vision 智慧屏 5"),
                        Map.of("rank", 2, "skuId", "SKU6004", "name", "海信 E8N Pro 85")
                )
        );

        CompareTargetResolver.ResolvedCompareTargets targets = resolver.resolve(
                sessionContext,
                Map.of(),
                "华为 Vision 智慧屏 5和海信 E8N Pro 85哪个好"
        );

        assertEquals(List.of("SKU6006", "SKU6004"), targets.skuIds());
    }
}
