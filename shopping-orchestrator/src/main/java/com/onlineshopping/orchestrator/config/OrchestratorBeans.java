package com.onlineshopping.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OrchestratorBeans {

    @Bean
    RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
