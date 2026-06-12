package com.onlineshopping.orchestrator.agent;

import com.onlineshopping.orchestrator.config.AgentRoutingProperties;
import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.PrefetchedCompareResult;
import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
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
        byIntent.put("discover", "consult_agent");
        byIntent.put("compare", "compare_agent");
        byIntent.put("shopping", "consult_agent");
        properties.setByIntent(byIntent);
        agentRouter = new AgentRouter(properties);
    }

    @Test
    void resolvesConsultAgentForDiscoverIntent() {
        AgentTarget target = agentRouter.resolve(preparedContext("shopping", SessionContextKeys.SUB_INTENT_DISCOVER));

        assertEquals("consult_agent", target.agentName());
        assertEquals("subIntent=discover", target.reason());
    }

    @Test
    void resolvesCompareAgentForCompareIntent() {
        AgentTarget target = agentRouter.resolve(preparedContext("shopping", SessionContextKeys.SUB_INTENT_COMPARE));

        assertEquals("compare_agent", target.agentName());
        assertEquals("subIntent=compare", target.reason());
    }

    @Test
    void fallsBackToDefaultAgentForUnknownSubIntent() {
        AgentTarget target = agentRouter.resolve(preparedContext("shopping", "order_tracking"));

        assertEquals("consult_agent", target.agentName());
        assertEquals("subIntent=order_tracking", target.reason());
    }

    private ChatPreparedContext preparedContext(String intentType, String shoppingSubIntent) {
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
                shoppingSubIntent,
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
                PrefetchedSearchResult.skipped(),
                PrefetchedCompareResult.skipped()
        );
    }
}
