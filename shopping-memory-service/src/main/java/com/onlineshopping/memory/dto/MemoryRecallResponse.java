package com.onlineshopping.memory.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryRecallResponse {

    private Map<String, Object> profileSegments = new LinkedHashMap<>();
    private List<String> recalledKeys = List.of();

    public Map<String, Object> getProfileSegments() {
        return profileSegments;
    }

    public void setProfileSegments(Map<String, Object> profileSegments) {
        this.profileSegments = profileSegments == null ? new LinkedHashMap<>() : profileSegments;
    }

    public List<String> getRecalledKeys() {
        return recalledKeys;
    }

    public void setRecalledKeys(List<String> recalledKeys) {
        this.recalledKeys = recalledKeys == null ? List.of() : recalledKeys;
    }
}
