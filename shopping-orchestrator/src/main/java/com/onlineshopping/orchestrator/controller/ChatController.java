package com.onlineshopping.orchestrator.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.auth.AuthSupport;
import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.service.CategoryResolutionService;
import com.onlineshopping.orchestrator.service.ContextExtractionService;
import com.onlineshopping.orchestrator.service.ContextMergeService;
import com.onlineshopping.orchestrator.service.MemoryClientService;
import com.onlineshopping.orchestrator.service.LongTermMemoryWriteService;
import com.onlineshopping.orchestrator.service.SessionStoreService;
import com.onlineshopping.orchestrator.support.PlainTextStreamBuffer;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
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
    private final ContextExtractionService contextExtractionService;
    private final ContextMergeService contextMergeService;
    private final LongTermMemoryWriteService longTermMemoryWriteService;
    private final CategoryResolutionService categoryResolutionService;
    private final ObjectMapper objectMapper;

    public ChatController(
            @Qualifier("supervisorAgentBean") LlmRoutingAgent supervisorAgent,
            SessionStoreService sessionStoreService,
            MemoryClientService memoryClientService,
            ContextExtractionService contextExtractionService,
            ContextMergeService contextMergeService,
            LongTermMemoryWriteService longTermMemoryWriteService,
            CategoryResolutionService categoryResolutionService,
            ObjectMapper objectMapper
    ) {
        this.supervisorAgent = supervisorAgent;
        this.sessionStoreService = sessionStoreService;
        this.memoryClientService = memoryClientService;
        this.contextExtractionService = contextExtractionService;
        this.contextMergeService = contextMergeService;
        this.longTermMemoryWriteService = longTermMemoryWriteService;
        this.categoryResolutionService = categoryResolutionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@Valid @RequestBody ChatRequest request) {
        final String userId = AuthSupport.requireUserId();
        return Flux.defer(() -> {
            try {
                String sessionId = resolveSessionId(request.getSessionId());
                ChatPreparedContext prepared = prepareContext(userId, request, sessionId);

                if (isSmallTalkOrNonShopping(prepared.intentType())) {
                    String reply = buildSmallTalkReply(request.getMessage());
                    sessionStoreService.appendTurns(userId, sessionId, prepared.sessionState(), request.getMessage(), reply);
                    return streamStaticReply(
                            sessionId,
                            reply,
                            Map.of(
                                    "toolMode", "orchestrator_direct_reply",
                                    "reason", prepared.intentType(),
                                    "sessionContext", prepared.sessionContext()
                            )
                    );
                }

                String clarification = buildClarificationIfNeeded(prepared.effectiveContext());
                if (clarification != null) {
                    markAskedFields(prepared.sessionContext(), prepared.effectiveContext());
                    markPendingField(prepared.sessionContext(), prepared.effectiveContext(), clarification);
                    prepared.sessionState().setSessionContext(prepared.sessionContext());
                    sessionStoreService.appendTurns(userId, sessionId, prepared.sessionState(), request.getMessage(), clarification);
                    LongTermMemoryWriteService.WriteResult memoryWrite = longTermMemoryWriteService.write(
                            userId,
                            prepared.extractedPatch(),
                            prepared.effectiveContext(),
                            prepared.profile(),
                            request.getMessage()
                    );
                    Map<String, Object> debug = buildMemoryDebugMap(
                            "orchestrator_clarify",
                            Map.of(
                                    "sessionContext", prepared.sessionContext(),
                                    "effectiveContext", prepared.effectiveContext(),
                                    "categoryResolution", prepared.categoryResolution().toDebugMap()
                            ),
                            memoryWrite
                    );
                    return streamStaticReply(sessionId, clarification, debug);
                }

                return streamAgentReply(prepared, request);
            } catch (Exception e) {
                return Flux.just(errorEvent(e.getMessage() == null ? "stream failed" : e.getMessage()));
            }
        });
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

    private Flux<ServerSentEvent<String>> streamAgentReply(ChatPreparedContext prepared, ChatRequest request) throws Exception {
        String userInput = buildUserInput(
                request.getMessage(),
                prepared.userId(),
                resolvedConstraints(prepared.effectiveContext())
        );
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(prepared.sessionId())
                .addMetadata("user_id", prepared.userId())
                .build();
        Map<String, Object> input = Map.of(
                "input", userInput,
                "chat_id", prepared.sessionId(),
                "user_id", prepared.userId()
        );

        CompiledGraph compiledGraph = supervisorAgent.getAndCompileGraph();
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
                Flux.defer(() -> finalizeAgentStream(prepared, request, replyBuffer))
        ).onErrorResume(error -> Flux.just(errorEvent(
                error.getMessage() == null ? "stream failed" : error.getMessage()
        )));
    }

    private Flux<ServerSentEvent<String>> finalizeAgentStream(
            ChatPreparedContext prepared,
            ChatRequest request,
            PlainTextStreamBuffer replyBuffer
    ) {
        String reply = normalizeAgentReply(replyBuffer.content(), prepared.effectiveContext());

        sessionStoreService.appendTurns(prepared.userId(), prepared.sessionId(), prepared.sessionState(), request.getMessage(), reply);
        LongTermMemoryWriteService.WriteResult memoryWrite = longTermMemoryWriteService.write(
                prepared.userId(),
                prepared.extractedPatch(),
                prepared.effectiveContext(),
                prepared.profile(),
                request.getMessage()
        );
        Map<String, Object> debug = buildMemoryDebugMap(
                "a2a+nacos",
                Map.of(
                        "memoryProfile", prepared.profile(),
                        "sessionContext", prepared.sessionContext(),
                        "effectiveContext", prepared.effectiveContext(),
                        "categoryResolution", prepared.categoryResolution().toDebugMap()
                ),
                memoryWrite
        );
        return Flux.just(doneEvent(prepared.sessionId(), reply, debug));
    }

    private String normalizeAgentReply(String rawReply, Map<String, Object> effectiveContext) {
        if (rawReply == null || rawReply.isBlank() || isInvalidReply(rawReply)) {
            return buildFallbackShoppingReply(effectiveContext);
        }
        return rawReply.trim();
    }

    private ChatPreparedContext prepareContext(String userId, ChatRequest request, String sessionId) {
        SessionState sessionState = sessionStoreService.getSession(userId, sessionId);
        Map<String, Object> profile = memoryClientService.getProfile(userId);
        Map<String, Object> currentSessionContext = sessionState.getSessionContext();
        String pendingField = pendingField(currentSessionContext);
        Map<String, Object> extractedPatch = pendingField == null
                ? contextExtractionService.extractPatch(request.getMessage(), currentSessionContext)
                : contextExtractionService.extractPendingFieldPatch(
                pendingField, request.getMessage(), currentSessionContext);
        normalizeCategoryRawPatch(request.getMessage(), sessionState.getSessionContext(), extractedPatch);
        Map<String, Object> sessionContext = contextMergeService.mergeSessionPatch(
                sessionState.getSessionContext(),
                extractedPatch
        );
        applyPendingFieldResult(sessionContext, pendingField, extractedPatch, request.getMessage());
        applyCategoryConfirmation(request.getMessage(), pendingField, sessionContext);
        CategoryResolutionResult categoryResolution = categoryResolutionService.resolve(sessionContext);
        boolean allowLongTermFallback = true;
        Map<String, Object> effectiveContext = contextMergeService.buildEffectiveContext(
                sessionContext,
                profile,
                allowLongTermFallback
        );
        effectiveContext.put("categoryResolution", sessionContext.getOrDefault("categoryResolution", CategoryResolutionResult.STATUS_SKIPPED));
        if (sessionContext.get("categoryConfidence") != null) {
            effectiveContext.put("categoryConfidence", sessionContext.get("categoryConfidence"));
        }
        sessionState.setSessionContext(sessionContext);
        String intentType = String.valueOf(effectiveContext.getOrDefault("intentType", "shopping"));
        return new ChatPreparedContext(
                userId,
                sessionId,
                sessionState,
                profile,
                extractedPatch,
                sessionContext,
                effectiveContext,
                intentType,
                categoryResolution
        );
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }

    private boolean isSmallTalkOrNonShopping(String intentType) {
        return "small_talk".equalsIgnoreCase(intentType) || "non_shopping".equalsIgnoreCase(intentType);
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

    private String buildUserInput(String message, String userId, Map<String, Object> resolvedConstraints) {
        return """
                请你作为导购咨询子Agent，基于结构化上下文回答。
                直接输出给用户看的自然语言回复，不要 JSON、不要 markdown、不要代码块。
                规则：
                1. resolvedConstraints 是主Agent已经整理好的本次咨询约束，以它为准执行；会话内表达优先于历史画像。
                2. 调用 searchProduct 时必须优先传 resolvedConstraints.categoryId；仅当 categoryId 为空时才传 categoryRaw。
                3. 你是推荐执行者，不负责追问缺失字段；缺预算、缺场景或 userUncertain=true 时，也必须调用工具并给出具体推荐。
                4. 如果预算缺失或用户说“先看看”，按不同价位/常见档位推荐；如果场景缺失，按通用需求假设推荐并说明假设。
                5. 禁止用“请告诉我预算/用途/场景/方便告诉我吗”等追问替代推荐；推荐后可以附带一句可选补充建议。
                6. 只能推荐 searchProduct 返回 JSON 中 products 里的商品；名称、价格必须与工具字段完全一致，禁止编造未返回的型号或改写价格（禁止“约/大概”）。
                7. 若 products 不足 3 款，只推荐实际返回的数量，禁止凑数编造。
                8. 如果工具返回当前品类或价格段没有精确命中，要如实说明，并推荐工具给出的其他品类或其他价格段候选（仍只能来自 products）。
                                
                userId: %s
                userMessage: %s
                resolvedConstraints: %s
                """.formatted(userId, message, resolvedConstraints);
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String buildSmallTalkReply(String message) {
        if (message == null || message.isBlank()) {
            return "你好，我是导购助手。告诉我你想买的品类、预算和使用场景，我就能给你推荐具体款式。";
        }
        String text = message.trim();
        if (containsAny(text, "谢谢", "辛苦了")) {
            return "不客气。你可以继续告诉我预算、场景或品牌偏好，我会继续帮你筛选。";
        }
        if (containsAny(text, "再见", "拜拜")) {
            return "好的，随时来找我，我可以继续帮你选购。";
        }
        return "你好，我在。你想买什么品类？可以直接说预算、使用场景和偏好。";
    }

    private void normalizeCategoryRawPatch(
            String userMessage,
            Map<String, Object> currentSessionContext,
            Map<String, Object> extractedPatch
    ) {
        if (extractedPatch == null) {
            return;
        }
        if (!hasValue(extractedPatch.get("categoryRaw")) && hasValue(extractedPatch.get("category"))) {
            extractedPatch.put("categoryRaw", extractedPatch.get("category"));
        }
        extractedPatch.remove("category");
        if (!hasValue(extractedPatch.get("categoryRaw")) && hasValue(extractedPatch.get("categoryName"))) {
            extractedPatch.put("categoryRaw", extractedPatch.get("categoryName"));
        }
        extractedPatch.remove("categoryId");
        extractedPatch.remove("categoryName");

        Object extractedCategoryRaw = extractedPatch.get("categoryRaw");
        if (!hasValue(extractedCategoryRaw)) {
            return;
        }
        String raw = extractedCategoryRaw.toString().trim();
        if (containsIgnoreCase(userMessage, raw)) {
            extractedPatch.put("intentType", "shopping");
            return;
        }
        Object currentCategoryRaw = categoryLabel(currentSessionContext);
        if (hasValue(currentCategoryRaw)
                && !currentCategoryRaw.toString().equalsIgnoreCase(raw)) {
            extractedPatch.remove("categoryRaw");
        }
    }

    private boolean containsIgnoreCase(String text, String needle) {
        return text != null
                && needle != null
                && !needle.isBlank()
                && text.toLowerCase(java.util.Locale.ROOT).contains(needle.toLowerCase(java.util.Locale.ROOT));
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

    private String buildClarificationIfNeeded(Map<String, Object> effectiveContext) {
        List<?> missingFields = effectiveContext.get("missingFields") instanceof List<?> list ? list : List.of();
        boolean userUncertain = Boolean.TRUE.equals(effectiveContext.get("userUncertain"));
        List<String> askedFields = normalizeStringList(effectiveContext.get("askedFields"));
        String categoryResolution = effectiveContext.get("categoryResolution") == null
                ? ""
                : effectiveContext.get("categoryResolution").toString();
        Object categoryRaw = effectiveContext.get("categoryRaw");
        Object categoryName = effectiveContext.get("categoryName");

        if (missingFields.contains("categoryConfirm") && !askedFields.contains("categoryConfirm")) {
            return "您说的「%s」，是指「%s」这个品类吗？可以直接回复“是”或纠正我。"
                    .formatted(
                            categoryRaw == null ? "这个商品" : categoryRaw,
                            categoryName == null ? categoryRaw : categoryName
                    );
        }
        if (missingFields.contains("category")) {
            if (CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE.equals(categoryResolution)) {
                return "类目服务暂时不可用，请稍后再试；你也可以直接说具体品类，如手机、耳机、电脑。";
            }
            if (CategoryResolutionResult.STATUS_UNRESOLVED.equals(categoryResolution) && hasValue(categoryRaw)) {
                return "我暂时没识别到「%s」对应的商品品类，能再说具体一点吗？比如手机、电脑、平板。"
                        .formatted(categoryRaw);
            }
            return "你想买什么品类或商品？可以直接说商品名、预算、使用场景和偏好。";
        }
        Object category = categoryLabel(effectiveContext);
        if (missingFields.contains("budget") && !userUncertain && !askedFields.contains("budget")) {
            return "收到，你想买%s。请补充一下大概预算；如果暂时不确定，也可以说“先看看”，我会按不同价位给你推荐。"
                    .formatted(category);
        }
        if (missingFields.contains("scene") && !userUncertain && !askedFields.contains("scene")) {
            return "这个%s主要用在什么场景？比如通勤、办公、学习、运动或游戏。"
                    .formatted(category);
        }
        return null;
    }

    private String pendingField(Map<String, Object> sessionContext) {
        if (sessionContext == null || !hasValue(sessionContext.get("pendingField"))) {
            return null;
        }
        return sessionContext.get("pendingField").toString();
    }

    private void applyPendingFieldResult(
            Map<String, Object> sessionContext,
            String pendingField,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (pendingField == null || sessionContext == null) {
            return;
        }
        boolean answered = Boolean.TRUE.equals(extractedPatch.get("answeredPendingField"));
        boolean userUncertain = Boolean.TRUE.equals(extractedPatch.get("userUncertain"));
        boolean categoryChanged = hasValue(extractedPatch.get("categoryRaw"))
                && !"category".equalsIgnoreCase(pendingField)
                && !"categoryConfirm".equalsIgnoreCase(pendingField);
        boolean categoryConfirmed = "categoryConfirm".equalsIgnoreCase(pendingField) && isAffirmativeReply(userMessage);
        if (answered || userUncertain || categoryChanged || categoryConfirmed
                || Boolean.FALSE.equals(extractedPatch.get("shouldKeepPending"))) {
            sessionContext.remove("pendingField");
            sessionContext.remove("pendingQuestion");
            return;
        }
        sessionContext.put("pendingField", pendingField);
    }

    private void applyCategoryConfirmation(
            String userMessage,
            String pendingField,
            Map<String, Object> sessionContext
    ) {
        if (sessionContext == null) {
            return;
        }
        if ("categoryConfirm".equalsIgnoreCase(pendingField) && isAffirmativeReply(userMessage)) {
            sessionContext.put("categoryResolution", CategoryResolutionResult.STATUS_RESOLVED);
        }
    }

    private boolean isAffirmativeReply(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.trim().toLowerCase(java.util.Locale.ROOT);
        return text.equals("是")
                || text.equals("对")
                || text.equals("嗯")
                || text.equals("yes")
                || text.equals("y")
                || text.contains("没错")
                || text.contains("是的")
                || text.contains("对的");
    }

    private void markAskedFields(Map<String, Object> sessionContext, Map<String, Object> effectiveContext) {
        String nextField = nextClarificationField(effectiveContext);
        if (nextField == null) {
            return;
        }
        java.util.LinkedHashSet<String> askedFields = new java.util.LinkedHashSet<>(normalizeStringList(sessionContext.get("askedFields")));
        askedFields.add(nextField);
        sessionContext.put("askedFields", new ArrayList<>(askedFields));
    }

    private void markPendingField(
            Map<String, Object> sessionContext,
            Map<String, Object> effectiveContext,
            String question
    ) {
        String field = nextClarificationField(effectiveContext);
        if (field == null) {
            sessionContext.remove("pendingField");
            sessionContext.remove("pendingQuestion");
            return;
        }
        sessionContext.put("pendingField", field);
        sessionContext.put("pendingQuestion", question);
    }

    private String nextClarificationField(Map<String, Object> effectiveContext) {
        List<?> missingFields = effectiveContext.get("missingFields") instanceof List<?> list ? list : List.of();
        boolean userUncertain = Boolean.TRUE.equals(effectiveContext.get("userUncertain"));
        List<String> askedFields = normalizeStringList(effectiveContext.get("askedFields"));
        if (missingFields.contains("categoryConfirm") && !askedFields.contains("categoryConfirm")) {
            return "categoryConfirm";
        }
        if (missingFields.contains("category")) {
            return "category";
        }
        if (missingFields.contains("budget") && !userUncertain && !askedFields.contains("budget")) {
            return "budget";
        }
        if (missingFields.contains("scene") && !userUncertain && !askedFields.contains("scene")) {
            return "scene";
        }
        return null;
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
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
