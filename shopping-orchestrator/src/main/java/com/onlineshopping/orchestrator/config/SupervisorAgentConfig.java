package com.onlineshopping.orchestrator.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;

@Configuration
public class SupervisorAgentConfig {

    @Bean("supervisorAgentBean")
    public LlmRoutingAgent supervisorAgentBean(
            ChatModel chatModel,
            AgentCardProvider agentCardProvider
    ) throws Exception {
        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> strategy = new HashMap<>();
            strategy.put("input", new ReplaceStrategy());
            strategy.put("chat_id", new ReplaceStrategy());
            strategy.put("user_id", new ReplaceStrategy());
            strategy.put("messages", new ReplaceStrategy());
            return strategy;
        };

        A2aRemoteAgent consultAgent = A2aRemoteAgent.builder()
                .name("consult_agent")
                .agentCardProvider(agentCardProvider)
                .description("电商导购咨询Agent")
                .build();

        return LlmRoutingAgent.builder()
                .name("shopping_supervisor")
                .model(chatModel)
                .state(stateFactory)
                .description("协调导购咨询Agent完成用户导购问答")
                .inputKey("input")
                .outputKey("messages")
                .subAgents(List.of(consultAgent))
                .build();
    }
}
