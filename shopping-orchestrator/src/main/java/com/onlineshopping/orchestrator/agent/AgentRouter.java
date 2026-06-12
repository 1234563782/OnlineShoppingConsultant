package com.onlineshopping.orchestrator.agent;

import com.onlineshopping.orchestrator.config.AgentRoutingProperties;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentRouter {

    private final AgentRoutingProperties properties;

    public AgentRouter(AgentRoutingProperties properties) {
        this.properties = properties;
    }

    public AgentTarget resolve(ChatPreparedContext prepared) {
        String routeKey = normalizeRouteKey(prepared);
        String agentName = properties.getByIntent().get(routeKey);
        if (!StringUtils.hasText(agentName)) {
            agentName = properties.getDefaultAgent();
        }
        if (!StringUtils.hasText(agentName)) {
            throw new IllegalStateException("No agent configured for route: " + routeKey);
        }
        return new AgentTarget(agentName.trim(), "subIntent=" + routeKey);
    }

    private String normalizeRouteKey(ChatPreparedContext prepared) {
        if (prepared.shoppingSubIntent() != null && !prepared.shoppingSubIntent().isBlank()) {
            return prepared.shoppingSubIntent().trim().toLowerCase();
        }
        String intentType = normalizeIntent(prepared.intentType());
        if (SessionContextKeys.SUB_INTENT_COMPARE.equals(intentType)) {
            return SessionContextKeys.SUB_INTENT_COMPARE;
        }
        if ("shopping".equals(intentType)) {
            return SessionContextKeys.SUB_INTENT_DISCOVER;
        }
        return intentType;
    }

    private String normalizeIntent(String intentType) {
        if (!StringUtils.hasText(intentType)) {
            return SessionContextKeys.SUB_INTENT_DISCOVER;
        }
        return intentType.trim().toLowerCase();
    }
}
