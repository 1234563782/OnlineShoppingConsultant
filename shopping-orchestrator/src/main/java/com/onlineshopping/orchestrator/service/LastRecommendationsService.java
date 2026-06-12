package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LastRecommendationsService {

    public void storeFromPrefetchedSearch(
            Map<String, Object> sessionContext,
            PrefetchedSearchResult prefetchedSearch
    ) {
        if (sessionContext == null || prefetchedSearch == null || !prefetchedSearch.isUsable()) {
            return;
        }
        List<Map<String, Object>> products = prefetchedSearch.products();
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Map<String, Object>> recommendations = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> product : products) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank++);
            copyIfPresent(item, product, "skuId");
            copyIfPresent(item, product, "name");
            copyIfPresent(item, product, "price");
            copyIfPresent(item, product, "brand");
            recommendations.add(item);
        }
        sessionContext.put(SessionContextKeys.LAST_RECOMMENDATIONS, recommendations);
    }

    private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }
}
