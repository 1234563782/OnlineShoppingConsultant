package com.onlineshopping.compare.config;

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
import java.util.Set;

@Configuration
public class CompareAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(CompareAgentConfig.class);
    private static final Set<String> ALLOWED_TOOLS = Set.of("compareProducts");

    private final ComparePromptConfig promptConfig;
    private final AgentLoopLimitProperties loopLimitProperties;

    public CompareAgentConfig(ComparePromptConfig promptConfig, AgentLoopLimitProperties loopLimitProperties) {
        this.promptConfig = promptConfig;
        this.loopLimitProperties = loopLimitProperties;
    }

    @Bean(name = "compareSubAgentBean")
    public ReactAgent compareSubAgentBean(
            @Qualifier("dashscopeChatModel") ChatModel chatModel,
            @Autowired(required = false) @Qualifier("loadbalancedMcpSyncToolCallbacks") ToolCallbackProvider nacosToolsProvider
    ) throws Exception {
        List<ToolCallback> tools = new ArrayList<>();
        if (nacosToolsProvider != null) {
            for (ToolCallback callback : nacosToolsProvider.getToolCallbacks()) {
                String toolName = callback.getToolDefinition().name();
                if (ALLOWED_TOOLS.contains(toolName)) {
                    tools.add(callback);
                    log.info("compare_agent add mcp tool: {}", toolName);
                }
            }
        }

        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> map = new HashMap<>();
            map.put("query", new ReplaceStrategy());
            map.put("result", new ReplaceStrategy());
            return map;
        };

        int maxIterations = loopLimitProperties.getMaxIterations();
        log.info("compare_agent maxIterations={}", maxIterations);

        return ReactAgent.builder()
                .name("compare_agent")
                .description("电商商品对比Agent，负责多 SKU 结构化对比与选购结论")
                .model(chatModel)
                .state(stateFactory)
                .instruction(promptConfig.getCompareAgentInstruction())
                .inputKey("query")
                .outputKey("result")
                .tools(tools)
                .maxIterations(maxIterations)
                .build();
    }
}
