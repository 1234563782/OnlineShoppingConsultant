package com.onlineshopping.consult.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Configuration
public class ConsultAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(ConsultAgentConfig.class);

    private final ConsultPromptConfig promptConfig;

    public ConsultAgentConfig(ConsultPromptConfig promptConfig) {
        this.promptConfig = promptConfig;
    }

    @Bean(name = "consultSubAgentBean")
    public ReactAgent consultSubAgentBean(
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
            @Autowired(required = false) @Qualifier("loadbalancedMcpSyncToolCallbacks") ToolCallbackProvider nacosToolsProvider
    ) throws Exception {
        List<ToolCallback> tools = new ArrayList<>();
        if (nacosToolsProvider != null) {
            for (ToolCallback callback : nacosToolsProvider.getToolCallbacks()) {
                tools.add(callback);
                log.info("consult_agent add mcp tool: {}", callback.getToolDefinition().name());
            }
        }

        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> map = new HashMap<>();
            map.put("query", new ReplaceStrategy());
            map.put("result", new ReplaceStrategy());
            return map;
        };

        return ReactAgent.builder()
                .name("consult_agent")
                .description("电商导购咨询Agent，负责商品搜索、推荐、对比、库存与优惠解读")
                .model(chatModel)
                .state(stateFactory)
                .instruction(promptConfig.getConsultAgentInstruction())
                .inputKey("query")
                .outputKey("result")
                .tools(tools)
                .build();
    }
}
