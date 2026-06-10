package com.onlineshopping.orchestrator.dto;

/**
 * Routing outcome for a single chat turn. Mutually exclusive — drives ChatController dispatch.
 * Category slot changes are tracked separately on {@link TurnDecision#categoryReplaced()}.
 */
public enum TurnOutcome {
    SMALL_TALK,
    NON_SHOPPING,
    NEED_CLARIFICATION,
    READY_FOR_AGENT
}
