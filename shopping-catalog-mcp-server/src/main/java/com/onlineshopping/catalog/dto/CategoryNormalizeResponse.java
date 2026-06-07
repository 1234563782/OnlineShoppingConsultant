package com.onlineshopping.catalog.dto;

public class CategoryNormalizeResponse {

    private String categoryId;
    private String categoryName;
    private String categoryRaw;
    private double confidence;
    private String status;
    private String matchedBy;

    public static CategoryNormalizeResponse resolved(
            String categoryId,
            String categoryName,
            String categoryRaw,
            double confidence,
            String matchedBy
    ) {
        CategoryNormalizeResponse response = new CategoryNormalizeResponse();
        response.categoryId = categoryId;
        response.categoryName = categoryName;
        response.categoryRaw = categoryRaw;
        response.confidence = confidence;
        response.status = "RESOLVED";
        response.matchedBy = matchedBy;
        return response;
    }

    public static CategoryNormalizeResponse unresolved(String categoryRaw) {
        CategoryNormalizeResponse response = new CategoryNormalizeResponse();
        response.categoryRaw = categoryRaw;
        response.confidence = 0.0;
        response.status = "UNRESOLVED";
        response.matchedBy = "none";
        return response;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryRaw() {
        return categoryRaw;
    }

    public void setCategoryRaw(String categoryRaw) {
        this.categoryRaw = categoryRaw;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMatchedBy() {
        return matchedBy;
    }

    public void setMatchedBy(String matchedBy) {
        this.matchedBy = matchedBy;
    }
}
