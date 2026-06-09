package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
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

        String currentRaw = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_RAW));
        String resolvedRaw = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.RESOLVED_CATEGORY_RAW));
        String categoryId = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_ID));
        String categoryResolution = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_RESOLUTION));

        if (CategoryResolutionResult.STATUS_RESOLVED.equals(categoryResolution)
                && categoryId != null
                && canReuseResolvedCategory(currentRaw, resolvedRaw)) {
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_RESOLVED,
                    categoryId,
                    SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_NAME)),
                    currentRaw == null ? resolvedRaw : currentRaw,
                    doubleValue(sessionContext.get(SessionContextKeys.CATEGORY_CONFIDENCE)),
                    "confirmed"
            );
        }

        if (currentRaw == null || currentRaw.isBlank()) {
            sessionContext.remove(SessionContextKeys.CATEGORY_ID);
            sessionContext.remove(SessionContextKeys.CATEGORY_NAME);
            sessionContext.remove(SessionContextKeys.CATEGORY_CONFIDENCE);
            sessionContext.remove(SessionContextKeys.RESOLVED_CATEGORY_RAW);
            sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_SKIPPED);
            return CategoryResolutionResult.skipped();
        }

        String categoryRaw = currentRaw.trim();
        Map<String, Object> normalized = categoryClientService.normalize(categoryRaw);
        String status = SessionContextSupport.stringValue(normalized.get("status"));
        double confidence = doubleValue(normalized.get("confidence"));
        String normalizedCategoryId = SessionContextSupport.stringValue(normalized.get("categoryId"));
        String categoryName = SessionContextSupport.stringValue(normalized.get("categoryName"));
        String matchedBy = SessionContextSupport.stringValue(normalized.get("matchedBy"));

        sessionContext.put(SessionContextKeys.CATEGORY_RAW, categoryRaw);
        sessionContext.put(SessionContextKeys.CATEGORY_CONFIDENCE, confidence);

        if (CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE.equals(status)) {
            sessionContext.remove(SessionContextKeys.CATEGORY_ID);
            sessionContext.remove(SessionContextKeys.CATEGORY_NAME);
            sessionContext.remove(SessionContextKeys.RESOLVED_CATEGORY_RAW);
            sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE);
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE,
                    null,
                    null,
                    categoryRaw,
                    confidence,
                    matchedBy
            );
        }

        if (CategoryResolutionResult.STATUS_RESOLVED.equals(status)
                && normalizedCategoryId != null
                && confidence >= confidenceThreshold) {
            sessionContext.put(SessionContextKeys.CATEGORY_ID, normalizedCategoryId);
            sessionContext.put(SessionContextKeys.CATEGORY_NAME, categoryName);
            sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_RESOLVED);
            sessionContext.put(SessionContextKeys.RESOLVED_CATEGORY_RAW, categoryRaw);
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_RESOLVED,
                    normalizedCategoryId,
                    categoryName,
                    categoryRaw,
                    confidence,
                    matchedBy
            );
        }

        if (CategoryResolutionResult.STATUS_RESOLVED.equals(status) && normalizedCategoryId != null) {
            sessionContext.put(SessionContextKeys.CATEGORY_ID, normalizedCategoryId);
            sessionContext.put(SessionContextKeys.CATEGORY_NAME, categoryName);
            sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_LOW_CONFIDENCE);
            sessionContext.put(SessionContextKeys.RESOLVED_CATEGORY_RAW, categoryRaw);
            return new CategoryResolutionResult(
                    CategoryResolutionResult.STATUS_LOW_CONFIDENCE,
                    normalizedCategoryId,
                    categoryName,
                    categoryRaw,
                    confidence,
                    matchedBy
            );
        }

        sessionContext.remove(SessionContextKeys.CATEGORY_ID);
        sessionContext.remove(SessionContextKeys.CATEGORY_NAME);
        sessionContext.remove(SessionContextKeys.RESOLVED_CATEGORY_RAW);
        sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_UNRESOLVED);
        return new CategoryResolutionResult(
                CategoryResolutionResult.STATUS_UNRESOLVED,
                null,
                null,
                categoryRaw,
                confidence,
                matchedBy
        );
    }

    private boolean canReuseResolvedCategory(String currentRaw, String resolvedRaw) {
        if (resolvedRaw == null) {
            return currentRaw == null;
        }
        if (currentRaw == null) {
            return false;
        }
        return currentRaw.equalsIgnoreCase(resolvedRaw);
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
