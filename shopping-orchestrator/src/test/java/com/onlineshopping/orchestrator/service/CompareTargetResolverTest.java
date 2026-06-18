package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

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

    @Test
    void resolvesProductNamesGloballyWhenSessionCategoryIsWrong() {
        CatalogSearchClientService catalogSearchClientService = org.mockito.Mockito.mock(CatalogSearchClientService.class);
        CompareIntentDetector compareIntentDetector = new CompareIntentDetector();
        CompareTargetResolver resolver = new CompareTargetResolver(catalogSearchClientService, compareIntentDetector);

        when(catalogSearchClientService.search(argThat(params ->
                "AlphaPad Ultra".equals(params.get("keyword")) && !params.containsKey("categoryId")
        ))).thenReturn(PrefetchedSearchResult.ok(
                "exact",
                "",
                List.of(product("SKU3008", "AlphaPad Ultra Laptop", "cat_computer", "电脑")),
                Map.of()
        ));
        when(catalogSearchClientService.search(argThat(params ->
                "BetaBook Air".equals(params.get("keyword")) && !params.containsKey("categoryId")
        ))).thenReturn(PrefetchedSearchResult.ok(
                "exact",
                "",
                List.of(product("SKU3009", "BetaBook Air Laptop", "cat_computer", "电脑")),
                Map.of()
        ));

        Map<String, Object> sessionContext = new LinkedHashMap<>();
        sessionContext.put("categoryId", "cat_tablet");
        sessionContext.put("categoryName", "平板");
        sessionContext.put("categoryRaw", "平板");

        Map<String, Object> patch = Map.of(
                "compareTargets", Map.of(
                        "productNames", List.of("AlphaPad Ultra", "BetaBook Air"),
                        "ordinalRefs", List.of(),
                        "skuIds", List.of()
                )
        );

        CompareTargetResolver.ResolvedCompareTargets targets = resolver.resolve(
                sessionContext,
                patch,
                "AlphaPad Ultra 和 BetaBook Air 比一下。"
        );

        assertEquals(List.of("SKU3008", "SKU3009"), targets.skuIds());
        assertEquals("cat_computer", sessionContext.get("categoryId"));
        assertEquals("电脑", sessionContext.get("categoryName"));
        assertEquals(
                SessionContextKeys.CATEGORY_SOURCE_COMPARE_PRODUCT,
                sessionContext.get(SessionContextKeys.CATEGORY_SOURCE)
        );
    }

    private Map<String, Object> product(String skuId, String name, String categoryId, String categoryName) {
        return Map.of(
                "skuId", skuId,
                "name", name,
                "categoryId", categoryId,
                "categoryName", categoryName
        );
    }
}
