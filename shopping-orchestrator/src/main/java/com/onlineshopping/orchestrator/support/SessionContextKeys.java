package com.onlineshopping.orchestrator.support;

import java.util.Set;

public final class SessionContextKeys {

    public static final String INTENT_TYPE = "intentType";
    public static final String CATEGORY_RAW = "categoryRaw";
    public static final String CATEGORY_ID = "categoryId";
    public static final String CATEGORY_NAME = "categoryName";
    public static final String CATEGORY_CONFIDENCE = "categoryConfidence";
    public static final String CATEGORY_RESOLUTION = "categoryResolution";
    public static final String RESOLVED_CATEGORY_RAW = "resolvedCategoryRaw";
    public static final String CATEGORY_UPDATED_AT = "categoryUpdatedAt";
    public static final String CATEGORY_SOURCE = "categorySource";
    public static final String CATEGORY_SOURCE_LLM = "llm";
    public static final String CATEGORY_SOURCE_RULE = "rule";
    public static final String BUDGET = "budget";
    public static final String SCENE = "scene";
    public static final String MUST_HAVE = "mustHave";
    public static final String PENDING_FIELD = "pendingField";
    public static final String PENDING_QUESTION = "pendingQuestion";
    public static final String ASKED_FIELDS = "askedFields";
    public static final String RECALLED_MEMORY_KEYS = "recalledMemoryKeys";
    public static final String SHOPPING_SUB_INTENT = "shoppingSubIntent";
    public static final String COMPARE_TARGETS = "compareTargets";
    public static final String COMPARE_FOCUS = "compareFocus";
    public static final String LAST_RECOMMENDATIONS = "lastRecommendations";

    public static final String SUB_INTENT_DISCOVER = "discover";
    public static final String SUB_INTENT_COMPARE = "compare";

    /**
     * Scratchpad fields: session-scoped slots written only by the orchestrator.
     * Consult Agent reads derived {@code resolvedConstraints}, not raw sessionContext.
     */
    public static final Set<String> SCRATCHPAD_KEYS = Set.of(
            INTENT_TYPE,
            CATEGORY_RAW,
            CATEGORY_ID,
            CATEGORY_NAME,
            CATEGORY_CONFIDENCE,
            CATEGORY_RESOLUTION,
            RESOLVED_CATEGORY_RAW,
            CATEGORY_UPDATED_AT,
            CATEGORY_SOURCE,
            BUDGET,
            SCENE,
            MUST_HAVE,
            PENDING_FIELD,
            PENDING_QUESTION,
            ASKED_FIELDS,
            RECALLED_MEMORY_KEYS,
            SHOPPING_SUB_INTENT,
            COMPARE_TARGETS,
            COMPARE_FOCUS,
            LAST_RECOMMENDATIONS
    );

    private SessionContextKeys() {
    }
}
