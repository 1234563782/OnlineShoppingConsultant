package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CategoryPatchGuard {

    private final CategoryIntentDetector categoryIntentDetector;

    public CategoryPatchGuard(CategoryIntentDetector categoryIntentDetector) {
        this.categoryIntentDetector = categoryIntentDetector;
    }

    public void removeUnsupportedCategoryReplace(
            String userMessage,
            Map<String, Object> currentSessionContext,
            Map<String, Object> extractedPatch
    ) {
        if (extractedPatch == null || !hasSessionCategory(currentSessionContext)) {
            return;
        }
        String candidate = SessionContextSupport.stringValue(extractedPatch.get(SessionContextKeys.CATEGORY_RAW));
        if (candidate == null) {
            return;
        }
        String source = SessionContextSupport.stringValue(extractedPatch.get(SessionContextKeys.CATEGORY_SOURCE));
        if (SessionContextKeys.CATEGORY_SOURCE_RULE.equalsIgnoreCase(source)) {
            return;
        }
        if (categoryIntentDetector.isSameCategoryAsSession(currentSessionContext, candidate)) {
            return;
        }
        if (categoryIntentDetector.isCategorySupportedByUserMessage(userMessage, candidate, currentSessionContext)) {
            return;
        }

        extractedPatch.remove(SessionContextKeys.CATEGORY_RAW);
        extractedPatch.remove(SessionContextKeys.CATEGORY_SOURCE);
    }

    private boolean hasSessionCategory(Map<String, Object> sessionContext) {
        return SessionContextSupport.hasValue(SessionContextSupport.categoryLabel(sessionContext));
    }
}
