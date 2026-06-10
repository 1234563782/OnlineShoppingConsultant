package com.onlineshopping.orchestrator.support;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRecallSupportTest {

    @Test
    void readsAndStoresRecalledKeys() {
        Map<String, Object> session = new HashMap<>();
        MemoryRecallSupport.storeRecalledKeys(session, List.of("budget", "brands"));

        assertThat(MemoryRecallSupport.recalledKeys(session))
                .containsExactly("budget", "brands");
    }

    @Test
    void clearsRecalledKeysWhenEmpty() {
        Map<String, Object> session = new HashMap<>();
        session.put(SessionContextKeys.RECALLED_MEMORY_KEYS, List.of("budget"));

        MemoryRecallSupport.storeRecalledKeys(session, List.of());

        assertThat(session).doesNotContainKey(SessionContextKeys.RECALLED_MEMORY_KEYS);
    }
}
