package com.onlineshopping.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.agent.AgentRouter;
import com.onlineshopping.orchestrator.agent.AgentTarget;
import com.onlineshopping.orchestrator.auth.AuthSupport;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import com.onlineshopping.orchestrator.service.A2aStreamingClientService;
import com.onlineshopping.orchestrator.service.AgentTurnPromptBuilder;
import com.onlineshopping.orchestrator.service.LongTermMemoryWriteService;
import com.onlineshopping.orchestrator.service.SessionStoreService;
import com.onlineshopping.orchestrator.service.UserInputProcessor;
import com.onlineshopping.prompt.PromptTemplateService;
import com.onlineshopping.prompt.RenderedPrompt;
import com.onlineshopping.orchestrator.support.PlainTextStreamBuffer;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import jakarta.validation.Valid;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentRouter agentRouter;
    private final A2aStreamingClientService a2aStreamingClientService;
    private final SessionStoreService sessionStoreService;
    private final UserInputProcessor userInputProcessor;
    private final LongTermMemoryWriteService longTermMemoryWriteService;
    private final AgentTurnPromptBuilder agentTurnPromptBuilder;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public ChatController(
            AgentRouter agentRouter,
            A2aStreamingClientService a2aStreamingClientService,
            SessionStoreService sessionStoreService,
            UserInputProcessor userInputProcessor,
            LongTermMemoryWriteService longTermMemoryWriteService,
            AgentTurnPromptBuilder agentTurnPromptBuilder,
            PromptTemplateService promptTemplateService,
            ObjectMapper objectMapper
    ) {
        this.agentRouter = agentRouter;
        this.a2aStreamingClientService = a2aStreamingClientService;
        this.sessionStoreService = sessionStoreService;
        this.userInputProcessor = userInputProcessor;
        this.longTermMemoryWriteService = longTermMemoryWriteService;
        this.agentTurnPromptBuilder = agentTurnPromptBuilder;
        this.promptTemplateService = promptTemplateService;
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
        RenderedPrompt agentTurnPrompt = agentTurnPromptBuilder.build(prepared, request);
        AgentTarget agentTarget = agentRouter.resolve(prepared);
        String agentThreadId = agentThreadId(prepared);
        PlainTextStreamBuffer replyBuffer = new PlainTextStreamBuffer();

        Flux<ServerSentEvent<String>> tokenFlux = a2aStreamingClientService.streamMessage(
                        agentTarget.agentName(),
                        agentTurnPrompt.content(),
                        agentThreadId,
                        prepared.userId()
                )
                .concatMap(chunk -> {
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
                finalizeAgentStream(prepared, request, replyBuffer, agentTurnPrompt, agentTarget)
        ).onErrorResume(error -> Flux.just(errorEvent(
                error.getMessage() == null ? "stream failed" : error.getMessage()
        )));
    }

    private Flux<ServerSentEvent<String>> finalizeAgentStream(
            ChatPreparedContext prepared,
            ChatRequest request,
            PlainTextStreamBuffer replyBuffer,
            RenderedPrompt agentTurnPrompt,
            AgentTarget agentTarget
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
                        "a2a-direct",
                        baseTurnDebug(prepared, "a2a-direct", agentTurnPrompt, agentTarget),
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
        return baseTurnDebug(prepared, toolMode, null, null);
    }

    private Map<String, Object> baseTurnDebug(
            ChatPreparedContext prepared,
            String toolMode,
            RenderedPrompt agentTurnPrompt,
            AgentTarget agentTarget
    ) {
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
        if (prepared.prefetchedSearch() != null) {
            debug.put("prefetchedSearch", prepared.prefetchedSearch().toDebugMap());
        }
        if (agentTurnPrompt != null) {
            debug.put("prompts", buildPromptDebug(agentTurnPrompt));
        }
        if (agentTarget != null) {
            debug.put("routedAgent", agentTarget.agentName());
            debug.put("routeReason", agentTarget.reason());
        }
        return debug;
    }

    private Map<String, Object> buildPromptDebug(RenderedPrompt agentTurnPrompt) {
        Map<String, Object> prompts = new LinkedHashMap<>();
        prompts.put("manifestVersion", promptTemplateService.manifestVersion());
        prompts.put("agentTurn", agentTurnPrompt.toDebugMap());
        return prompts;
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
