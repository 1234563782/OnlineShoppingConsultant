package com.onlineshopping.catalog.vector;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@Conditional(VectorSearchEnabledCondition.class)
public class VectorDataSourceConfig {

    @Bean(name = "vectorDataSource")
    public DataSource vectorDataSource(VectorStoreProperties properties) {
        DataSourceProperties dsp = new DataSourceProperties();
        dsp.setUrl(properties.getJdbcUrl());
        dsp.setUsername(properties.getUsername());
        dsp.setPassword(properties.getPassword());
        dsp.setDriverClassName("org.postgresql.Driver");
        return dsp.initializeDataSourceBuilder().build();
    }

    @Bean(name = "vectorJdbcTemplate")
    public JdbcTemplate vectorJdbcTemplate(@Qualifier("vectorDataSource") DataSource vectorDataSource) {
        JdbcTemplate template = new JdbcTemplate(vectorDataSource);
        template.setQueryTimeout(10);
        return template;
    }
}
