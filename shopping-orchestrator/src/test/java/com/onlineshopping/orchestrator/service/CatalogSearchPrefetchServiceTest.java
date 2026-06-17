package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogSearchPrefetchServiceTest {

    private CatalogSearchClientService catalogSearchClientService;
    private CatalogSearchPrefetchService service;

    @BeforeEach
    void setUp() {
        catalogSearchClientService = mock(CatalogSearchClientService.class);
        service = new CatalogSearchPrefetchService(catalogSearchClientService);
        ReflectionTestUtils.setField(service, "searchLimit", 5);
    }

    @Test
    void prefetch_buildsSearchParamsFromResolvedConstraints() {
        Map<String, Object> effectiveContext = Map.of(
                "resolvedConstraints", Map.of(
                        "categoryId", "cat_phone",
                        "categoryRaw", "手机",
                        "searchHints", Map.of(
                                "brandKeyword", "小米",
                                "budget", Map.of("min", 2500, "max", 3500)
                        )
                )
        );
        when(catalogSearchClientService.search(anyMap())).thenReturn(
                PrefetchedSearchResult.ok(
                        "same_brand_other_price",
                        "ok",
                        List.of(Map.of("skuId", "SKU1001", "name", "小米 14", "price", 3999)),
                        Map.of()
                )
        );

        PrefetchedSearchResult result = service.prefetch(effectiveContext, "想买小米手机，预算3000");

        assertTrue(result.isUsable());
        assertEquals("same_brand_other_price", result.matchType());
        verify(catalogSearchClientService).search(org.mockito.ArgumentMatchers.argThat(params -> {
            Map<String, Object> map = new LinkedHashMap<>(params);
            String semanticQuery = String.valueOf(map.get("semanticQuery"));
            return "cat_phone".equals(map.get("categoryId"))
                    && "小米".equals(map.get("keyword"))
                    && Double.valueOf(2500d).equals(map.get("minPrice"))
                    && Double.valueOf(3500d).equals(map.get("maxPrice"))
                    && Integer.valueOf(5).equals(map.get("limit"))
                    && semanticQuery.contains("手机")
                    && semanticQuery.contains("品牌偏好：小米")
                    && semanticQuery.contains("预算：2500-3500元")
                    && semanticQuery.contains("想买小米手机，预算3000");
        }));
    }

    @Test
    void prefetch_rewritesSemanticQueryWithResolvedContextForFollowUp() {
        Map<String, Object> effectiveContext = Map.of(
                "resolvedConstraints", Map.of(
                        "categoryId", "cat_headphone",
                        "categoryName", "耳机",
                        "scene", "学习",
                        "mustHave", List.of("降噪", "佩戴舒适"),
                        "searchHints", Map.of(
                                "budget", Map.of("max", 1500)
                        )
                )
        );
        when(catalogSearchClientService.search(anyMap())).thenReturn(
                PrefetchedSearchResult.ok(
                        "exact",
                        "ok",
                        List.of(Map.of("skuId", "SKU2003", "name", "漫步者 NeoBuds Pro")),
                        Map.of()
                )
        );

        service.prefetch(effectiveContext, "那便宜点的呢");

        verify(catalogSearchClientService).search(org.mockito.ArgumentMatchers.argThat(params -> {
            Map<String, Object> map = new LinkedHashMap<>(params);
            String semanticQuery = String.valueOf(map.get("semanticQuery"));
            return "cat_headphone".equals(map.get("categoryId"))
                    && Double.valueOf(1500d).equals(map.get("maxPrice"))
                    && semanticQuery.contains("耳机")
                    && semanticQuery.contains("使用场景：学习")
                    && semanticQuery.contains("必须满足：降噪、佩戴舒适")
                    && semanticQuery.contains("预算：1500元以内")
                    && semanticQuery.contains("那便宜点的呢");
        }));
    }

    @Test
    void prefetch_returnsUnavailableWhenCategoryMissing() {
        PrefetchedSearchResult result = service.prefetch(
                Map.of("resolvedConstraints", Map.of("budget", Map.of("min", 2000, "max", 3000))),
                "预算3000"
        );

        assertEquals(PrefetchedSearchResult.STATUS_UNAVAILABLE, result.status());
    }
}
