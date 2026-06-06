package com.onlineshopping.orchestrator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MemoryMergeService {

    /**
     * Filters agent memoryPatch: whitelist fields, no override of extraction, grounded check.
     */
    public Map<String, Object> sanitizeAgentPatch(
            Map<String, Object> agentPatch,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (agentPatch == null || agentPatch.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new HashMap<>();
        if (!hasListValue(extractionPatch.get("brandPreferences"))) {
            List<String> grounded = filterGroundedListItems(
                    normalizeList(agentPatch.get("brandPreferences")),
                    effectiveContext,
                    extractionPatch,
                    extractedPatch,
                    userMessage,
                    "brandPreferences"
            );
            if (!grounded.isEmpty()) {
                sanitized.put("brandPreferences", grounded);
            }
        }
        if (!hasListValue(extractionPatch.get("dislikes"))) {
            List<String> grounded = filterGroundedListItems(
                    normalizeList(agentPatch.get("dislikes")),
                    effectiveContext,
                    extractionPatch,
                    extractedPatch,
                    userMessage,
                    "dislikes"
            );
            if (!grounded.isEmpty()) {
                sanitized.put("dislikes", grounded);
            }
        }
        if (!hasValue(extractionPatch.get("notes"))) {
            Object notes = agentPatch.get("notes");
            if (hasValue(notes) && isNotesGrounded(
                    notes.toString(), effectiveContext, extractionPatch, extractedPatch, userMessage)) {
                sanitized.put("notes", notes.toString().trim());
            }
        }
        return sanitized;
    }

    /**
     * Merges orchestrator extraction patch with sanitized agent patch. Extraction wins on scalar fields.
     */
    public Map<String, Object> mergeForProfile(
            Map<String, Object> extractionPatch,
            Map<String, Object> sanitizedAgentPatch
    ) {
        Map<String, Object> extraction = extractionPatch == null ? Map.of() : extractionPatch;
        Map<String, Object> agent = sanitizedAgentPatch == null ? Map.of() : sanitizedAgentPatch;
        Map<String, Object> merged = new HashMap<>();
        putMergedList(merged, extraction, agent, "brandPreferences");
        putMergedList(merged, extraction, agent, "dislikes");
        if (hasValue(extraction.get("notes"))) {
            merged.put("notes", extraction.get("notes").toString().trim());
        } else if (hasValue(agent.get("notes"))) {
            merged.put("notes", agent.get("notes").toString().trim());
        }
        return merged;
    }

    private void putMergedList(
            Map<String, Object> target,
            Map<String, Object> extraction,
            Map<String, Object> agent,
            String key
    ) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(normalizeList(extraction.get(key)));
        values.addAll(normalizeList(agent.get(key)));
        if (!values.isEmpty()) {
            target.put(key, new ArrayList<>(values));
        }
    }

    private List<String> filterGroundedListItems(
            List<String> items,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage,
            String fieldName
    ) {
        List<String> grounded = new ArrayList<>();
        for (String item : items) {
            if (isListItemGrounded(item, effectiveContext, extractionPatch, extractedPatch, userMessage, fieldName)) {
                grounded.add(item);
            }
        }
        return grounded;
    }

    private boolean isListItemGrounded(
            String item,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage,
            String fieldName
    ) {
        if (item == null || item.isBlank()) {
            return false;
        }
        String normalizedItem = item.trim();
        if (containsIgnoreCase(userMessage, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(effectiveContext, fieldName, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(extractionPatch, fieldName, normalizedItem)) {
            return true;
        }
        if (listContainsIgnoreCase(extractedPatch, fieldName, normalizedItem)) {
            return true;
        }
        Map<String, Object> profile = longTermProfileReference(effectiveContext);
        return listContainsIgnoreCase(profile, fieldName, normalizedItem);
    }

    private boolean isNotesGrounded(
            String notes,
            Map<String, Object> effectiveContext,
            Map<String, Object> extractionPatch,
            Map<String, Object> extractedPatch,
            String userMessage
    ) {
        if (notes == null || notes.isBlank()) {
            return false;
        }
        String normalizedNotes = notes.trim();
        if (containsIgnoreCase(userMessage, normalizedNotes)) {
            return true;
        }
        if (notesMatchField(normalizedNotes, effectiveContext, "notes")) {
            return true;
        }
        if (notesMatchField(normalizedNotes, extractionPatch, "notes")) {
            return true;
        }
        if (notesMatchField(normalizedNotes, extractedPatch, "notes")) {
            return true;
        }
        Map<String, Object> profile = longTermProfileReference(effectiveContext);
        return notesMatchField(normalizedNotes, profile, "notes");
    }

    private boolean listContainsIgnoreCase(Map<String, Object> source, String fieldName, String item) {
        return normalizeList(source == null ? null : source.get(fieldName)).stream()
                .anyMatch(value -> equalsIgnoreCase(value, item));
    }

    private boolean notesMatchField(String notes, Map<String, Object> source, String fieldName) {
        if (source == null) {
            return false;
        }
        Object value = source.get(fieldName);
        return hasValue(value) && textsOverlap(notes, value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> longTermProfileReference(Map<String, Object> effectiveContext) {
        if (effectiveContext == null) {
            return Map.of();
        }
        Object reference = effectiveContext.get("longTermProfileReference");
        if (reference instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private boolean textsOverlap(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return containsIgnoreCase(a, b) || containsIgnoreCase(b, a);
    }

    private List<String> normalizeList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString().trim());
            }
        }
        return result;
    }

    private boolean hasListValue(Object value) {
        return !normalizeList(value).isEmpty();
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank() && !"null".equalsIgnoreCase(s);
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        return true;
    }

    private boolean containsIgnoreCase(String text, String needle) {
        return text != null
                && needle != null
                && !needle.isBlank()
                && text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
