package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.dto.MergeSessionResult;
import com.onlineshopping.orchestrator.dto.SessionProcessResult;
import com.onlineshopping.orchestrator.dto.SlotProcessResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SessionStateMachine {

    private final ContextExtractionService contextExtractionService;
    private final CategoryPatchNormalizer categoryPatchNormalizer;
    private final CategoryIntentDetector categoryIntentDetector;
    private final CategoryPatchGuard categoryPatchGuard;
    private final BrandIntentDetector brandIntentDetector;
    private final CompareIntentDetector compareIntentDetector;
    private final CompareTargetResolver compareTargetResolver;
    private final ContextMergeService contextMergeService;
    private final CategoryResolutionService categoryResolutionService;

    public SessionStateMachine(
            ContextExtractionService contextExtractionService,
            CategoryPatchNormalizer categoryPatchNormalizer,
            CategoryIntentDetector categoryIntentDetector,
            CategoryPatchGuard categoryPatchGuard,
            BrandIntentDetector brandIntentDetector,
            CompareIntentDetector compareIntentDetector,
            CompareTargetResolver compareTargetResolver,
            ContextMergeService contextMergeService,
            CategoryResolutionService categoryResolutionService
    ) {
        this.contextExtractionService = contextExtractionService;
        this.categoryPatchNormalizer = categoryPatchNormalizer;
        this.categoryIntentDetector = categoryIntentDetector;
        this.categoryPatchGuard = categoryPatchGuard;
        this.brandIntentDetector = brandIntentDetector;
        this.compareIntentDetector = compareIntentDetector;
        this.compareTargetResolver = compareTargetResolver;
        this.contextMergeService = contextMergeService;
        this.categoryResolutionService = categoryResolutionService;
    }

    public SessionProcessResult process(
            String userMessage,
            Map<String, Object> currentSessionContext,
            Map<String, Object> profile
    ) {
        SlotProcessResult slots = processSlots(userMessage, currentSessionContext);
        return finalizeWithProfile(slots, profile, Map.of());
    }

    public SlotProcessResult processSlots(String userMessage, Map<String, Object> currentSessionContext) {
        Map<String, Object> sessionBefore = snapshotCategoryFields(currentSessionContext);
        String pendingField = pendingField(currentSessionContext);
        Map<String, Object> extractedPatch = pendingField == null
                ? contextExtractionService.extractPatch(userMessage, currentSessionContext)
                : contextExtractionService.extractPendingFieldPatch(
                pendingField, userMessage, currentSessionContext);

        categoryPatchNormalizer.normalize(userMessage, currentSessionContext, extractedPatch);
        compareIntentDetector.reconcileComparePatch(userMessage, extractedPatch, currentSessionContext);
        categoryIntentDetector.reconcileCategoryPatch(userMessage, currentSessionContext, extractedPatch);
        categoryPatchGuard.removeUnsupportedCategoryReplace(userMessage, currentSessionContext, extractedPatch);
        brandIntentDetector.reconcileBrandPatch(userMessage, extractedPatch);
        markExplicitBudgetUncertainty(userMessage, extractedPatch);

        MergeSessionResult mergeResult = contextMergeService.mergeSessionPatch(
                currentSessionContext,
                extractedPatch
        );
        Map<String, Object> sessionContext = mergeResult.sessionContext();

        applyPendingFieldResult(sessionContext, pendingField, extractedPatch, userMessage);
        applyCategoryConfirmation(userMessage, pendingField, sessionContext);
        applyCompareProductCategory(sessionContext, extractedPatch, userMessage);

        CategoryResolutionResult categoryResolution = categoryResolutionService.resolve(sessionContext);
        return new SlotProcessResult(
                sessionBefore,
                extractedPatch,
                sessionContext,
                categoryResolution,
                mergeResult.categoryReplaced(),
                mergeResult.categoryReplaceReason()
        );
    }

    public SessionProcessResult finalizeWithProfile(
            SlotProcessResult slots,
            Map<String, Object> profile,
            Map<String, Object> memoryDebug
    ) {
        Map<String, Object> sessionContext = slots.sessionContext();
        Map<String, Object> effectiveContext = contextMergeService.buildEffectiveContext(
                sessionContext,
                profile == null ? Map.of() : profile,
                true,
                !slots.categoryReplaced()
        );
        effectiveContext.put(
                SessionContextKeys.CATEGORY_RESOLUTION,
                sessionContext.getOrDefault(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_SKIPPED)
        );
        if (sessionContext.get(SessionContextKeys.CATEGORY_CONFIDENCE) != null) {
            effectiveContext.put(SessionContextKeys.CATEGORY_CONFIDENCE, sessionContext.get(SessionContextKeys.CATEGORY_CONFIDENCE));
        }

        String intentType = String.valueOf(effectiveContext.getOrDefault(SessionContextKeys.INTENT_TYPE, "shopping"));
        Map<String, Object> stateDebug = buildStateDebug(
                slots.sessionBefore(),
                sessionContext,
                slots.extractedPatch(),
                slots.categoryReplaced(),
                slots.categoryReplaceReason(),
                effectiveContext,
                memoryDebug
        );

        return new SessionProcessResult(
                slots.extractedPatch(),
                sessionContext,
                effectiveContext,
                intentType,
                slots.categoryResolution(),
                slots.categoryReplaced(),
                slots.categoryReplaceReason(),
                stateDebug
        );
    }

    private Map<String, Object> buildStateDebug(
            Map<String, Object> sessionBefore,
            Map<String, Object> sessionAfter,
            Map<String, Object> extractedPatch,
            boolean categoryReplaced,
            String categoryReplaceReason,
            Map<String, Object> effectiveContext,
            Map<String, Object> memoryDebug
    ) {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("extractedPatch", extractedPatch);
        debug.put("categoryReplaced", categoryReplaced);
        debug.put("categoryReplaceReason", categoryReplaceReason == null ? "" : categoryReplaceReason);
        debug.put("resolvedConstraints", effectiveContext.get("resolvedConstraints"));
        debug.put("sessionContextBefore", sessionBefore);
        debug.put("sessionContextAfter", snapshotCategoryFields(sessionAfter));
        if (memoryDebug != null && !memoryDebug.isEmpty()) {
            debug.put("memoryRecall", memoryDebug);
        }
        return debug;
    }

    private void applyCompareProductCategory(
            Map<String, Object> sessionContext,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (sessionContext == null) {
            return;
        }
        Object subIntent = sessionContext.get(SessionContextKeys.SHOPPING_SUB_INTENT);
        if (!SessionContextKeys.SUB_INTENT_COMPARE.equalsIgnoreCase(subIntent == null ? "" : subIntent.toString())) {
            return;
        }
        compareTargetResolver.resolve(sessionContext, extractedPatch, userMessage);
    }

    private Map<String, Object> snapshotCategoryFields(Map<String, Object> sessionContext) {
        Map<String, Object> snapshot = new HashMap<>();
        if (sessionContext == null) {
            return snapshot;
        }
        snapshot.put(SessionContextKeys.CATEGORY_RAW, sessionContext.get(SessionContextKeys.CATEGORY_RAW));
        snapshot.put(SessionContextKeys.CATEGORY_ID, sessionContext.get(SessionContextKeys.CATEGORY_ID));
        snapshot.put(SessionContextKeys.CATEGORY_NAME, sessionContext.get(SessionContextKeys.CATEGORY_NAME));
        snapshot.put(SessionContextKeys.CATEGORY_RESOLUTION, sessionContext.get(SessionContextKeys.CATEGORY_RESOLUTION));
        snapshot.put(SessionContextKeys.RESOLVED_CATEGORY_RAW, sessionContext.get(SessionContextKeys.RESOLVED_CATEGORY_RAW));
        snapshot.put(SessionContextKeys.CATEGORY_SOURCE, sessionContext.get(SessionContextKeys.CATEGORY_SOURCE));
        return snapshot;
    }

    private String pendingField(Map<String, Object> sessionContext) {
        if (sessionContext == null || !SessionContextSupport.hasValue(sessionContext.get(SessionContextKeys.PENDING_FIELD))) {
            return null;
        }
        return sessionContext.get(SessionContextKeys.PENDING_FIELD).toString();
    }

    private void applyPendingFieldResult(
            Map<String, Object> sessionContext,
            String pendingField,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (pendingField == null || sessionContext == null) {
            return;
        }
        boolean answered = Boolean.TRUE.equals(extractedPatch.get("answeredPendingField"));
        boolean userUncertain = Boolean.TRUE.equals(extractedPatch.get("userUncertain"));
        boolean categoryChanged = SessionContextSupport.hasValue(extractedPatch.get(SessionContextKeys.CATEGORY_RAW))
                && !"category".equalsIgnoreCase(pendingField)
                && !"categoryConfirm".equalsIgnoreCase(pendingField);
        boolean categoryConfirmed = "categoryConfirm".equalsIgnoreCase(pendingField) && isAffirmativeReply(userMessage);
        if (answered || userUncertain || categoryChanged || categoryConfirmed
                || Boolean.FALSE.equals(extractedPatch.get("shouldKeepPending"))) {
            sessionContext.remove(SessionContextKeys.PENDING_FIELD);
            sessionContext.remove(SessionContextKeys.PENDING_QUESTION);
            return;
        }
        sessionContext.put(SessionContextKeys.PENDING_FIELD, pendingField);
    }

    private void markExplicitBudgetUncertainty(String userMessage, Map<String, Object> extractedPatch) {
        if (extractedPatch == null) {
            return;
        }
        if (hasBudgetValue(extractedPatch.get(SessionContextKeys.BUDGET))) {
            extractedPatch.put(SessionContextKeys.BUDGET_UNCERTAIN, false);
            return;
        }
        extractedPatch.put(SessionContextKeys.BUDGET_UNCERTAIN, explicitlyUnsureAboutBudget(userMessage));
    }

    private boolean explicitlyUnsureAboutBudget(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.trim().toLowerCase(Locale.ROOT);
        boolean mentionsBudget = text.contains("预算")
                || text.contains("价位")
                || text.contains("价格")
                || text.contains("多少钱")
                || text.contains("花多少");
        if (!mentionsBudget) {
            return false;
        }
        return text.contains("没想好")
                || text.contains("没定")
                || text.contains("不确定")
                || text.contains("先不定")
                || text.contains("没考虑")
                || text.contains("不知道")
                || text.contains("不清楚");
    }

    private boolean hasBudgetValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return false;
        }
        return SessionContextSupport.hasValue(map.get("min")) || SessionContextSupport.hasValue(map.get("max"));
    }

    private void applyCategoryConfirmation(
            String userMessage,
            String pendingField,
            Map<String, Object> sessionContext
    ) {
        if (sessionContext == null) {
            return;
        }
        if ("categoryConfirm".equalsIgnoreCase(pendingField) && isAffirmativeReply(userMessage)) {
            sessionContext.put(SessionContextKeys.CATEGORY_RESOLUTION, CategoryResolutionResult.STATUS_RESOLVED);
            String categoryRaw = SessionContextSupport.stringValue(sessionContext.get(SessionContextKeys.CATEGORY_RAW));
            if (categoryRaw != null) {
                sessionContext.put(SessionContextKeys.RESOLVED_CATEGORY_RAW, categoryRaw);
            }
        }
    }

    private boolean isAffirmativeReply(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        String text = userMessage.trim().toLowerCase(Locale.ROOT);
        return text.equals("是")
                || text.equals("对")
                || text.equals("嗯")
                || text.equals("yes")
                || text.equals("y")
                || text.contains("没错")
                || text.contains("是的")
                || text.contains("对的");
    }
}
