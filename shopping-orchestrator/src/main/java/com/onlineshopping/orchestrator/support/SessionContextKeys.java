package com.onlineshopping.orchestrator.support;

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
    public static final String BUDGET = "budget";
    public static final String SCENE = "scene";
    public static final String MUST_HAVE = "mustHave";
    public static final String PENDING_FIELD = "pendingField";
    public static final String PENDING_QUESTION = "pendingQuestion";
    public static final String ASKED_FIELDS = "askedFields";

    private SessionContextKeys() {
    }
}
