package com.onlineshopping.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.orchestrator.dto.SessionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SessionStoreService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${shopping.session.max-turns:10}")
    private int maxTurns;

    @Value("${shopping.session.ttl-days:7}")
    private int ttlDays;

    public SessionStoreService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public SessionState getSession(String userId, String sessionId) {
        String key = key(userId, sessionId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            SessionState state = new SessionState();
            state.setUserId(userId);
            state.setUpdatedAt(Instant.now().toString());
            return state;
        }
        try {
            SessionState state = objectMapper.readValue(json, SessionState.class);
            if (state.getTurns() == null) {
                state.setTurns(new ArrayList<>());
            }
            if (state.getUserId() != null && !state.getUserId().equals(userId)) {
                SessionState fresh = new SessionState();
                fresh.setUserId(userId);
                fresh.setUpdatedAt(Instant.now().toString());
                return fresh;
            }
            state.setUserId(userId);
            return state;
        } catch (Exception e) {
            SessionState state = new SessionState();
            state.setUserId(userId);
            state.setUpdatedAt(Instant.now().toString());
            return state;
        }
    }

    public void appendTurns(String userId, String sessionId, SessionState state, String userInput, String assistantReply) {
        List<SessionState.Turn> turns = state.getTurns();
        turns.add(turn("user", userInput));
        turns.add(turn("assistant", assistantReply));
        if (turns.size() > maxTurns) {
            state.setTurns(new ArrayList<>(turns.subList(turns.size() - maxTurns, turns.size())));
        }
        state.setUserId(userId);
        state.setUpdatedAt(Instant.now().toString());
        try {
            redisTemplate.opsForValue().set(
                    key(userId, sessionId),
                    objectMapper.writeValueAsString(state),
                    ttlDays,
                    TimeUnit.DAYS
            );
        } catch (Exception ignored) {
        }
    }

    private SessionState.Turn turn(String role, String content) {
        SessionState.Turn t = new SessionState.Turn();
        t.setRole(role);
        t.setContent(content);
        t.setTs(Instant.now().toString());
        return t;
    }

    private String key(String userId, String sessionId) {
        return "session:chat:" + userId + ":" + sessionId;
    }
}
