package com.onlineshopping.orchestrator;

import com.onlineshopping.orchestrator.auth.AuthProperties;
import com.onlineshopping.orchestrator.config.AgentRoutingProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.onlineshopping.orchestrator.auth.mapper")
@EnableConfigurationProperties({AuthProperties.class, AgentRoutingProperties.class})
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
