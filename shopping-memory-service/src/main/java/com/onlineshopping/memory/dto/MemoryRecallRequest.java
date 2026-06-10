package com.onlineshopping.memory.dto;

import java.util.ArrayList;
import java.util.List;

public class MemoryRecallRequest {

    private String query;
    private int topK = 5;
    private List<String> excludeKeys = new ArrayList<>();

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public List<String> getExcludeKeys() {
        return excludeKeys;
    }

    public void setExcludeKeys(List<String> excludeKeys) {
        this.excludeKeys = excludeKeys == null ? new ArrayList<>() : excludeKeys;
    }
}
