package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.SessionProcessResult;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import com.onlineshopping.orchestrator.support.SessionContextKeys;
import org.springframework.stereotype.Service;

@Service
public class TurnOutcomeResolver {

    private final ClarificationBuilder clarificationBuilder;
    private final SmallTalkReplyBuilder smallTalkReplyBuilder;
    private final CompareTargetResolver compareTargetResolver;

    public TurnOutcomeResolver(
            ClarificationBuilder clarificationBuilder,
            SmallTalkReplyBuilder smallTalkReplyBuilder,
            CompareTargetResolver compareTargetResolver
    ) {
        this.clarificationBuilder = clarificationBuilder;
        this.smallTalkReplyBuilder = smallTalkReplyBuilder;
        this.compareTargetResolver = compareTargetResolver;
    }

    public TurnDecision resolve(SessionProcessResult processed, String userMessage) {
        boolean categoryReplaced = processed.categoryReplaced();
        String categoryReplaceReason = processed.categoryReplaceReason() == null
                ? ""
                : processed.categoryReplaceReason();
        String intentType = processed.intentType() == null ? "shopping" : processed.intentType();

        if ("small_talk".equalsIgnoreCase(intentType)) {
            return new TurnDecision(
                    TurnOutcome.SMALL_TALK,
                    categoryReplaced,
                    categoryReplaceReason,
                    smallTalkReplyBuilder.build(userMessage),
                    null,
                    null
            );
        }
        if ("non_shopping".equalsIgnoreCase(intentType)) {
            return new TurnDecision(
                    TurnOutcome.NON_SHOPPING,
                    categoryReplaced,
                    categoryReplaceReason,
                    smallTalkReplyBuilder.build(userMessage),
                    null,
                    null
            );
        }

        if (isCompareIntent(processed)) {
            CompareTargetResolver.ResolvedCompareTargets targets = compareTargetResolver.resolve(
                    processed.sessionContext(),
                    processed.extractedPatch(),
                    userMessage
            );
            if (targets.skuIds().size() < 2) {
                return new TurnDecision(
                        TurnOutcome.NEED_CLARIFICATION,
                        categoryReplaced,
                        categoryReplaceReason,
                        null,
                        "请告诉我你想对比的具体商品，例如「小米 14 和 iPhone 15 哪个好」，或者说「第一款和第二款哪个划算」。",
                        "compareTargets"
                );
            }
            return new TurnDecision(
                    TurnOutcome.READY_FOR_AGENT,
                    categoryReplaced,
                    categoryReplaceReason,
                    null,
                    null,
                    null
            );
        }

        ClarificationBuilder.Clarification clarification = clarificationBuilder.buildIfNeeded(processed.effectiveContext());
        if (clarification != null) {
            return new TurnDecision(
                    TurnOutcome.NEED_CLARIFICATION,
                    categoryReplaced,
                    categoryReplaceReason,
                    null,
                    clarification.message(),
                    clarification.field()
            );
        }

        return new TurnDecision(
                TurnOutcome.READY_FOR_AGENT,
                categoryReplaced,
                categoryReplaceReason,
                null,
                null,
                null
        );
    }

    private boolean isCompareIntent(SessionProcessResult processed) {
        if (processed.sessionContext() == null) {
            return false;
        }
        Object value = processed.sessionContext().get(SessionContextKeys.SHOPPING_SUB_INTENT);
        return SessionContextKeys.SUB_INTENT_COMPARE.equalsIgnoreCase(
                value == null ? "" : value.toString()
        );
    }
}
