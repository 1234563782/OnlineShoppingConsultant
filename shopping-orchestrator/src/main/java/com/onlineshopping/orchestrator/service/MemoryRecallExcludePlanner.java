package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds recall excludeKeys from session slots already filled this turn.
 * Avoids re-injecting profile segments that duplicate active session context.
 */
@Service
public class MemoryRecallExcludePlanner {

    private static final String SEGMENT_BUDGET = "budget";
    private static final String SEGMENT_BRANDS = "brands";
    private static final String SEGMENT_SCENE = "scene";
    private static final String SEGMENT_NOTES = "notes";

    public List<String> plan(Map<String, Object> sessionContext) {
        List<String> exclude = new ArrayList<>();
        if (sessionContext == null) {
            return exclude;
        }
        if (hasSessionBudget(sessionContext)) {
            exclude.add(SEGMENT_BUDGET);
        }
        if (hasSessionBrands(sessionContext)) {
            exclude.add(SEGMENT_BRANDS);
        }
        if (SessionContextSupport.hasValue(sessionContext.get(SessionContextKeys.SCENE))) {
            exclude.add(SEGMENT_SCENE);
        }
        if (hasSessionNotes(sessionContext)) {
            exclude.add(SEGMENT_NOTES);
        }
        return exclude;
    }

    private boolean hasSessionBudget(Map<String, Object> sessionContext) {
        Object budget = sessionContext.get(SessionContextKeys.BUDGET);
        if (!(budget instanceof Map<?, ?> budgetMap)) {
            return false;
        }
        return SessionContextSupport.hasValue(budgetMap.get("min"))
                || SessionContextSupport.hasValue(budgetMap.get("max"));
    }

    private boolean hasSessionBrands(Map<String, Object> sessionContext) {
        return !ProfileListNormalizer.normalizeList(sessionContext.get("brandPreferences")).isEmpty()
                || !ProfileListNormalizer.normalizeList(sessionContext.get("dislikes")).isEmpty();
    }

    private boolean hasSessionNotes(Map<String, Object> sessionContext) {
        return !ProfileListNormalizer.normalizeList(sessionContext.get("notes")).isEmpty();
    }
}
