package com.onlineshopping.orchestrator.support;

/**
 * Accumulates plain-text stream chunks and emits only new suffix deltas.
 * Handles both incremental tokens and cumulative "full text so far" chunks.
 */
public class PlainTextStreamBuffer {

    private final StringBuilder content = new StringBuilder();
    private int emittedLength;

    public String append(String chunk) {
        if (chunk == null || chunk.isBlank()) {
            return "";
        }
        mergeChunk(chunk);
        if (content.length() <= emittedLength) {
            return "";
        }
        String delta = content.substring(emittedLength);
        emittedLength = content.length();
        return delta;
    }

    public String content() {
        return content.toString();
    }

    private void mergeChunk(String chunk) {
        String current = content.toString();
        if (chunk.equals(current)) {
            return;
        }
        if (!current.isEmpty() && chunk.startsWith(current)) {
            content.setLength(0);
            content.append(chunk);
            return;
        }
        if (!current.isEmpty() && current.startsWith(chunk)) {
            return;
        }
        content.append(chunk);
    }
}
