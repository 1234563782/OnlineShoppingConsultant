package com.onlineshopping.orchestrator.agent;

import com.onlineshopping.orchestrator.config.AgentRoutingProperties;
import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRouterTest {

    private AgentRouter agentRouter;

    @BeforeEach
    void setUp() {
        AgentRoutingProperties properties = new AgentRoutingProperties();
        properties.setDefaultAgent("consult_agent");
        Map<String, String> byIntent = new LinkedHashMap<>();
        byIntent.put("shopping", "consult_agent");
        properties.setByIntent(byIntent);
        agentRouter = new AgentRouter(properties);
    }

    @Test
    void resolvesConsultAgentForShoppingIntent() {
        AgentTarget target = agentRouter.resolve(preparedContext("shopping"));

        assertEquals("consult_agent", target.agentName());
        assertEquals("intent=shopping", target.reason());
    }

    @Test
    void fallsBackToDefaultAgentForUnknownIntent() {
        AgentTarget target = agentRouter.resolve(preparedContext("order_tracking"));

        assertEquals("consult_agent", target.agentName());
        assertEquals("intent=order_tracking", target.reason());
    }

    private ChatPreparedContext preparedContext(String intentType) {
        TurnDecision decision = new TurnDecision(
                TurnOutcome.READY_FOR_AGENT,
                false,
                "",
                null,
                null,
                null
        );
        return new ChatPreparedContext(
                "user-1",
                "session-1",
                new SessionState(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                intentType,
                new CategoryResolutionResult(
                        CategoryResolutionResult.STATUS_UNRESOLVED,
                        null,
                        null,
                        "test",
                        0.0,
                        null
                ),
                Map.of(),
                decision,
                PrefetchedSearchResult.skipped()
        );
    }
}
