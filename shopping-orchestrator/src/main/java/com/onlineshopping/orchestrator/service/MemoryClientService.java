package com.onlineshopping.orchestrator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class MemoryClientService {

    private final RestTemplate restTemplate;

    @Value("${shopping.memory.base-url:http://localhost:8086}")
    private String memoryBaseUrl;

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
}
