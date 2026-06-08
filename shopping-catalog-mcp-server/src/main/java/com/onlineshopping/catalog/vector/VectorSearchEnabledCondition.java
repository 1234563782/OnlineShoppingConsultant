package com.onlineshopping.catalog.vector;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Enables vector beans when shopping.vector.enabled, JDBC URL, and DashScope API key are all set.
 */
public class VectorSearchEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        if (!env.getProperty("shopping.vector.enabled", Boolean.class, false)) {
            return false;
        }
        String jdbcUrl = env.getProperty("shopping.vector.jdbc-url", "");
        if (!StringUtils.hasText(jdbcUrl)) {
            return false;
        }
        String apiKey = env.getProperty("SPRING_AI_DASHSCOPE_API_KEY", "");
        if (!StringUtils.hasText(apiKey)) {
            return false;
        }
        return true;
    }
}
