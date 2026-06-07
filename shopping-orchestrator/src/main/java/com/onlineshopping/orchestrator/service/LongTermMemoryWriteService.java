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

    public LongTermMemoryWriteService(
            ContextMergeService contextMergeService,
            MemoryMergeService memoryMergeService,
            ProfileReconcileService profileReconcileService,
            MemoryClientService memoryClientService
    ) {
        this.contextMergeService = contextMergeService;
        this.memoryMergeService = memoryMergeService;
        this.profileReconcileService = profileReconcileService;
        this.memoryClientService = memoryClientService;
    }

    public WriteResult write(
            String userId,
            Map<String, Object> extractedPatch,
            Map<String, Object> effectiveContext,
            Map<String, Object> existingProfile,
            String userMessage
    ) {
        Map<String, Object> extractionPatch = contextMergeService.toLongTermMemoryPatch(extractedPatch);
        Map<String, Object> sessionPatch = memoryMergeService.deriveSessionPreferencePatch(
                effectiveContext,
                extractedPatch,
                existingProfile,
                userMessage
        );
        Map<String, Object> mergedPatch = memoryMergeService.mergeForProfile(extractionPatch, sessionPatch);
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
            reconciledPatch = profileReconcileService.reconcile(existingProfile, patchToWrite, userMessage);
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
