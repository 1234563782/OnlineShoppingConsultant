package com.onlineshopping.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "shopping.agents")
public class AgentRoutingProperties {

    private String defaultAgent = "consult_agent";

    private Map<String, String> byIntent = new LinkedHashMap<>();

    public String getDefaultAgent() {
        return defaultAgent;
    }

    public void setDefaultAgent(String defaultAgent) {
        this.defaultAgent = defaultAgent;
    }

    public Map<String, String> getByIntent() {
        return byIntent;
    }

    public void setByIntent(Map<String, String> byIntent) {
        this.byIntent = byIntent;
    }
}
