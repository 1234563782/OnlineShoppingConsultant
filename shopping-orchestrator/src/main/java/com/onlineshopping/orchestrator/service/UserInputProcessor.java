package com.onlineshopping.orchestrator.service;



import com.onlineshopping.orchestrator.dto.ChatPreparedContext;

import com.onlineshopping.orchestrator.dto.MemoryRecallResult;

import com.onlineshopping.orchestrator.dto.PrefetchedCompareResult;

import com.onlineshopping.orchestrator.dto.PrefetchedSearchResult;

import com.onlineshopping.orchestrator.dto.SessionProcessResult;

import com.onlineshopping.orchestrator.dto.SessionState;

import com.onlineshopping.orchestrator.dto.SlotProcessResult;

import com.onlineshopping.orchestrator.dto.TurnDecision;

import com.onlineshopping.orchestrator.dto.TurnOutcome;

import com.onlineshopping.orchestrator.support.MemoryRecallSupport;

import com.onlineshopping.orchestrator.support.SessionContextKeys;

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

    private final ComparePrefetchService comparePrefetchService;



    public UserInputProcessor(

            SessionStoreService sessionStoreService,

            MemoryClientService memoryClientService,

            SessionStateMachine sessionStateMachine,

            TurnOutcomeResolver turnOutcomeResolver,

            ClarificationBuilder clarificationBuilder,

            MemoryRecallExcludePlanner memoryRecallExcludePlanner,

            CatalogSearchPrefetchService catalogSearchPrefetchService,

            ComparePrefetchService comparePrefetchService

    ) {

        this.sessionStoreService = sessionStoreService;

        this.memoryClientService = memoryClientService;

        this.sessionStateMachine = sessionStateMachine;

        this.turnOutcomeResolver = turnOutcomeResolver;

        this.clarificationBuilder = clarificationBuilder;

        this.memoryRecallExcludePlanner = memoryRecallExcludePlanner;

        this.catalogSearchPrefetchService = catalogSearchPrefetchService;

        this.comparePrefetchService = comparePrefetchService;

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



        PrefetchedSearchResult prefetchedSearch = PrefetchedSearchResult.skipped();

        PrefetchedCompareResult prefetchedCompare = PrefetchedCompareResult.skipped();

        if (decision.outcome() == TurnOutcome.READY_FOR_AGENT) {

            if (comparePrefetchService.isCompareIntent(processed.sessionContext())) {

                prefetchedCompare = comparePrefetchService.prefetch(

                        processed.sessionContext(),

                        processed.extractedPatch(),

                        message

                );

                if (!prefetchedCompare.isUsable()) {

                    decision = compareFailureDecision(decision, prefetchedCompare);

                }

            } else {

                prefetchedSearch = catalogSearchPrefetchService.prefetch(processed.effectiveContext(), message);

            }

        }



        if (decision.outcome() == TurnOutcome.NEED_CLARIFICATION) {

            clarificationBuilder.applySessionMarkers(

                    processed.sessionContext(),

                    processed.effectiveContext(),

                    new ClarificationBuilder.Clarification(decision.clarification(), decision.clarificationField())

            );

            sessionState.setSessionContext(processed.sessionContext());

        }



        sessionStoreService.saveSession(userId, resolvedSessionId, sessionState);



        String shoppingSubIntent = shoppingSubIntent(processed.sessionContext());



        return new ChatPreparedContext(

                userId,

                resolvedSessionId,

                sessionState,

                recallResult.profileSegments(),

                processed.extractedPatch(),

                processed.sessionContext(),

                processed.effectiveContext(),

                processed.intentType(),

                shoppingSubIntent,

                processed.categoryResolution(),

                processed.stateDebug(),

                decision,

                prefetchedSearch,

                prefetchedCompare

        );

    }



    private TurnDecision compareFailureDecision(TurnDecision current, PrefetchedCompareResult prefetchedCompare) {

        String message = prefetchedCompare.message() != null && !prefetchedCompare.message().isBlank()

                ? prefetchedCompare.message()

                : (prefetchedCompare.error() == null ? "暂时无法完成商品对比，请稍后再试。" : prefetchedCompare.error());

        return new TurnDecision(

                TurnOutcome.NEED_CLARIFICATION,

                current.categoryReplaced(),

                current.categoryReplaceReason(),

                null,

                message,

                "compareTargets"

        );

    }



    private String shoppingSubIntent(Map<String, Object> sessionContext) {

        if (sessionContext == null) {

            return SessionContextKeys.SUB_INTENT_DISCOVER;

        }

        Object value = sessionContext.get(SessionContextKeys.SHOPPING_SUB_INTENT);

        if (value == null || value.toString().isBlank()) {

            return SessionContextKeys.SUB_INTENT_DISCOVER;

        }

        return value.toString().trim().toLowerCase();

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

