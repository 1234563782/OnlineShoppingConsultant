package com.onlineshopping.orchestrator.agent;

import com.onlineshopping.orchestrator.config.AgentRoutingProperties;
import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentRouter {

    private final AgentRoutingProperties properties;

    public AgentRouter(AgentRoutingProperties properties) {
        this.properties = properties;
    }

    public AgentTarget resolve(ChatPreparedContext prepared) {
        String intentType = normalizeIntent(prepared.intentType());
        String agentName = properties.getByIntent().get(intentType);
        if (!StringUtils.hasText(agentName)) {
            agentName = properties.getDefaultAgent();
        }
        if (!StringUtils.hasText(agentName)) {
            throw new IllegalStateException("No agent configured for intent: " + intentType);
        }
        return new AgentTarget(agentName.trim(), "intent=" + intentType);
    }

    private String normalizeIntent(String intentType) {
        if (!StringUtils.hasText(intentType)) {
            return "shopping";
        }
        return intentType.trim().toLowerCase();
    }
}
