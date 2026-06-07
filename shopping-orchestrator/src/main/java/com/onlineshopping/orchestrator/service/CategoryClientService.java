package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.LocalCategoryNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class CategoryClientService {

    private static final Logger log = LoggerFactory.getLogger(CategoryClientService.class);

    private final RestTemplate restTemplate;

    @Value("${shopping.catalog.base-url:http://localhost:8083}")
    private String catalogBaseUrl;

    @Value("${shopping.catalog.fallback-enabled:true}")
    private boolean fallbackEnabled;

    public CategoryClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> normalize(String categoryRaw) {
        if (categoryRaw == null || categoryRaw.isBlank()) {
            return Map.of(
                    "status", CategoryResolutionResult.STATUS_UNRESOLVED,
                    "categoryRaw", "",
                    "confidence", 0.0
            );
        }
        String trimmed = categoryRaw.trim();
        try {
            var uri = UriComponentsBuilder
                    .fromHttpUrl(catalogBaseUrl + "/api/v1/categories/normalize")
                    .queryParam("raw", trimmed)
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();
            Map<String, Object> response = restTemplate.getForObject(uri, Map.class);
            if (response != null && !response.isEmpty()) {
                return response;
            }
            log.warn("Catalog normalize returned empty for raw={}", trimmed);
        } catch (Exception e) {
            log.warn("Catalog normalize failed for raw={}: {}", trimmed, e.getMessage());
            if (fallbackEnabled) {
                Map<String, Object> fallback = new HashMap<>(LocalCategoryNormalizer.normalize(trimmed));
                fallback.put("catalogError", e.getMessage() == null ? "catalog unavailable" : e.getMessage());
                return fallback;
            }
            return Map.of(
                    "status", CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE,
                    "categoryRaw", trimmed,
                    "confidence", 0.0,
                    "error", e.getMessage() == null ? "catalog unavailable" : e.getMessage()
            );
        }
        if (fallbackEnabled) {
            return LocalCategoryNormalizer.normalize(trimmed);
        }
        return Map.of("status", CategoryResolutionResult.STATUS_UNRESOLVED, "categoryRaw", trimmed, "confidence", 0.0);
    }
}
