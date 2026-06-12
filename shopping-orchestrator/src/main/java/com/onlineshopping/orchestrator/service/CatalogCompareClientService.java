package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.PrefetchedCompareResult;
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
public class CatalogCompareClientService {

    private final RestTemplate restTemplate;

    @Value("${shopping.catalog.base-url:http://localhost:8083}")
    private String catalogBaseUrl;

    public CatalogCompareClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public PrefetchedCompareResult compare(List<String> skuIds, List<String> focusDimensions) {
        if (skuIds == null || skuIds.size() < 2) {
            return PrefetchedCompareResult.insufficient("至少需要 2 个有效商品才能对比");
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("skuIds", skuIds);
        if (focusDimensions != null && !focusDimensions.isEmpty()) {
            requestBody.put("focusDimensions", focusDimensions);
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    catalogBaseUrl + "/api/v1/products/compare",
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody),
                    new ParameterizedTypeReference<>() {
                    }
            );
            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty()) {
                return PrefetchedCompareResult.unavailable("catalog compare returned empty");
            }
            return mapResponse(body);
        } catch (Exception e) {
            return PrefetchedCompareResult.unavailable(
                    e.getMessage() == null ? "catalog compare unavailable" : e.getMessage()
            );
        }
    }

    @SuppressWarnings("unchecked")
    private PrefetchedCompareResult mapResponse(Map<String, Object> body) {
        String status = stringValue(body.get("status"));
        List<String> skuIds = extractStringList(body.get("skuIds"));
        List<Map<String, Object>> products = extractProducts(body.get("products"));
        List<String> compareDimensions = extractStringList(body.get("compareDimensions"));
        boolean crossCategory = Boolean.TRUE.equals(body.get("crossCategory"));
        String message = stringValue(body.get("message"));
        if (PrefetchedCompareResult.STATUS_OK.equals(status)) {
            return PrefetchedCompareResult.fromResponse(
                    status,
                    skuIds,
                    products,
                    compareDimensions,
                    crossCategory,
                    message
            );
        }
        if (PrefetchedCompareResult.STATUS_INSUFFICIENT_TARGETS.equals(status)) {
            return PrefetchedCompareResult.insufficient(
                    message == null ? "对比目标不足" : message
            );
        }
        return PrefetchedCompareResult.unavailable(message == null ? status : message);
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

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
