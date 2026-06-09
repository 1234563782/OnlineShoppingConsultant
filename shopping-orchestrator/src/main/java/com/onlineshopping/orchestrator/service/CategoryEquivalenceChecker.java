package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class CategoryEquivalenceChecker {

    private final CategoryClientService categoryClientService;

    public CategoryEquivalenceChecker(CategoryClientService categoryClientService) {
        this.categoryClientService = categoryClientService;
    }

    public boolean isSameCategory(Object left, Object right) {
        String leftText = SessionContextSupport.stringValue(left);
        String rightText = SessionContextSupport.stringValue(right);
        if (leftText == null || rightText == null) {
            return false;
        }
        if (leftText.equalsIgnoreCase(rightText)) {
            return true;
        }
        if (SessionContextSupport.textsOverlap(leftText, rightText)) {
            return true;
        }
        return sameNormalizedCategoryId(leftText, rightText);
    }

    public boolean isSameCategoryAsSession(Map<String, Object> sessionContext, String newCategoryRaw) {
        if (sessionContext == null || !SessionContextSupport.hasValue(newCategoryRaw)) {
            return false;
        }
        String currentCategoryId = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_ID));
        if (currentCategoryId != null) {
            String newCategoryId = normalizedCategoryId(newCategoryRaw.trim());
            if (newCategoryId != null) {
                return currentCategoryId.equalsIgnoreCase(newCategoryId);
            }
        }
        String currentLabel = SessionContextSupport.categoryLabel(sessionContext);
        return isSameCategory(currentLabel, newCategoryRaw);
    }

    private boolean sameNormalizedCategoryId(String leftText, String rightText) {
        String leftId = normalizedCategoryId(leftText);
        String rightId = normalizedCategoryId(rightText);
        return leftId != null && leftId.equalsIgnoreCase(rightId);
    }

    private String normalizedCategoryId(String raw) {
        Map<String, Object> normalized = categoryClientService.normalize(raw);
        if (!CategoryResolutionResult.STATUS_RESOLVED.equals(
                SessionContextSupport.stringValue(normalized.get("status")))) {
            return null;
        }
        return SessionContextSupport.stringValue(normalized.get("categoryId"));
    }
}
