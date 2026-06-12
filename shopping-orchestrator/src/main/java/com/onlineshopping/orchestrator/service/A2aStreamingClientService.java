package com.onlineshopping.orchestrator.service;

import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class A2aStreamingClientService {

    private static final Logger log = LoggerFactory.getLogger(A2aStreamingClientService.class);

    private final AgentCardProvider agentCardProvider;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int streamTimeoutSeconds;

    public A2aStreamingClientService(
            AgentCardProvider agentCardProvider,
            ObjectMapper objectMapper,
            @Value("${shopping.agents.a2a.connect-timeout-seconds:30}") int connectTimeoutSeconds,
            @Value("${shopping.agents.a2a.stream-timeout-seconds:120}") int streamTimeoutSeconds
    ) {
        this.agentCardProvider = agentCardProvider;
        this.objectMapper = objectMapper;
        this.streamTimeoutSeconds = streamTimeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    public Flux<String> streamMessage(String agentName, String messageText, String threadId, String userId) {
        return Flux.<String>create(sink -> {
            try {
                AgentCardWrapper agentCard = resolveAgentCard(agentName);
                String baseUrl = agentCard.url();
                if (!StringUtils.hasText(baseUrl)) {
                    sink.error(new IllegalStateException("AgentCard.url is empty for agent: " + agentName));
                    return;
                }

                String payload = buildStreamingPayload(messageText, threadId, userId);
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );
                if (response.statusCode() != 200) {
                    sink.error(new IllegalStateException(
                            "A2A stream failed for agent " + agentName + ", status: " + response.statusCode()
                    ));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8)
                )) {
                    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(streamTimeoutSeconds);
                    String line;
                    while ((line = reader.readLine()) != null && !sink.isCancelled()) {
                        if (System.nanoTime() > deadlineNanos) {
                            sink.error(new IllegalStateException(
                                    "A2A stream timeout after " + streamTimeoutSeconds + "s for agent " + agentName
                            ));
                            return;
                        }
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }
                        String jsonContent = trimmed.substring(5).trim();
                        if ("[DONE]".equals(jsonContent)) {
                            break;
                        }
                        extractTextChunk(jsonContent).ifPresent(sink::next);
                    }
                    sink.complete();
                }
            } catch (Exception e) {
                if (!sink.isCancelled()) {
                    sink.error(e);
                }
            }
        }, reactor.core.publisher.FluxSink.OverflowStrategy.BUFFER).subscribeOn(Schedulers.boundedElastic());
    }

    private AgentCardWrapper resolveAgentCard(String agentName) {
        if (agentCardProvider.supportGetAgentCardByName()) {
            return agentCardProvider.getAgentCard(agentName);
        }
        AgentCardWrapper card = agentCardProvider.getAgentCard();
        if (card == null) {
            throw new IllegalStateException("Agent card not found for agent: " + agentName);
        }
        return card;
    }

    private String buildStreamingPayload(String messageText, String threadId, String userId) throws Exception {
        String id = UUID.randomUUID().toString();
        String messageId = UUID.randomUUID().toString().replace("-", "");

        Map<String, Object> part = Map.of("kind", "text", "text", messageText);
        Map<String, Object> message = new HashMap<>();
        message.put("kind", "message");
        message.put("messageId", messageId);
        message.put("parts", List.of(part));
        message.put("role", "user");

        Map<String, Object> params = new HashMap<>();
        params.put("message", message);

        Map<String, Object> metadata = new HashMap<>();
        if (StringUtils.hasText(threadId)) {
            metadata.put("threadId", threadId);
        }
        if (StringUtils.hasText(userId)) {
            metadata.put("userId", userId);
        }
        params.put("metadata", metadata);

        Map<String, Object> root = new HashMap<>();
        root.put("id", id);
        root.put("jsonrpc", "2.0");
        root.put("method", "message/stream");
        root.put("params", params);
        return objectMapper.writeValueAsString(root);
    }

    private java.util.Optional<String> extractTextChunk(String jsonContent) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    jsonContent,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
            Object resultObject = parsed.get("result");
            if (!(resultObject instanceof Map<?, ?> result)) {
                return java.util.Optional.empty();
            }
            String text = extractResponseText(result);
            if (!StringUtils.hasText(text)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(text);
        } catch (Exception e) {
            log.debug("Skip unparsable A2A SSE chunk: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<?, ?> result) {
        if ("artifact-update".equals(result.get("kind"))) {
            return textFromParts((Map<String, Object>) result.get("artifact"));
        }
        if (result.containsKey("artifacts")) {
            Object artifactsObject = result.get("artifacts");
            if (artifactsObject instanceof List<?> artifacts) {
                StringBuilder builder = new StringBuilder();
                for (Object artifact : artifacts) {
                    if (artifact instanceof Map<?, ?> artifactMap) {
                        builder.append(textFromParts((Map<String, Object>) artifactMap));
                    }
                }
                if (!builder.isEmpty()) {
                    return builder.toString();
                }
            }
        }
        if (result.containsKey("parts")) {
            return textFromParts((Map<String, Object>) result);
        }
        if (result.containsKey("message")) {
            return textFromParts((Map<String, Object>) result.get("message"));
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String textFromParts(Map<String, Object> container) {
        if (container == null || !container.containsKey("parts")) {
            return "";
        }
        Object partsObject = container.get("parts");
        if (!(partsObject instanceof List<?> parts) || parts.isEmpty()) {
            return "";
        }
        Object lastPart = parts.get(parts.size() - 1);
        if (lastPart instanceof Map<?, ?> partMap) {
            Object text = partMap.get("text");
            return text == null ? "" : text.toString();
        }
        return "";
    }

}
