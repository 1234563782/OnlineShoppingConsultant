package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.dto.ChatPreparedContext;
import com.onlineshopping.orchestrator.dto.MemoryRecallResult;
import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;
import com.onlineshopping.orchestrator.dto.SessionProcessResult;
import com.onlineshopping.orchestrator.dto.SessionState;
import com.onlineshopping.orchestrator.dto.SlotProcessResult;
import com.onlineshopping.orchestrator.dto.TurnDecision;
import com.onlineshopping.orchestrator.dto.TurnOutcome;
import com.onlineshopping.orchestrator.support.MemoryRecallSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserInputProcessor {

    private final SessionStoreService sessionStoreService;
    private final MemoryClientService memoryClientService;
    private final SessionStateMachine sessionStateMachine;
    private final TurnOutcomeResolver turnOutcomeResolver;
    private final ClarificationBuilder clarificationBuilder;
    private final MemoryRecallExcludePlanner memoryRecallExcludePlanner;
    private final CatalogSearchPrefetchService catalogSearchPrefetchService;

    public UserInputProcessor(
            SessionStoreService sessionStoreService,
            MemoryClientService memoryClientService,
            SessionStateMachine sessionStateMachine,
            TurnOutcomeResolver turnOutcomeResolver,
            ClarificationBuilder clarificationBuilder,
            MemoryRecallExcludePlanner memoryRecallExcludePlanner,
            CatalogSearchPrefetchService catalogSearchPrefetchService
    ) {
        this.sessionStoreService = sessionStoreService;
        this.memoryClientService = memoryClientService;
        this.sessionStateMachine = sessionStateMachine;
        this.turnOutcomeResolver = turnOutcomeResolver;
        this.clarificationBuilder = clarificationBuilder;
        this.memoryRecallExcludePlanner = memoryRecallExcludePlanner;
        this.catalogSearchPrefetchService = catalogSearchPrefetchService;
    }

    public ChatPreparedContext process(String userId, String sessionId, String message) {
        String resolvedSessionId = resolveSessionId(sessionId);
        SessionState sessionState = sessionStoreService.getSession(userId, resolvedSessionId);
        Map<String, Object> sessionContext = sessionState.getSessionContext();

        SlotProcessResult slots = sessionStateMachine.processSlots(message, sessionContext);
        List<String> excludeKeys = memoryRecallExcludePlanner.plan(slots.sessionContext());
        MemoryRecallResult recallResult = memoryClientService.recall(userId, message, excludeKeys);

        MemoryRecallSupport.storeRecalledKeys(slots.sessionContext(), recallResult.recalledKeys());
        Map<String, Object> memoryDebug = memoryDebug(recallResult);
        SessionProcessResult processed = sessionStateMachine.finalizeWithProfile(
                slots,
                recallResult.profileSegments(),
                memoryDebug
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

        PrefetchedSearchResult prefetchedSearch = prefetchSearchIfNeeded(decision, processed, message);

        return new ChatPreparedContext(
                userId,
                resolvedSessionId,
                sessionState,
                recallResult.profileSegments(),
                processed.extractedPatch(),
                processed.sessionContext(),
                processed.effectiveContext(),
                processed.intentType(),
                processed.categoryResolution(),
                processed.stateDebug(),
                decision,
                prefetchedSearch
        );
    }

    private PrefetchedSearchResult prefetchSearchIfNeeded(
            TurnDecision decision,
            SessionProcessResult processed,
            String message
    ) {
        if (decision.outcome() != TurnOutcome.READY_FOR_AGENT) {
            return PrefetchedSearchResult.skipped();
        }
        return catalogSearchPrefetchService.prefetch(processed.effectiveContext(), message);
    }

    private Map<String, Object> memoryDebug(MemoryRecallResult recallResult) {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("excludeKeys", recallResult.excludeKeys());
        debug.put("recalledKeys", recallResult.recalledKeys());
        debug.put("segmentFields", recallResult.profileSegments().keySet());
        return debug;
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }
}
