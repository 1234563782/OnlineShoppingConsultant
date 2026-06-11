package com.onlineshopping.prompt;

import java.util.LinkedHashMap;
import java.util.Map;

public record RenderedPrompt(
        String promptId,
        String version,
        String content,
        String contentHash
) {
    public Map<String, Object> toDebugMap() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("id", promptId);
        debug.put("version", version);
        debug.put("hash", contentHash);
        return debug;
    }
}
