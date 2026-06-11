package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.prompt.PromptTemplateService;
import com.onlineshopping.prompt.RenderedPrompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AgentTurnPromptBuilder {

    private final PromptTemplateService promptTemplateService;

    public AgentTurnPromptBuilder(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public RenderedPrompt build(ChatPreparedContext prepared, ChatRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userId", prepared.userId());
        variables.put("userMessage", request.getMessage());
        variables.put("resolvedConstraints", resolvedConstraints(prepared.effectiveContext()));
        variables.put("prefetchedSearchResult", prefetchedPayload(prepared.prefetchedSearch()));
        variables.put("prefetchedSearch", prepared.prefetchedSearch());
        variables.put("prefetchedOk", prepared.prefetchedSearch() != null && prepared.prefetchedSearch().isUsable());
        variables.put("notices", buildNotices(prepared));
        return promptTemplateService.renderRouted("consult_turn", variables);
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

    private Map<String, Object> prefetchedPayload(PrefetchedSearchResult prefetchedSearch) {
        if (prefetchedSearch == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", prefetchedSearch.status());
        payload.put("matchType", prefetchedSearch.matchType());
        payload.put("message", prefetchedSearch.message());
        payload.put("products", prefetchedSearch.products());
        payload.put("searchParams", prefetchedSearch.searchParams());
        return payload;
    }

    private String buildNotices(ChatPreparedContext prepared) {
        StringBuilder notices = new StringBuilder();
        if (prepared.turnDecision().categoryReplaced()) {
            notices.append(readNotice("prompts/turn/notices/category-replaced.md"));
        }
        PrefetchedSearchResult prefetchedSearch = prepared.prefetchedSearch();
        if (prefetchedSearch != null && PrefetchedSearchResult.STATUS_UNAVAILABLE.equals(prefetchedSearch.status())) {
            if (!notices.isEmpty()) {
                notices.append('\n');
            }
            notices.append(readNotice("prompts/turn/notices/prefetch-failed.md"));
        }
        return notices.toString();
    }

    private String readNotice(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }
}
