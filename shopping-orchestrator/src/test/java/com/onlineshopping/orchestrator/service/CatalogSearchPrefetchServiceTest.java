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
                        "预算内没有该品牌，改推同品牌其他价位。",
                        List.of(Map.of("skuId", "SKU1001", "name", "小米 14", "price", 3999)),
                        Map.of()
                )
        );

        PrefetchedSearchResult result = service.prefetch(effectiveContext, "想买小米手机，预算3000");

        assertTrue(result.isUsable());
        assertEquals("same_brand_other_price", result.matchType());
        verify(catalogSearchClientService).search(org.mockito.ArgumentMatchers.argThat(params -> {
            Map<String, Object> map = new LinkedHashMap<>(params);
            return "cat_phone".equals(map.get("categoryId"))
                    && "小米".equals(map.get("keyword"))
                    && Double.valueOf(2500d).equals(map.get("minPrice"))
                    && Double.valueOf(3500d).equals(map.get("maxPrice"))
                    && Integer.valueOf(5).equals(map.get("limit"))
                    && "想买小米手机，预算3000".equals(map.get("semanticQuery"));
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
