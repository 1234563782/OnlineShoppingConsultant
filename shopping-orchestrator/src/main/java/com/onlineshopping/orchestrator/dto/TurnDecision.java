package com.onlineshopping.orchestrator.dto;

/**
 * Full routing decision for one turn: which path to take plus slot-change observability.
 */
public record TurnDecision(
        TurnOutcome outcome,
        boolean categoryReplaced,
        String categoryReplaceReason,
        String directReply,
        String clarification,
        String clarificationField
) {
    public boolean shouldInvokeAgent() {
        return outcome == TurnOutcome.READY_FOR_AGENT;
    }
}
