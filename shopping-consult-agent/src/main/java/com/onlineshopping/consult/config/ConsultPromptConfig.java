package com.onlineshopping.consult.config;

import com.onlineshopping.prompt.PromptTemplateService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConsultPromptConfig {

    private final PromptTemplateService promptTemplateService;

    public ConsultPromptConfig(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public String getConsultAgentInstruction() {
        return promptTemplateService.render("consult-agent", Map.of()).content();
    }
}
