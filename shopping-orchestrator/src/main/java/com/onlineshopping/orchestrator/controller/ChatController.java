package com.onlineshopping.orchestrator.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.auth.AuthSupport;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import com.onlineshopping.orchestrator.service.LongTermMemoryWriteService;
import com.onlineshopping.orchestrator.service.SessionStoreService;
import com.onlineshopping.orchestrator.service.UserInputProcessor;
import com.onlineshopping.orchestrator.support.PlainTextStreamBuffer;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final LlmRoutingAgent supervisorAgent;
    private final SessionStoreService sessionStoreService;
    private final UserInputProcessor userInputProcessor;
    private final LongTermMemoryWriteService longTermMemoryWriteService;
    private final ObjectMapper objectMapper;

    public ChatController(
            @Qualifier("supervisorAgentBean") LlmRoutingAgent supervisorAgent,
            SessionStoreService sessionStoreService,
            UserInputProcessor userInputProcessor,
            LongTermMemoryWriteService longTermMemoryWriteService,
            ObjectMapper objectMapper
    ) {
        this.supervisorAgent = supervisorAgent;
        this.sessionStoreService = sessionStoreService;
        this.userInputProcessor = userInputProcessor;
        this.longTermMemoryWriteService = longTermMemoryWriteService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
        final String userId = AuthSupport.requireUserId();
        return Flux.defer(() -> {
            try {
                ChatPreparedContext prepared = userInputProcessor.process(
                        userId,
                        request.getSessionId(),
                        request.getMessage()
                );
                return dispatch(prepared, request);
            } catch (Exception e) {
                return Flux.just(errorEvent(e.getMessage() == null ? "stream failed" : e.getMessage()));
            }
        });
    }

    private Flux<ServerSentEvent<String>> dispatch(ChatPreparedContext prepared, ChatRequest request) {
        TurnDecision decision = prepared.turnDecision();
        return switch (decision.outcome()) {
            case SMALL_TALK, NON_SHOPPING -> streamDirectReply(
                    prepared,
                    request,
                    decision.directReply(),
                    "orchestrator_direct_reply",
                    decision.outcome().name().toLowerCase()
            );
            case NEED_CLARIFICATION -> streamClarification(prepared, request, decision);
            case READY_FOR_AGENT -> streamAgentReply(prepared, request);
        };
    }

    private Flux<ServerSentEvent<String>> streamDirectReply(
            ChatPreparedContext prepared,
            ChatRequest request,
            String reply,
            String toolMode,
            String reason
    ) {
        sessionStoreService.appendTurns(
                prepared.userId(),
                prepared.sessionId(),
                prepared.sessionState(),
                request.getMessage(),
                reply
        );
        Map<String, Object> debug = baseTurnDebug(prepared, toolMode);
        debug.put("reason", reason);
        return streamStaticReply(prepared.sessionId(), reply, debug);
    }

    private Flux<ServerSentEvent<String>> streamClarification(
            ChatPreparedContext prepared,
            ChatRequest request,
            TurnDecision decision
    ) {
        String clarification = decision.clarification();
        sessionStoreService.appendTurns(
                prepared.userId(),
                prepared.sessionId(),
                prepared.sessionState(),
                request.getMessage(),
                clarification
        );
        LongTermMemoryWriteService.WriteResult memoryWrite = longTermMemoryWriteService.write(
                prepared.userId(),
                prepared.extractedPatch(),
                prepared.effectiveContext(),
                prepared.profile(),
                request.getMessage()
        );
        Map<String, Object> debug = buildMemoryDebugMap(
                "orchestrator_clarify",
                baseTurnDebug(prepared, "orchestrator_clarify"),
                memoryWrite
        );
        return streamStaticReply(prepared.sessionId(), clarification, debug);
    }

    private Flux<ServerSentEvent<String>> streamStaticReply(
            String sessionId,
            String reply,
            Map<String, Object> debug
    ) {
        return Flux.concat(
                Flux.just(sessionEvent(sessionId)),
                Flux.just(deltaEvent(reply)),
                Flux.just(doneEvent(sessionId, reply, debug))
        );
    }

    private Flux<ServerSentEvent<String>> streamAgentReply(ChatPreparedContext prepared, ChatRequest request) {
        String userInput = buildUserInput(
                request.getMessage(),
                prepared.userId(),
                resolvedConstraints(prepared.effectiveContext()),
                prepared.turnDecision().categoryReplaced()
        );
        String agentThreadId = agentThreadId(prepared);
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(agentThreadId)
                .addMetadata("user_id", prepared.userId())
                .build();
        Map<String, Object> input = Map.of(
                "input", userInput,
                "chat_id", agentThreadId,
                "user_id", prepared.userId()
        );

        final CompiledGraph compiledGraph;
        try {
            compiledGraph = supervisorAgent.getAndCompileGraph();
        } catch (Exception e) {
            return Flux.just(errorEvent(e.getMessage() == null ? "stream failed" : e.getMessage()));
        }
        PlainTextStreamBuffer replyBuffer = new PlainTextStreamBuffer();

        Flux<ServerSentEvent<String>> tokenFlux = compiledGraph.fluxStream(input, runnableConfig)
                .concatMap(output -> {
                    if (!"a2aNode".equals(output.node()) || !(output instanceof StreamingOutput streamingOutput)) {
                        return Flux.empty();
                    }
                    String chunk = streamingOutput.chunk();
                    if (chunk == null || chunk.isBlank() || "Agent State: submitted".equals(chunk)) {
                        return Flux.empty();
                    }
                    String delta = replyBuffer.append(chunk);
                    if (delta.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(deltaEvent(delta));
                });

        return Flux.concat(
                Flux.just(sessionEvent(prepared.sessionId())),
                tokenFlux,
                finalizeAgentStream(prepared, request, replyBuffer)
        ).onErrorResume(error -> Flux.just(errorEvent(
                error.getMessage() == null ? "stream failed" : error.getMessage()
        )));
    }

    private Flux<ServerSentEvent<String>> finalizeAgentStream(
            ChatPreparedContext prepared,
            ChatRequest request,
            PlainTextStreamBuffer replyBuffer
    ) {
        return Flux.defer(() -> {
            String buffered = replyBuffer.content();
            String reply = normalizeAgentReply(buffered, prepared.effectiveContext());
            try {
                sessionStoreService.appendTurns(
                        prepared.userId(),
                        prepared.sessionId(),
                        prepared.sessionState(),
                        request.getMessage(),
                        reply
                );
                LongTermMemoryWriteService.WriteResult memoryWrite = longTermMemoryWriteService.write(
                        prepared.userId(),
                        prepared.extractedPatch(),
                        prepared.effectiveContext(),
                        prepared.profile(),
                        request.getMessage()
                );
                Map<String, Object> debug = buildMemoryDebugMap(
                        "a2a+nacos",
                        baseTurnDebug(prepared, "a2a+nacos"),
                        memoryWrite
                );
                debug.put("memoryProfile", prepared.profile());
                return Flux.just(doneEvent(prepared.sessionId(), reply, debug));
            } catch (Exception e) {
                log.error(
                        "Chat finalize failed after stream (sessionId={}); client already received deltas.",
                        prepared.sessionId(),
                        e
                );
                Map<String, Object> safeDebug = new HashMap<>();
                safeDebug.put("streamFinalizeError", e.getClass().getSimpleName());
                safeDebug.put(
                        "streamFinalizeMessage",
                        e.getMessage() == null ? "" : e.getMessage()
                );
                return Flux.just(doneEvent(prepared.sessionId(), reply, safeDebug));
            }
        });
    }

    private Map<String, Object> baseTurnDebug(ChatPreparedContext prepared, String toolMode) {
        Map<String, Object> debug = new HashMap<>();
        TurnDecision decision = prepared.turnDecision();
        debug.put("toolMode", toolMode);
        debug.put("turnOutcome", decision.outcome().name());
        debug.put("categoryReplaced", decision.categoryReplaced());
        debug.put("categoryReplaceReason", decision.categoryReplaceReason());
        debug.put("clarificationField", decision.clarificationField());
        debug.put("sessionContext", prepared.sessionContext());
        debug.put("effectiveContext", prepared.effectiveContext());
        debug.put("categoryResolution", prepared.categoryResolution().toDebugMap());
        debug.put("stateDebug", prepared.stateDebug());
        return debug;
    }

    private String normalizeAgentReply(String rawReply, Map<String, Object> effectiveContext) {
        if (rawReply == null || rawReply.isBlank() || isInvalidReply(rawReply)) {
            return buildFallbackShoppingReply(effectiveContext);
        }
        return rawReply.trim();
    }

    private ServerSentEvent<String> sessionEvent(String sessionId) {
        return streamEvent("session", Map.of("sessionId", sessionId));
    }

    private ServerSentEvent<String> deltaEvent(String content) {
        return streamEvent("delta", Map.of("content", content));
    }

    private ServerSentEvent<String> doneEvent(String sessionId, String reply, Map<String, Object> debug) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("reply", reply);
        payload.put("debug", debug);
        return streamEvent("done", payload);
    }

    private ServerSentEvent<String> errorEvent(String message) {
        return streamEvent("error", Map.of("message", message));
    }

    private ServerSentEvent<String> streamEvent(String type, Map<String, Object> payload) {
        Map<String, Object> event = new HashMap<>(payload);
        event.put("type", type);
        try {
            return ServerSentEvent.<String>builder()
                    .data(objectMapper.writeValueAsString(event))
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .data("{\"type\":\"error\",\"message\":\"event serialization failed\"}")
                    .build();
        }
    }

    private Map<String, Object> buildMemoryDebugMap(
            String toolMode,
            Map<String, Object> extra,
            LongTermMemoryWriteService.WriteResult memoryWrite
    ) {
        Map<String, Object> debug = new HashMap<>(extra);
        debug.put("toolMode", toolMode);
        debug.put("memoryPatchFromExtraction", memoryWrite.extractionPatch());
        debug.put("memoryPatchFromSession", memoryWrite.sessionPatch());
        debug.put("memoryPatchMerged", memoryWrite.mergedPatch());
        debug.put("memoryPatchReconciled", memoryWrite.reconciledPatch());
        debug.put("profileWritten", memoryWrite.profileWritten());
        return debug;
    }

    private String agentThreadId(ChatPreparedContext prepared) {
        int turnCount = prepared.sessionState().getTurns() == null ? 0 : prepared.sessionState().getTurns().size();
        String threadId = prepared.sessionId() + ":turn:" + turnCount;
        if (prepared.turnDecision().categoryReplaced()) {
            Object categoryId = prepared.effectiveContext().get(SessionContextKeys.CATEGORY_ID);
            if (categoryId != null && !categoryId.toString().isBlank()) {
                threadId = threadId + ":cat:" + categoryId;
            }
        }
        return threadId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolvedConstraints(Map<String, Object> effectiveContext) {
        if (effectiveContext == null) {
            return Map.of();
        }
        Object resolved = effectiveContext.get("resolvedConstraints");
        if (resolved instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return effectiveContext;
    }

    private String buildUserInput(
            String message,
            String userId,
            Map<String, Object> resolvedConstraints,
            boolean categoryReplaced
    ) {
        String categorySwitchNotice = categoryReplaced
                ? "【本轮品类已切换】必须以 resolvedConstraints 中的最新 categoryId 重新调用 searchProduct，禁止沿用上一轮品类或旧搜索结果。\n"
                : "";
        return """
                请你作为导购咨询子Agent，基于结构化上下文回答。
                直接输出给用户看的自然语言回复，不要 JSON、不要 markdown、不要代码块。
                %s规则：
                1. resolvedConstraints 是主Agent已经整理好的本次咨询约束，以它为准执行；会话内表达优先于历史画像。
                2. 调用 searchProduct 时必须优先传 resolvedConstraints.categoryId；仅当 categoryId 为空时才传 categoryRaw。
                3. 若 resolvedConstraints.searchHints.brandKeyword 非空，必须原样传入 searchProduct 的 keyword 参数；预算用 searchHints.budget 的 min/max。
                4. 搜索兜底由工具按顺序执行：先同品牌同预算，再无预算同品牌，再无品牌同预算，最后同品类其他品牌；你只需解释工具返回的 matchType，不要自行换品牌或编造型号。
                5. 若 userMessage 明确表达与上一轮不同的品类，必须以 resolvedConstraints 中的最新 categoryId 重新调用 searchProduct，禁止沿用上一轮品类结果。
                6. 你是推荐执行者，不负责追问缺失字段；缺预算、缺场景或 userUncertain=true 时，也必须调用工具并给出具体推荐。
                7. 如果预算缺失或用户说“先看看”，按不同价位/常见档位推荐；如果场景缺失，按通用需求假设推荐并说明假设。
                8. 禁止用“请告诉我预算/用途/场景/方便告诉我吗”等追问替代推荐；推荐后可以附带一句可选补充建议。
                9. 只能推荐 searchProduct 返回 JSON 中 products 里的商品；名称、价格必须与工具字段完全一致，禁止编造未返回的型号或改写价格（禁止“约/大概”）。
                10. 若 products 不足 3 款，只推荐实际返回的数量，禁止凑数编造。
                11. matchType 为 same_brand_other_price 时，说明预算内没有该品牌，改推同品牌其他价位；为 same_category_other_brand_* 时，说明该品牌无货，改推其他品牌，并如实告知。
                                
                userId: %s
                userMessage: %s
                resolvedConstraints: %s
                """.formatted(categorySwitchNotice, userId, message, resolvedConstraints);
    }

    private boolean isInvalidReply(String reply) {
        if (reply == null) {
            return true;
        }
        String normalized = reply.trim().toLowerCase();
        return normalized.isBlank()
                || "undefined".equals(normalized)
                || "null".equals(normalized)
                || "{}".equals(normalized)
                || "[]".equals(normalized);
    }

    private String buildFallbackShoppingReply(Map<String, Object> effectiveContext) {
        Map<String, Object> constraints = resolvedConstraints(effectiveContext);
        Object category = categoryLabel(constraints.isEmpty() ? effectiveContext : constraints);
        Object budget = constraints.isEmpty() ? effectiveContext.get("budget") : constraints.get("budget");
        if (hasValue(category)) {
            if (hasBudgetValue(budget)) {
                return "收到，你想买%s，我会基于当前预算、偏好和注意事项继续筛选具体款式。".formatted(category);
            }
            return "收到，你想买%s。请补充预算、品牌偏好或必须功能，我会给你推荐具体款式。".formatted(category);
        }
        return "我已收到你的需求。你可以告诉我想买的品类、预算和使用场景，我会给你推荐具体型号。";
    }

    private Object categoryLabel(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        if (hasValue(context.get("categoryName"))) {
            return context.get("categoryName");
        }
        if (hasValue(context.get("categoryRaw"))) {
            return context.get("categoryRaw");
        }
        if (hasValue(context.get("categoryId"))) {
            return context.get("categoryId");
        }
        return context.get("category");
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"null".equalsIgnoreCase(s);
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (hasValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean hasBudgetValue(Object value) {
        if (!(value instanceof Map<?, ?> budget)) {
            return false;
        }
        return hasValue(budget.get("min")) || hasValue(budget.get("max"));
    }
}
