package com.onlineshopping.memory.dto;

import java.time.Instant;
import java.util.Map;

public class MemoryResponse {

    private String userId;
    private Map<String, Object> profileJson;
    private String summaryMd;
    private Instant updatedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, Object> getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(Map<String, Object> profileJson) {
        this.profileJson = profileJson;
    }

    public String getSummaryMd() {
        return summaryMd;
    }

    public void setSummaryMd(String summaryMd) {
        this.summaryMd = summaryMd;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
