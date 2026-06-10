package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.CategoryResolutionResult;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import com.onlineshopping.orchestrator.support.SessionContextSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects explicit category switches from user message and reconciles extraction patch.
 * Runs even when LLM already filled categoryRaw (which may incorrectly repeat the old session category).
 */
@Service
public class CategoryIntentDetector {

    private static final int MIN_TOKEN_LENGTH = 2;
    private static final int MAX_TOKEN_LENGTH = 8;

    /**
     * Matches switch phrases like「我想再看看电脑」「换成平板」「想买耳机」.
     * Group 1 is the category token to normalize.
     */
    private static final Pattern SWITCH_CATEGORY_PATTERN = Pattern.compile(
            "(?:再看看|换成|改看|想看|还是看|看看|推荐|想买|要买|买)([^\\s，。！？,.!?:;：；、]{1,8})"
    );

    private final CategoryClientService categoryClientService;
    private final CategoryEquivalenceChecker categoryEquivalenceChecker;

    public CategoryIntentDetector(
            CategoryClientService categoryClientService,
            CategoryEquivalenceChecker categoryEquivalenceChecker
    ) {
        this.categoryClientService = categoryClientService;
        this.categoryEquivalenceChecker = categoryEquivalenceChecker;
    }

    /**
     * Force patch categoryRaw when user message indicates a different category than current session.
     */
    public void reconcileCategoryPatch(String userMessage, Map<String, Object> sessionContext, Map<String, Object> patch) {
        if (patch == null) {
            return;
        }
        detectCategoryRaw(userMessage, sessionContext).ifPresent(detected -> {
            patch.put(SessionContextKeys.CATEGORY_RAW, detected);
            patch.put(SessionContextKeys.CATEGORY_SOURCE, SessionContextKeys.CATEGORY_SOURCE_RULE);
            patch.put(SessionContextKeys.INTENT_TYPE, "shopping");
        });
    }

    public boolean isSameCategoryAsSession(Map<String, Object> sessionContext, String categoryRaw) {
        return categoryEquivalenceChecker.isSameCategoryAsSession(sessionContext, categoryRaw);
    }

    public boolean isCategorySupportedByUserMessage(
            String userMessage,
            String categoryRaw,
            Map<String, Object> sessionContext
    ) {
        String message = SessionContextSupport.stringValue(userMessage);
        String candidate = SessionContextSupport.stringValue(categoryRaw);
        if (message == null || candidate == null) {
            return false;
        }
        if (SessionContextSupport.textsOverlap(message, candidate)) {
            return true;
        }
        return detectCategoryRaw(message, sessionContext)
                .map(detected -> categoryEquivalenceChecker.isSameCategory(detected, candidate))
                .orElse(false);
    }

    public Optional<String> detectCategoryRaw(String userMessage, Map<String, Object> sessionContext) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String message = userMessage.trim();
        Optional<DetectedCategory> best = detectFromSwitchPhrase(message, sessionContext);
        if (best.isEmpty()) {
            best = selectBestCandidate(message, sessionContext);
        }
        if (best.isEmpty()) {
            best = selectBestFromSubstrings(message, sessionContext);
        }
        return best.map(DetectedCategory::label);
    }

    private Optional<DetectedCategory> detectFromSwitchPhrase(String message, Map<String, Object> sessionContext) {
        Matcher matcher = SWITCH_CATEGORY_PATTERN.matcher(message);
        DetectedCategory best = null;
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            if (candidate.length() < MIN_TOKEN_LENGTH) {
                continue;
            }
            Optional<DetectedCategory> detected = toDetectedCategory(
                    categoryClientService.normalize(candidate),
                    candidate,
                    sessionContext
            );
            if (detected.isPresent() && isBetter(detected.get(), best)) {
                best = detected.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<DetectedCategory> selectBestCandidate(String text, Map<String, Object> sessionContext) {
        return toDetectedCategory(categoryClientService.normalize(text), text, sessionContext);
    }

    private Optional<DetectedCategory> selectBestFromSubstrings(String message, Map<String, Object> sessionContext) {
        DetectedCategory best = null;
        Set<String> seen = new LinkedHashSet<>();
        for (int start = 0; start < message.length(); start++) {
            int maxLen = Math.min(MAX_TOKEN_LENGTH, message.length() - start);
            for (int len = maxLen; len >= MIN_TOKEN_LENGTH; len--) {
                String token = message.substring(start, start + len).trim();
                if (token.length() < MIN_TOKEN_LENGTH || !isLikelyCategoryToken(token) || !seen.add(token)) {
                    continue;
                }
                Optional<DetectedCategory> candidate = toDetectedCategory(
                        categoryClientService.normalize(token),
                        token,
                        sessionContext
                );
                if (candidate.isPresent() && isBetter(candidate.get(), best)) {
                    best = candidate.get();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<DetectedCategory> toDetectedCategory(
            Map<String, Object> normalized,
            String sourceText,
            Map<String, Object> sessionContext
    ) {
        if (!CategoryResolutionResult.STATUS_RESOLVED.equals(
                SessionContextSupport.stringValue(normalized.get("status")))) {
            return Optional.empty();
        }
        String label = preferredRaw(normalized);
        if (!SessionContextSupport.hasValue(label)) {
            return Optional.empty();
        }
        if (categoryEquivalenceChecker.isSameCategoryAsSession(sessionContext, label)) {
            return Optional.empty();
        }
        double confidence = confidence(normalized);
        return Optional.of(new DetectedCategory(label, confidence, sourceText));
    }

    private boolean isBetter(DetectedCategory candidate, DetectedCategory currentBest) {
        if (currentBest == null) {
            return true;
        }
        if (candidate.confidence() != currentBest.confidence()) {
            return candidate.confidence() > currentBest.confidence();
        }
        return candidate.label().length() > currentBest.label().length();
    }

    private boolean isLikelyCategoryToken(String token) {
        if (token.isBlank()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (Character.isLetter(ch) || Character.isDigit(ch)) {
                return true;
            }
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private double confidence(Map<String, Object> normalized) {
        Object value = normalized.get("confidence");
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

    private String preferredRaw(Map<String, Object> normalized) {
        String categoryName = SessionContextSupport.stringValue(normalized.get("categoryName"));
        if (categoryName != null) {
            return categoryName;
        }
        return SessionContextSupport.stringValue(normalized.get("categoryRaw"));
    }

    private record DetectedCategory(String label, double confidence, String sourceText) {
    }
}
