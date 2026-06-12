package com.onlineshopping.compare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "shopping.agent.loop-limit")
public class AgentLoopLimitProperties {

    /**
     * Max ReAct iterations (model <-> tool cycles) per A2A request.
     * Aligns with compare-agent prompt frontmatter max_turns.
     */
    private int maxIterations = 6;

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
}
