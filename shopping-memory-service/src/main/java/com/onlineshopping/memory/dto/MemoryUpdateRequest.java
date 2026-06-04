package com.onlineshopping.memory.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public class MemoryUpdateRequest {

    @NotNull
    private Map<String, Object> profileJson;

    public Map<String, Object> getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(Map<String, Object> profileJson) {
        this.profileJson = profileJson;
    }
}
