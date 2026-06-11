package com.onlineshopping.orchestrator.prompt;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.prompt.PromptTemplateService;
import com.onlineshopping.prompt.RenderedPrompt;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateServiceTest {

    private final PromptTemplateService service = new PromptTemplateService();

    @Test
    void renderContextExtractionSubstitutesVariables() {
        RenderedPrompt rendered = service.render(
                "context-extraction",
                Map.of(
                        "sessionContext", Map.of("categoryId", "cat_phone"),
                        "userMessage", "想买手机"
                )
        );

        assertEquals("context-extraction", rendered.promptId());
        assertTrue(rendered.content().contains("想买手机"));
        assertTrue(rendered.content().contains("cat_phone"));
        assertTrue(rendered.content().contains("上下文抽取器"));
    }

    @Test
    void renderRoutedSelectsPrefetchedTurnPrompt() {
        RenderedPrompt rendered = service.renderRouted(
                "consult_turn",
                Map.of(
                        "prefetchedSearch",
                        PrefetchedSearchResult.ok("exact", "ok", List.of(), Map.of()),
                        "userId", "u1",
                        "userMessage", "推荐手机",
                        "resolvedConstraints", Map.of("categoryId", "cat_phone"),
                        "prefetchedSearchResult", Map.of("status", "ok"),
                        "notices", ""
                )
        );

        assertEquals("agent-turn-prefetched", rendered.promptId());
        assertTrue(rendered.content().contains("禁止调用 searchProduct"));
        assertTrue(rendered.content().contains("商品推荐规则"));
    }

    @Test
    void renderRoutedSelectsLegacyTurnPromptWhenPrefetchUnavailable() {
        RenderedPrompt rendered = service.renderRouted(
                "consult_turn",
                Map.of(
                        "prefetchedSearch",
                        PrefetchedSearchResult.unavailable("catalog down"),
                        "userId", "u1",
                        "userMessage", "推荐手机",
                        "resolvedConstraints", Map.of(),
                        "prefetchedSearchResult", Map.of(),
                        "notices", ""
                )
        );

        assertEquals("agent-turn-legacy", rendered.promptId());
        assertTrue(rendered.content().contains("searchProduct"));
    }

    @Test
    void manifestVersionIsLoaded() {
        assertEquals("2026.06.11.1", service.manifestVersion());
    }
}
