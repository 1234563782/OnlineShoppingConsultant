package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.SessionProcessResult;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import org.springframework.stereotype.Service;

@Service
public class TurnOutcomeResolver {

    private final ClarificationBuilder clarificationBuilder;
    private final SmallTalkReplyBuilder smallTalkReplyBuilder;

    public TurnOutcomeResolver(
            ClarificationBuilder clarificationBuilder,
            SmallTalkReplyBuilder smallTalkReplyBuilder
    ) {
        this.clarificationBuilder = clarificationBuilder;
        this.smallTalkReplyBuilder = smallTalkReplyBuilder;
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
}
