package com.onlineshopping.orchestrator.support;

import java.util.Locale;

/**
 * Extracts assistantReply text from incremental or cumulative JSON stream chunks.
 */
public class AssistantReplyStreamFilter {

    private static final String[] KEYS = {
            "\"assistantReply\":\"",
            "\"assistantReply\": \"",
            "\"reply\":\"",
            "\"reply\": \""
    };

    private final StringBuilder raw = new StringBuilder();
    private int emittedLength;
    private boolean valueComplete;

    public String append(String chunk) {
        if (chunk == null || chunk.isBlank() || valueComplete) {
            return "";
        }
        mergeChunk(chunk);
        String decoded = decodeAssistantReplyValue(raw.toString());
        if (decoded == null || decoded.length() <= emittedLength) {
            return "";
        }
        String delta = decoded.substring(emittedLength);
        emittedLength = decoded.length();
        return delta;
    }

    public String rawContent() {
        return raw.toString();
    }

    public static String decodeFull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        AssistantReplyStreamFilter filter = new AssistantReplyStreamFilter();
        filter.mergeChunk(text);
        return filter.decodeAssistantReplyValue(text);
    }

    private void mergeChunk(String chunk) {
        String current = raw.toString();
        if (chunk.equals(current)) {
            return;
        }
        if (!current.isEmpty() && chunk.startsWith(current)) {
            raw.setLength(0);
            raw.append(chunk);
            return;
        }
        if (!current.isEmpty() && current.startsWith(chunk)) {
            return;
        }
        raw.append(chunk);
    }

    private String decodeAssistantReplyValue(String text) {
        if (valueComplete) {
            return text.isEmpty() ? null : text.substring(0, Math.min(text.length(), emittedLength));
        }
        KeyMatch keyMatch = findKey(text);
        if (keyMatch == null) {
            return null;
        }

        StringBuilder decoded = new StringBuilder();
        int index = keyMatch.valueStart();
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\\' && index + 1 < text.length()) {
                decoded.append(unescape(text.charAt(index + 1)));
                index += 2;
                continue;
            }
            if (current == '"') {
                valueComplete = true;
                return decoded.toString();
            }
            decoded.append(current);
            index++;
        }
        return decoded.toString();
    }

    private KeyMatch findKey(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int bestIdx = -1;
        int bestLen = -1;
        for (String key : KEYS) {
            int idx = lower.indexOf(key.toLowerCase(Locale.ROOT));
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
                bestLen = key.length();
            }
        }
        if (bestIdx < 0) {
            return null;
        }
        return new KeyMatch(bestIdx, bestIdx + bestLen);
    }

    private char unescape(char value) {
        return switch (value) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> value;
        };
    }

    private record KeyMatch(int keyStart, int valueStart) {
    }
}
