package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedCompareResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ComparePrefetchService {

    private final CatalogCompareClientService catalogCompareClientService;
    private final CompareTargetResolver compareTargetResolver;

    public ComparePrefetchService(
            CatalogCompareClientService catalogCompareClientService,
            CompareTargetResolver compareTargetResolver
    ) {
        this.catalogCompareClientService = catalogCompareClientService;
        this.compareTargetResolver = compareTargetResolver;
    }

    public PrefetchedCompareResult prefetch(
            Map<String, Object> sessionContext,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        CompareTargetResolver.ResolvedCompareTargets targets = compareTargetResolver.resolve(
                sessionContext,
                extractedPatch,
                userMessage
        );
        if (targets.skuIds().size() < 2) {
            return PrefetchedCompareResult.insufficient("至少需要 2 个有效商品才能对比，请说明具体型号或第几款。");
        }
        return catalogCompareClientService.compare(targets.skuIds(), targets.focusDimensions());
    }

    public boolean isCompareIntent(Map<String, Object> sessionContext) {
        return SessionContextKeys.SUB_INTENT_COMPARE.equalsIgnoreCase(shoppingSubIntent(sessionContext));
    }

    private String shoppingSubIntent(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return SessionContextKeys.SUB_INTENT_DISCOVER;
        }
        Object value = sessionContext.get(SessionContextKeys.SHOPPING_SUB_INTENT);
        if (value == null || value.toString().isBlank()) {
            return SessionContextKeys.SUB_INTENT_DISCOVER;
        }
        return value.toString().trim();
    }
}
