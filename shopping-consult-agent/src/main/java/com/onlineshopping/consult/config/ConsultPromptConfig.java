package com.onlineshopping.consult.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsultPromptConfig {

    @Value("${agent.prompts.consult-agent-instruction}")
    private String consultAgentInstruction;

    public String getConsultAgentInstruction() {
        return consultAgentInstruction;
    }
}
