package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.MemoryRecallResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryClientService {

    private final RestTemplate restTemplate;

    @Value("${shopping.memory.base-url:http://localhost:8086}")
    private String memoryBaseUrl;

    @Value("${shopping.memory.recall-top-k:5}")
    private int recallTopK;

    public MemoryClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProfile(String userId) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    memoryBaseUrl + "/api/v1/memory/{userId}",
                    Map.class,
                    userId
            );
            if (response == null || !(response.get("profileJson") instanceof Map<?, ?> profileJson)) {
                return new HashMap<>();
            }
            return (Map<String, Object>) profileJson;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    public MemoryRecallResult recall(String userId, String query, List<String> excludeKeys) {
        List<String> safeExclude = excludeKeys == null ? List.of() : new ArrayList<>(excludeKeys);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query == null ? "" : query);
            body.put("topK", recallTopK);
            body.put("excludeKeys", safeExclude);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    memoryBaseUrl + "/api/v1/memory/{userId}/recall",
                    body,
                    Map.class,
                    userId
            );
            if (response == null) {
                return fallbackRecall(userId, safeExclude);
            }
            Map<String, Object> segments = Map.of();
            if (response.get("profileSegments") instanceof Map<?, ?> rawSegments) {
                segments = (Map<String, Object>) rawSegments;
            }
            List<String> recalledKeys = normalizeStringList(response.get("recalledKeys"));
            return new MemoryRecallResult(segments, recalledKeys, safeExclude);
        } catch (Exception e) {
            return fallbackRecall(userId, safeExclude);
        }
    }

    public Map<String, Object> recall(String userId, String query) {
        return recall(userId, query, List.of()).profileSegments();
    }

    public void mergePatch(String userId, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("profileJson", patch);
        try {
            restTemplate.put(memoryBaseUrl + "/api/v1/memory/{userId}", new HttpEntity<>(body, headers), userId);
        } catch (Exception ignored) {
        }
    }

    private MemoryRecallResult fallbackRecall(String userId, List<String> excludeKeys) {
        return new MemoryRecallResult(getProfile(userId), List.of(), excludeKeys);
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
    }
}
