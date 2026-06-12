package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.ChatRequest;
import com.onlineshopping.orchestrator.dto.PrefetchedCompareResult;
import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.prompt.PromptTemplateService;
import com.onlineshopping.prompt.RenderedPrompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentTurnPromptBuilder {

    private final PromptTemplateService promptTemplateService;

    public AgentTurnPromptBuilder(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public RenderedPrompt build(ChatPreparedContext prepared, ChatRequest request) {
        if (SessionContextKeys.SUB_INTENT_COMPARE.equalsIgnoreCase(prepared.shoppingSubIntent())) {
            return buildCompareTurn(prepared, request);
        }
        return buildDiscoverTurn(prepared, request);
    }

    private RenderedPrompt buildDiscoverTurn(ChatPreparedContext prepared, ChatRequest request) {
        Map<String, Object> variables = baseVariables(prepared, request);
        variables.put("prefetchedSearchResult", prefetchedPayload(prepared.prefetchedSearch()));
        variables.put("prefetchedSearch", prepared.prefetchedSearch());
        variables.put("prefetchedOk", prepared.prefetchedSearch() != null && prepared.prefetchedSearch().isUsable());
        variables.put("notices", buildDiscoverNotices(prepared));
        return promptTemplateService.renderRouted("consult_turn", variables);
    }

    private RenderedPrompt buildCompareTurn(ChatPreparedContext prepared, ChatRequest request) {
        Map<String, Object> variables = baseVariables(prepared, request);
        variables.put("prefetchedCompareResult", prefetchedComparePayload(prepared.prefetchedCompare()));
        variables.put("prefetchedCompare", prepared.prefetchedCompare());
        variables.put("prefetchedCompareOk", prepared.prefetchedCompare() != null && prepared.prefetchedCompare().isUsable());
        variables.put("compareFocus", compareFocus(prepared));
        variables.put("notices", buildCompareNotices(prepared));
        return promptTemplateService.render("agent-turn-compare", variables);
    }

    private Map<String, Object> baseVariables(ChatPreparedContext prepared, ChatRequest request) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userId", prepared.userId());
        variables.put("userMessage", request.getMessage());
        variables.put("resolvedConstraints", resolvedConstraints(prepared.effectiveContext()));
        return variables;
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

    private Map<String, Object> prefetchedComparePayload(PrefetchedCompareResult prefetchedCompare) {
        if (prefetchedCompare == null) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", prefetchedCompare.status());
        payload.put("skuIds", prefetchedCompare.skuIds());
        payload.put("products", prefetchedCompare.products());
        payload.put("compareDimensions", prefetchedCompare.compareDimensions());
        payload.put("crossCategory", prefetchedCompare.crossCategory());
        payload.put("message", prefetchedCompare.message());
        return payload;
    }

    private List<String> compareFocus(ChatPreparedContext prepared) {
        if (prepared.sessionContext() == null) {
            return List.of();
        }
        Object focus = prepared.sessionContext().get(SessionContextKeys.COMPARE_FOCUS);
        if (focus instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private String buildDiscoverNotices(ChatPreparedContext prepared) {
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

    private String buildCompareNotices(ChatPreparedContext prepared) {
        StringBuilder notices = new StringBuilder();
        PrefetchedCompareResult prefetchedCompare = prepared.prefetchedCompare();
        if (prefetchedCompare != null && prefetchedCompare.crossCategory()) {
            notices.append("对比商品属于不同品类，请说明对比局限并分别给出适用场景。");
        }
        if (prefetchedCompare != null
                && prefetchedCompare.message() != null
                && !prefetchedCompare.message().isBlank()) {
            if (!notices.isEmpty()) {
                notices.append('\n');
            }
            notices.append(prefetchedCompare.message());
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
