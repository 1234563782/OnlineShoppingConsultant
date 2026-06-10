package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.SessionProcessResult;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class UserInputProcessor {

    private final SessionStoreService sessionStoreService;
    private final MemoryClientService memoryClientService;
    private final SessionStateMachine sessionStateMachine;
    private final TurnOutcomeResolver turnOutcomeResolver;
    private final ClarificationBuilder clarificationBuilder;

    public UserInputProcessor(
            SessionStoreService sessionStoreService,
            MemoryClientService memoryClientService,
            SessionStateMachine sessionStateMachine,
            TurnOutcomeResolver turnOutcomeResolver,
            ClarificationBuilder clarificationBuilder
    ) {
        this.sessionStoreService = sessionStoreService;
        this.memoryClientService = memoryClientService;
        this.sessionStateMachine = sessionStateMachine;
        this.turnOutcomeResolver = turnOutcomeResolver;
        this.clarificationBuilder = clarificationBuilder;
    }

    public ChatPreparedContext process(String userId, String sessionId, String message) {
        String resolvedSessionId = resolveSessionId(sessionId);
        SessionState sessionState = sessionStoreService.getSession(userId, resolvedSessionId);
        Map<String, Object> profile = memoryClientService.recall(userId, message);

        SessionProcessResult processed = sessionStateMachine.process(
                message,
                sessionState.getSessionContext(),
                profile
        );
        sessionState.setSessionContext(processed.sessionContext());

        TurnDecision decision = turnOutcomeResolver.resolve(processed, message);
        if (decision.outcome() == TurnOutcome.NEED_CLARIFICATION) {
            clarificationBuilder.applySessionMarkers(
                    processed.sessionContext(),
                    processed.effectiveContext(),
                    new ClarificationBuilder.Clarification(decision.clarification(), decision.clarificationField())
            );
            sessionState.setSessionContext(processed.sessionContext());
        }

        sessionStoreService.saveSession(userId, resolvedSessionId, sessionState);

        return new ChatPreparedContext(
                userId,
                resolvedSessionId,
                sessionState,
                profile,
                processed.extractedPatch(),
                processed.sessionContext(),
                processed.effectiveContext(),
                processed.intentType(),
                processed.categoryResolution(),
                processed.stateDebug(),
                decision
        );
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }
}
