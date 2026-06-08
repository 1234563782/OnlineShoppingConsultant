package com.onlineshopping.catalog.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Explicit primary MySQL datasource for JPA. Required because {@code vectorDataSource}
 * would otherwise be the only {@link DataSource} bean and disable auto-configuration.
 */
@Configuration
public class CatalogDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties catalogDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource catalogDataSource(DataSourceProperties catalogDataSourceProperties) {
        return catalogDataSourceProperties.initializeDataSourceBuilder().build();
    }
}
