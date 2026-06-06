package com.onlineshopping.orchestrator.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.ChatResponse;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.service.ContextExtractionService;
import com.onlineshopping.orchestrator.service.ContextMergeService;
import com.onlineshopping.orchestrator.service.MemoryClientService;
import com.onlineshopping.orchestrator.service.MemoryMergeService;
import com.onlineshopping.orchestrator.service.SessionStoreService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final LlmRoutingAgent supervisorAgent;
    private final SessionStoreService sessionStoreService;
    private final MemoryClientService memoryClientService;
    private final ContextExtractionService contextExtractionService;
    private final ContextMergeService contextMergeService;
    private final MemoryMergeService memoryMergeService;
    private final ObjectMapper objectMapper;

    public ChatController(
            @Qualifier("supervisorAgentBean") LlmRoutingAgent supervisorAgent,
            SessionStoreService sessionStoreService,
            MemoryClientService memoryClientService,
            ContextExtractionService contextExtractionService,
            ContextMergeService contextMergeService,
            MemoryMergeService memoryMergeService,
            ObjectMapper objectMapper
    ) {
        this.supervisorAgent = supervisorAgent;
        this.sessionStoreService = sessionStoreService;
        this.memoryClientService = memoryClientService;
        this.contextExtractionService = contextExtractionService;
        this.contextMergeService = contextMergeService;
        this.memoryMergeService = memoryMergeService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) throws Exception {
        String sessionId = (request.getSessionId() == null || request.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.getSessionId();

        SessionState sessionState = sessionStoreService.getSession(sessionId, request.getUserId());
        Map<String, Object> profile = memoryClientService.getProfile(request.getUserId());
        Map<String, Object> currentSessionContext = sessionState.getSessionContext();
        String pendingField = pendingField(currentSessionContext);
        Map<String, Object> extractedPatch = pendingField == null
                ? contextExtractionService.extractPatch(request.getMessage(), currentSessionContext)
                : contextExtractionService.extractPendingFieldPatch(pendingField, request.getMessage(), currentSessionContext);
        normalizeCategoryRawPatch(request.getMessage(), sessionState.getSessionContext(), extractedPatch);
        Map<String, Object> sessionContext = contextMergeService.mergeSessionPatch(
                sessionState.getSessionContext(),
                extractedPatch
        );
        applyPendingFieldResult(sessionContext, pendingField, extractedPatch);
        boolean allowLongTermFallback = shouldAllowLongTermFallback(sessionContext);
        Map<String, Object> effectiveContext = contextMergeService.buildEffectiveContext(
                sessionContext,
                profile,
                allowLongTermFallback
        );
        sessionState.setSessionContext(sessionContext);

        String intentType = String.valueOf(effectiveContext.getOrDefault("intentType", "shopping"));
        if ("small_talk".equalsIgnoreCase(intentType) || "non_shopping".equalsIgnoreCase(intentType)) {
            String directReply = buildSmallTalkReply(request.getMessage());
            sessionStoreService.appendTurns(sessionId, sessionState, request.getMessage(), directReply);
            ChatResponse response = new ChatResponse();
            response.setSessionId(sessionId);
            response.setReply(directReply);
            response.setDebug(Map.of(
                    "toolMode", "orchestrator_direct_reply",
                    "reason", intentType,
                    "sessionContext", sessionContext
            ));
            return response;
        }

        String clarification = buildClarificationIfNeeded(effectiveContext);
        if (clarification != null) {
            markAskedFields(sessionContext, effectiveContext);
            markPendingField(sessionContext, effectiveContext, clarification);
            sessionState.setSessionContext(sessionContext);
            sessionStoreService.appendTurns(sessionId, sessionState, request.getMessage(), clarification);
            LongTermMemoryWriteResult memoryWrite = persistLongTermMemory(
                    request.getUserId(),
                    extractedPatch,
                    Map.of(),
                    effectiveContext,
                    request.getMessage()
            );
            ChatResponse response = new ChatResponse();
            response.setSessionId(sessionId);
            response.setReply(clarification);
            response.setDebug(buildMemoryDebugMap(
                    "orchestrator_clarify",
                    Map.of(
                            "sessionContext", sessionContext,
                            "effectiveContext", effectiveContext
                    ),
                    memoryWrite
            ));
            return response;
        }

        String userInput = buildUserInput(request.getMessage(), request.getUserId(), effectiveContext);

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

        String rawReply = extractReply(outputs);
        AgentResult agentResult = parseAgentResult(rawReply);
        String reply = agentResult.assistantReply();
        if (isInvalidReply(reply)) {
            reply = buildFallbackShoppingReply(effectiveContext);
        }

        sessionStoreService.appendTurns(sessionId, sessionState, request.getMessage(), reply);
        LongTermMemoryWriteResult memoryWrite = persistLongTermMemory(
                request.getUserId(),
                extractedPatch,
                agentResult.memoryPatch(),
                effectiveContext,
                request.getMessage()
        );

        ChatResponse response = new ChatResponse();
        response.setSessionId(sessionId);
        response.setReply(reply);
        response.setDebug(buildMemoryDebugMap(
                "a2a+nacos",
                Map.of(
                        "memoryProfile", profile,
                        "sessionContext", sessionContext,
                        "effectiveContext", effectiveContext
                ),
                memoryWrite
        ));
        return response;
    }

    private LongTermMemoryWriteResult persistLongTermMemory(
            String userId,
            Map<String, Object> extractedPatch,
            Map<String, Object> agentMemoryPatch,
            Map<String, Object> effectiveContext,
            String userMessage
    ) {
        Map<String, Object> extractionPatch = contextMergeService.toLongTermMemoryPatch(extractedPatch);
        Map<String, Object> agentPatch = memoryMergeService.sanitizeAgentPatch(
                agentMemoryPatch,
                effectiveContext,
                extractionPatch,
                extractedPatch,
                userMessage
        );
        Map<String, Object> mergedPatch = memoryMergeService.mergeForProfile(extractionPatch, agentPatch);
        memoryClientService.mergePatch(userId, mergedPatch);
        return new LongTermMemoryWriteResult(extractionPatch, agentPatch, mergedPatch);
    }

    private Map<String, Object> buildMemoryDebugMap(
            String toolMode,
            Map<String, Object> extra,
            LongTermMemoryWriteResult memoryWrite
    ) {
        Map<String, Object> debug = new HashMap<>(extra);
        debug.put("toolMode", toolMode);
        debug.put("memoryPatchFromExtraction", memoryWrite.extractionPatch());
        debug.put("memoryPatchFromAgent", memoryWrite.agentPatch());
        debug.put("memoryPatchMerged", memoryWrite.mergedPatch());
        debug.put("profileWritten", memoryWrite.mergedPatch() != null && !memoryWrite.mergedPatch().isEmpty());
        return debug;
    }

    private record LongTermMemoryWriteResult(
            Map<String, Object> extractionPatch,
            Map<String, Object> agentPatch,
            Map<String, Object> mergedPatch
    ) {
    }

    private String buildUserInput(String message, String userId, Map<String, Object> effectiveContext) {
        return """
                请你作为导购咨询子Agent，基于结构化上下文回答。
                返回严格JSON对象（不要markdown、不要代码块）：
                {
                  "assistantReply":"给用户看的自然语言回复",
                  "memoryPatch":{
                    "brandPreferences":["string"],
                    "dislikes":["string"],
                    "notes":"string|null"
                  }
                }
                规则：
                1. effectiveContext 是主Agent已经整理好的本次咨询上下文，以它为准执行。
                2. 你是推荐执行者，不负责追问缺失字段；缺预算、缺场景或 userUncertain=true 时，也必须调用工具并给出具体推荐。
                3. 如果预算缺失或用户说“先看看”，按不同价位/常见档位推荐；如果场景缺失，按通用需求假设推荐并说明假设。
                4. 禁止用“请告诉我预算/用途/场景/方便告诉我吗”等追问替代推荐；推荐后可以附带一句可选补充建议。
                5. 推荐时必须给出具体款式（名称+大致价格+理由）。
                6. 如果工具返回当前品类或价格段没有精确命中，要如实说明，并推荐工具给出的其他品类或其他价格段候选。
                7. memoryPatch 仅包含用户在本轮或 effectiveContext 中已明确表达的稳定长期偏好，允许字段只有 brandPreferences、dislikes、notes。
                8. 禁止推测用户未说过的品牌、排斥项或长期备注；不要填写 budget、scene；无新增长期偏好时返回空对象 {}。
                                
                userId: %s
                userMessage: %s
                effectiveContext: %s
                """.formatted(userId, message, effectiveContext);
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

    private AgentResult parseAgentResult(String rawReply) {
        if (rawReply == null || rawReply.isBlank()) {
            return new AgentResult("", Map.of());
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawReply, new TypeReference<>() {
            });
            String assistantReply = String.valueOf(parsed.getOrDefault("assistantReply", ""));
            if (isInvalidReply(assistantReply)) {
                Object alt = parsed.get("reply");
                if (alt != null) {
                    assistantReply = String.valueOf(alt);
                }
            }
            Object patch = parsed.get("memoryPatch");
            Map<String, Object> memoryPatch = patch instanceof Map<?, ?> p
                    ? (Map<String, Object>) p
                    : Map.of();
            return new AgentResult(isInvalidReply(assistantReply) ? rawReply : assistantReply, memoryPatch);
        } catch (Exception ignored) {
            Matcher matcher = Pattern.compile("\\{[\\s\\S]*\\}").matcher(rawReply);
            if (matcher.find()) {
                String possibleJson = matcher.group();
                try {
                    Map<String, Object> parsed = objectMapper.readValue(possibleJson, new TypeReference<>() {
                    });
                    String assistantReply = String.valueOf(parsed.getOrDefault("assistantReply", ""));
                    Object patch = parsed.get("memoryPatch");
                    Map<String, Object> memoryPatch = patch instanceof Map<?, ?> p
                            ? (Map<String, Object>) p
                            : Map.of();
                    if (!isInvalidReply(assistantReply)) {
                        return new AgentResult(assistantReply, memoryPatch);
                    }
                } catch (Exception ignoredAgain) {
                }
            }
            return new AgentResult(rawReply, Map.of());
        }
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
        if (missingFields.contains("category")) {
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

    private boolean shouldAllowLongTermFallback(Map<String, Object> sessionContext) {
        boolean userUncertain = Boolean.TRUE.equals(sessionContext.get("userUncertain"));
        List<String> askedFields = normalizeStringList(sessionContext.get("askedFields"));
        return userUncertain || askedFields.contains("budget") || askedFields.contains("scene");
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
            Map<String, Object> extractedPatch
    ) {
        if (pendingField == null || sessionContext == null) {
            return;
        }
        boolean answered = Boolean.TRUE.equals(extractedPatch.get("answeredPendingField"));
        boolean userUncertain = Boolean.TRUE.equals(extractedPatch.get("userUncertain"));
        boolean categoryChanged = hasValue(extractedPatch.get("categoryRaw"))
                && !"category".equalsIgnoreCase(pendingField);
        if (answered || userUncertain || categoryChanged || Boolean.FALSE.equals(extractedPatch.get("shouldKeepPending"))) {
            sessionContext.remove("pendingField");
            sessionContext.remove("pendingQuestion");
            return;
        }
        sessionContext.put("pendingField", pendingField);
    }

    private void markAskedFields(Map<String, Object> sessionContext, Map<String, Object> effectiveContext) {
        List<?> missingFields = effectiveContext.get("missingFields") instanceof List<?> list ? list : List.of();
        java.util.LinkedHashSet<String> askedFields = new java.util.LinkedHashSet<>(normalizeStringList(sessionContext.get("askedFields")));
        for (Object field : missingFields) {
            if (field != null) {
                askedFields.add(field.toString());
            }
        }
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
        Object category = categoryLabel(effectiveContext);
        Object budget = effectiveContext.get("budget");
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

    private record AgentResult(String assistantReply, Map<String, Object> memoryPatch) {
    }
}
