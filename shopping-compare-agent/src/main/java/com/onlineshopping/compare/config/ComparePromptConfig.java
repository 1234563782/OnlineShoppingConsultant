package com.onlineshopping.compare.config;

import com.onlineshopping.prompt.PromptTemplateService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ComparePromptConfig {

    private final PromptTemplateService promptTemplateService;

    public ComparePromptConfig(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    public String getCompareAgentInstruction() {
        return promptTemplateService.render("compare-agent", Map.of()).content();
    }
}
