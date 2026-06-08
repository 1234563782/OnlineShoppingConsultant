package com.onlineshopping.catalog.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopping.vector")
public class VectorStoreProperties {

    /**
     * When true and jdbc-url non-blank and DashScope API key present, vector search is used.
     */
    private boolean enabled = false;

    private String jdbcUrl = "";

    private String username = "postgres";

    private String password = "";

    private String embeddingModel = "text-embedding-v2";

    private int embeddingDimensions = 1536;

    private boolean adminRebuildEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public boolean isAdminRebuildEnabled() {
        return adminRebuildEnabled;
    }

    public void setAdminRebuildEnabled(boolean adminRebuildEnabled) {
        this.adminRebuildEnabled = adminRebuildEnabled;
    }
}
