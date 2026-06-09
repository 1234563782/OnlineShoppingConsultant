package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CategoryPatchNormalizer {

    public void normalize(String userMessage, Map<String, Object> currentSessionContext, Map<String, Object> extractedPatch) {
        if (extractedPatch == null) {
            return;
        }
        if (!SessionContextSupport.hasValue(extractedPatch.get(SessionContextKeys.CATEGORY_RAW))
                && SessionContextSupport.hasValue(extractedPatch.get("category"))) {
            extractedPatch.put(SessionContextKeys.CATEGORY_RAW, extractedPatch.get("category"));
        }
        extractedPatch.remove("category");
        if (!SessionContextSupport.hasValue(extractedPatch.get(SessionContextKeys.CATEGORY_RAW))
                && SessionContextSupport.hasValue(extractedPatch.get(SessionContextKeys.CATEGORY_NAME))) {
            extractedPatch.put(SessionContextKeys.CATEGORY_RAW, extractedPatch.get(SessionContextKeys.CATEGORY_NAME));
        }
        extractedPatch.remove(SessionContextKeys.CATEGORY_ID);
        extractedPatch.remove(SessionContextKeys.CATEGORY_NAME);

        Object extractedCategoryRaw = extractedPatch.get(SessionContextKeys.CATEGORY_RAW);
        if (!SessionContextSupport.hasValue(extractedCategoryRaw)) {
            return;
        }
        extractedPatch.put(SessionContextKeys.INTENT_TYPE, "shopping");
        if (!SessionContextSupport.hasValue(extractedPatch.get(SessionContextKeys.CATEGORY_SOURCE))) {
            extractedPatch.put(SessionContextKeys.CATEGORY_SOURCE, "llm");
        }
    }
}
