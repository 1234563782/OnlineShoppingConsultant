package com.onlineshopping.orchestrator.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AuthSessionStore {

    private static final String KEY_PREFIX = "auth:token:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthProperties authProperties;

    public AuthSessionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AuthProperties authProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
    }

    public String createSession(AuthSession session) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        save(token, session);
        return token;
    }

    public void save(String token, AuthSession session) {
        if (token == null || token.isBlank() || session == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    key(token),
                    objectMapper.writeValueAsString(session),
                    authProperties.getTokenTtlDays(),
                    TimeUnit.DAYS
            );
        } catch (JsonProcessingException ignored) {
        }
    }

    public Optional<AuthSession> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String json = redisTemplate.opsForValue().get(key(token));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, AuthSession.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void delete(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(key(token));
        }
    }

    public Duration cookieMaxAge() {
        return Duration.ofDays(Math.max(1, authProperties.getTokenTtlDays()));
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
