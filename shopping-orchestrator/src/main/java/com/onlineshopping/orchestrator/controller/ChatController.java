package com.onlineshopping.orchestrator.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.ChatResponse;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.service.MemoryClientService;
import com.onlineshopping.orchestrator.service.SessionStoreService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final LlmRoutingAgent supervisorAgent;
    private final SessionStoreService sessionStoreService;
    private final MemoryClientService memoryClientService;

    public ChatController(
            @Qualifier("supervisorAgentBean") LlmRoutingAgent supervisorAgent,
            SessionStoreService sessionStoreService,
            MemoryClientService memoryClientService
    ) {
        this.supervisorAgent = supervisorAgent;
        this.sessionStoreService = sessionStoreService;
        this.memoryClientService = memoryClientService;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) throws Exception {
        String sessionId = (request.getSessionId() == null || request.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        SessionState sessionState = sessionStoreService.getSession(sessionId, request.getUserId());
        Map<String, Object> profile = memoryClientService.getProfile(request.getUserId());
        String userInput = buildUserInput(request.getMessage(), request.getUserId(), profile, sessionState.getTurns());

        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(sessionId)
                .addMetadata("user_id", request.getUserId())
                .build();
        Map<String, Object> input = Map.of(
                "input", userInput,
                "chat_id", sessionId,
                "user_id", request.getUserId()
        );

        CompiledGraph compiledGraph = supervisorAgent.getAndCompileGraph();
        Flux<NodeOutput> stream = compiledGraph.fluxStream(input, runnableConfig);
        List<NodeOutput> outputs = stream.collectList().block();

        String reply = extractReply(outputs);
        if (reply.isBlank()) {
            reply = "我已收到你的需求。可以先告诉我预算和使用场景吗？";
        }

        sessionStoreService.appendTurns(sessionId, sessionState, request.getMessage(), reply);
        Map<String, Object> memoryPatch = extractMemoryPatch(reply);
        memoryClientService.mergePatch(request.getUserId(), memoryPatch);

        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setReply(reply);
        response.setDebug(Map.of(
                "toolMode", "a2a+nacos",
                "memoryProfile", profile
        ));
        return response;
    }

    private String buildUserInput(String message, String userId, Map<String, Object> profile, List<SessionState.Turn> turns) {
        return "userMessage: " + message
                + "\nuserId: " + userId
                + "\nmemoryProfile: " + profile
                + "\nrecentTurns: " + turns;
    }

    private String extractReply(List<NodeOutput> outputs) {
        if (outputs == null || outputs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (NodeOutput output : outputs) {
            if ("a2aNode".equals(output.node()) && output instanceof StreamingOutput streamingOutput) {
                String chunk = streamingOutput.chunk();
                if (chunk != null && !chunk.isBlank() && !"Agent State: submitted".equals(chunk)) {
                    builder.append(chunk);
                }
            }
        }
        return builder.toString();
    }

    private Map<String, Object> extractMemoryPatch(String reply) {
        Map<String, Object> patch = new HashMap<>();
        if (reply.contains("预算") && reply.contains("3000")) {
            patch.put("budgetMin", 3000);
            patch.put("budgetMax", 5000);
        }
        return patch;
    }
}
