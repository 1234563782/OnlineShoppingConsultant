package com.onlineshopping.catalog.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls DashScope text-embedding HTTP API (same key as chat).
 */
@Component
public class DashScopeEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingClient.class);
    private static final String ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DashScopeEmbeddingClient(
            @Value("${SPRING_AI_DASHSCOPE_API_KEY:}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    /**
     * @return embedding vector, or null on failure
     */
    public float[] embed(String model, String text, int expectedDimensions) {
        if (apiKey.isEmpty() || text == null || text.isBlank()) {
            return null;
        }
        try {
            String body = objectMapper.createObjectNode()
                    .put("model", model)
                    .set("input", objectMapper.createObjectNode()
                            .set("texts", objectMapper.createArrayNode().add(text)))
                    .toString();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                log.warn("DashScope embedding HTTP {}: {}", response.statusCode(), truncate(response.body(), 500));
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray() || embeddings.isEmpty()) {
                log.warn("DashScope embedding missing output.embeddings: {}", truncate(response.body(), 500));
                return null;
            }
            JsonNode vec = embeddings.get(0).path("embedding");
            if (!vec.isArray()) {
                return null;
            }
            List<Float> floats = new ArrayList<>();
            for (JsonNode n : vec) {
                floats.add((float) n.asDouble());
            }
            float[] arr = new float[floats.size()];
            for (int i = 0; i < floats.size(); i++) {
                arr[i] = floats.get(i);
            }
            if (arr.length != expectedDimensions) {
                log.warn("DashScope embedding dimension mismatch: expected {}, got {} — rejecting vector", expectedDimensions, arr.length);
                return null;
            }
            return arr;
        } catch (Exception e) {
            log.warn("DashScope embedding failed: {}", e.getMessage());
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
