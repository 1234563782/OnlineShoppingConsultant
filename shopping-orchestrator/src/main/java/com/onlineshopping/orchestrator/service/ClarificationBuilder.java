package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class ClarificationBuilder {

    public record Clarification(String message, String field) {
    }

    public Clarification buildIfNeeded(Map<String, Object> effectiveContext) {
        if (effectiveContext == null) {
            return null;
        }
        List<?> missingFields = effectiveContext.get("missingFields") instanceof List<?> list ? list : List.of();
        boolean userUncertain = Boolean.TRUE.equals(effectiveContext.get("userUncertain"));
        List<String> askedFields = normalizeStringList(effectiveContext.get(SessionContextKeys.ASKED_FIELDS));
        String categoryResolution = effectiveContext.get(SessionContextKeys.CATEGORY_RESOLUTION) == null
                ? ""
                : effectiveContext.get(SessionContextKeys.CATEGORY_RESOLUTION).toString();
        Object categoryRaw = effectiveContext.get(SessionContextKeys.CATEGORY_RAW);
        Object categoryName = effectiveContext.get(SessionContextKeys.CATEGORY_NAME);

        if (missingFields.contains("categoryConfirm") && !askedFields.contains("categoryConfirm")) {
            return new Clarification(
                    "您说的「%s」，是指「%s」这个品类吗？可以直接回复“是”或纠正我。"
                            .formatted(
                                    categoryRaw == null ? "这个商品" : categoryRaw,
                                    categoryName == null ? categoryRaw : categoryName
                            ),
                    "categoryConfirm"
            );
        }
        if (missingFields.contains("category")) {
            if (CategoryResolutionResult.STATUS_SERVICE_UNAVAILABLE.equals(categoryResolution)) {
                return new Clarification(
                        "类目服务暂时不可用，请稍后再试；你也可以直接说具体品类，如手机、耳机、电脑。",
                        "category"
                );
            }
            if (CategoryResolutionResult.STATUS_UNRESOLVED.equals(categoryResolution) && hasValue(categoryRaw)) {
                return new Clarification(
                        "我暂时没识别到「%s」对应的商品品类，能再说具体一点吗？比如手机、电脑、平板。"
                                .formatted(categoryRaw),
                        "category"
                );
            }
            return new Clarification(
                    "你想买什么品类或商品？可以直接说商品名、预算、使用场景和偏好。",
                    "category"
            );
        }
        Object category = categoryLabel(effectiveContext);
        boolean budgetUncertain = Boolean.TRUE.equals(effectiveContext.get(SessionContextKeys.BUDGET_UNCERTAIN));
        if (missingFields.contains("budget") && budgetUncertain && !askedFields.contains("budget")) {
            return new Clarification(
                    "收到，你想买%s。预算还没定的话，可以先告诉我一个大概上限；如果想先看看，也可以回复“先看看”，我会按不同价位给你推荐。"
                            .formatted(category),
                    "budget"
            );
        }
        if (missingFields.contains("scene") && !userUncertain && !askedFields.contains("scene")) {
            return new Clarification(
                    "这个%s主要用在什么场景？比如通勤、办公、学习、运动或游戏。"
                            .formatted(category),
                    "scene"
            );
        }
        return null;
    }

    public void applySessionMarkers(
            Map<String, Object> sessionContext,
            Map<String, Object> effectiveContext,
            Clarification clarification
    ) {
        if (sessionContext == null || clarification == null) {
            return;
        }
        String field = clarification.field();
        if (field == null || field.isBlank()) {
            sessionContext.remove(SessionContextKeys.PENDING_FIELD);
            sessionContext.remove(SessionContextKeys.PENDING_QUESTION);
            return;
        }
        LinkedHashSet<String> askedFields = new LinkedHashSet<>(normalizeStringList(sessionContext.get(SessionContextKeys.ASKED_FIELDS)));
        askedFields.add(field);
        sessionContext.put(SessionContextKeys.ASKED_FIELDS, new ArrayList<>(askedFields));
        sessionContext.put(SessionContextKeys.PENDING_FIELD, field);
        sessionContext.put(SessionContextKeys.PENDING_QUESTION, clarification.message());
    }

    private Object categoryLabel(Map<String, Object> context) {
        if (hasValue(context.get(SessionContextKeys.CATEGORY_NAME))) {
            return context.get(SessionContextKeys.CATEGORY_NAME);
        }
        if (hasValue(context.get(SessionContextKeys.CATEGORY_RAW))) {
            return context.get(SessionContextKeys.CATEGORY_RAW);
        }
        if (hasValue(context.get(SessionContextKeys.CATEGORY_ID))) {
            return context.get(SessionContextKeys.CATEGORY_ID);
        }
        return context.get("category");
    }

    private List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"null".equalsIgnoreCase(s);
        }
        return true;
    }
}
