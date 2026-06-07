package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CategoryResolutionService {

    private final CategoryClientService categoryClientService;

    @Value("${shopping.catalog.confidence-threshold:0.85}")
    private double confidenceThreshold;

    public CategoryResolutionService(CategoryClientService categoryClientService) {
        this.categoryClientService = categoryClientService;
    }

    /**
     * Resolves categoryRaw to categoryId in sessionContext (Orchestrator-owned normalization).
     */
    public CategoryResolutionResult resolve(Map<String, Object> sessionContext) {
        if (sessionContext == null) {
            return CategoryResolutionResult.skipped();
        }
        if (CategoryResolutionResult.STATUS_RESOLVED.equals(stringValue(sessionContext.get("categoryResolution")))
                && stringValue(sessionContext.get("categoryId")) != null) {
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_RESOLVED,
                    stringValue(sessionContext.get("categoryId")),
                    stringValue(sessionContext.get("categoryName")),
                    stringValue(sessionContext.get("categoryRaw")),
                    doubleValue(sessionContext.get("categoryConfidence")),
                    "confirmed"
            );
        }
        Object rawValue = sessionContext.get("categoryRaw");
        if (rawValue == null || rawValue.toString().isBlank()) {
            sessionContext.remove("categoryId");
            sessionContext.remove("categoryName");
            sessionContext.remove("categoryConfidence");
            sessionContext.put("categoryResolution", CategoryResolutionResult.STATUS_SKIPPED);
            return CategoryResolutionResult.skipped();
        }

        String categoryRaw = rawValue.toString().trim();
        Map<String, Object> normalized = categoryClientService.normalize(categoryRaw);
        String status = stringValue(normalized.get("status"));
        double confidence = doubleValue(normalized.get("confidence"));
        String categoryId = stringValue(normalized.get("categoryId"));
        String categoryName = stringValue(normalized.get("categoryName"));
        String matchedBy = stringValue(normalized.get("matchedBy"));

        sessionContext.put("categoryRaw", categoryRaw);
        sessionContext.put("categoryConfidence", confidence);

        if (CategoryResolutionResult.STATUS_RESOLVED.equals(status)
                && categoryId != null
                && confidence >= confidenceThreshold) {
            sessionContext.put("categoryId", categoryId);
            sessionContext.put("categoryName", categoryName);
            sessionContext.put("categoryResolution", CategoryResolutionResult.STATUS_RESOLVED);
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_RESOLVED,
                    categoryId,
                    categoryName,
                    categoryRaw,
                    confidence,
                    matchedBy
            );
        }

        if (CategoryResolutionResult.STATUS_RESOLVED.equals(status) && categoryId != null) {
            sessionContext.put("categoryId", categoryId);
            sessionContext.put("categoryName", categoryName);
            sessionContext.put("categoryResolution", CategoryResolutionResult.STATUS_LOW_CONFIDENCE);
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_LOW_CONFIDENCE,
                    categoryId,
                    categoryName,
                    categoryRaw,
                    confidence,
                    matchedBy
            );
        }

        sessionContext.remove("categoryId");
        sessionContext.remove("categoryName");
        sessionContext.put("categoryResolution", CategoryResolutionResult.STATUS_UNRESOLVED);
        return new CategoryResolutionResult(
                CategoryResolutionResult.STATUS_UNRESOLVED,
                null,
                null,
                categoryRaw,
                confidence,
                matchedBy
        );
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isBlank() ? null : text;
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
