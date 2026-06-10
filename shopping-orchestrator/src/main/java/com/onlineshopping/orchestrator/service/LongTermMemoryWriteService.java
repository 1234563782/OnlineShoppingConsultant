package com.onlineshopping.orchestrator.service;

import com.onlineshopping.orchestrator.support.ProfileListNormalizer;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class LongTermMemoryWriteService {

    private final ContextMergeService contextMergeService;
    private final MemoryMergeService memoryMergeService;
    private final ProfileReconcileService profileReconcileService;
    private final MemoryClientService memoryClientService;
    private final MemoryWriteFilter memoryWriteFilter;

    public LongTermMemoryWriteService(
            ContextMergeService contextMergeService,
            MemoryMergeService memoryMergeService,
            ProfileReconcileService profileReconcileService,
            MemoryClientService memoryClientService,
            MemoryWriteFilter memoryWriteFilter
    ) {
        this.contextMergeService = contextMergeService;
        this.memoryMergeService = memoryMergeService;
        this.profileReconcileService = profileReconcileService;
        this.memoryClientService = memoryClientService;
        this.memoryWriteFilter = memoryWriteFilter;
    }

    public WriteResult write(
            String userId,
            Map<String, Object> extractedPatch,
            Map<String, Object> effectiveContext,
            Map<String, Object> existingProfile,
            String userMessage
    ) {
        Map<String, Object> extractionPatch = memoryWriteFilter.filter(
                contextMergeService.toLongTermMemoryPatch(extractedPatch),
                userMessage,
                true
        );
        Map<String, Object> sessionPatch = memoryWriteFilter.filter(
                memoryMergeService.deriveSessionPreferencePatch(
                        effectiveContext,
                        extractedPatch,
                        existingProfile,
                        userMessage
                ),
                userMessage,
                false
        );
        Map<String, Object> mergedPatch = memoryMergeService.mergeForProfile(extractionPatch, sessionPatch);
        mergedPatch = memoryWriteFilter.filter(mergedPatch, userMessage, !extractionPatch.isEmpty());
        boolean shouldReconcile = ProfileListNormalizer.hasPreferenceIncoming(mergedPatch)
                || memoryMergeService.sessionContradictsProfile(existingProfile, effectiveContext, userMessage);

        if (mergedPatch.isEmpty() && !shouldReconcile) {
            return new WriteResult(extractionPatch, sessionPatch, mergedPatch, Map.of(), Map.of(), false);
        }

        Map<String, Object> patchToWrite = mergedPatch;
        Map<String, Object> reconciledPatch = Map.of();
        if (shouldReconcile) {
            if (!ProfileListNormalizer.hasPreferenceIncoming(mergedPatch)) {
                patchToWrite = sessionPatch;
            }
            patchToWrite = memoryWriteFilter.filter(patchToWrite, userMessage, !extractionPatch.isEmpty());
            if (patchToWrite.isEmpty()) {
                return new WriteResult(extractionPatch, sessionPatch, mergedPatch, Map.of(), Map.of(), false);
            }
            reconciledPatch = profileReconcileService.reconcile(existingProfile, patchToWrite, userMessage);
            reconciledPatch = memoryWriteFilter.filter(reconciledPatch, userMessage, !extractionPatch.isEmpty());
            if (reconciledPatch.isEmpty()) {
                return new WriteResult(extractionPatch, sessionPatch, mergedPatch, Map.of(), Map.of(), false);
            }
            memoryClientService.mergePatch(userId, reconciledPatch);
            return new WriteResult(extractionPatch, sessionPatch, mergedPatch, reconciledPatch, reconciledPatch, true);
        }

        memoryClientService.mergePatch(userId, patchToWrite);
        return new WriteResult(extractionPatch, sessionPatch, mergedPatch, Map.of(), patchToWrite, true);
    }

    public record WriteResult(
            Map<String, Object> extractionPatch,
            Map<String, Object> sessionPatch,
            Map<String, Object> mergedPatch,
            Map<String, Object> reconciledPatch,
            Map<String, Object> writtenPatch,
            boolean profileWritten
    ) {
    }
}
