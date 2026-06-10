package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogSearchClientService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSearchClientService.class);

    private final RestTemplate restTemplate;

    @Value("${shopping.catalog.base-url:http://localhost:8083}")
    private String catalogBaseUrl;

    public CatalogSearchClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public PrefetchedSearchResult search(Map<String, Object> searchParams) {
        if (searchParams == null || searchParams.isEmpty()) {
            return PrefetchedSearchResult.unavailable("search params empty");
        }
        Map<String, Object> requestBody = new LinkedHashMap<>(searchParams);
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    catalogBaseUrl + "/api/v1/products/search",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.warn("Catalog search returned empty body, params={}", searchParams);
                return PrefetchedSearchResult.unavailable("catalog search returned empty");
            }
            String matchType = stringValue(body.get("matchType"));
            String message = stringValue(body.get("message"));
            List<Map<String, Object>> products = extractProducts(body.get("products"));
            return PrefetchedSearchResult.ok(matchType, message, products, searchParams);
        } catch (Exception e) {
            log.warn("Catalog search failed, params={}: {}", searchParams, e.getMessage());
            return PrefetchedSearchResult.unavailable(
                    e.getMessage() == null ? "catalog unavailable" : e.getMessage()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractProducts(Object rawProducts) {
        if (!(rawProducts instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
