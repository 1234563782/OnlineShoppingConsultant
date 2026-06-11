package com.onlineshopping.prompt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PromptSupportConfiguration {

    @Bean
    public PromptTemplateService promptTemplateService() {
        return new PromptTemplateService();
    }
}
