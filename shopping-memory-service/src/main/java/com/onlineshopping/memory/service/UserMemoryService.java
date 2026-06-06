package com.onlineshopping.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.memory.dto.MemoryResponse;
import com.onlineshopping.memory.model.UserMemoryEntity;
import com.onlineshopping.memory.repo.UserMemoryRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserMemoryService {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "budgetMin",
            "budgetMax",
            "scene",
            "brandPreferences",
            "dislikes",
            "notes",
            "lastUpdatedAt"
    );

    private static final String RECONCILE_REPLACE_KEY = "_reconcileReplace";

    private final UserMemoryRepository repository;
    private final ObjectMapper objectMapper;

    public UserMemoryService(UserMemoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public MemoryResponse getByUserId(String userId) {
        UserMemoryEntity entity = repository.findById(userId)
                .orElseGet(() -> {
                    UserMemoryEntity empty = new UserMemoryEntity();
                    empty.setUserId(userId);
                    empty.setProfileJson("{}");
                    empty.setSummaryMd("");
                    return empty;
                });
        return toResponse(entity);
    }

    public MemoryResponse mergeUpdate(String userId, Map<String, Object> patch) {
        UserMemoryEntity entity = repository.findById(userId).orElseGet(() -> {
            UserMemoryEntity created = new UserMemoryEntity();
            created.setUserId(userId);
            created.setProfileJson("{}");
            created.setSummaryMd("");
            return created;
        });
        Map<String, Object> current = parseJsonMap(entity.getProfileJson());
        Map<String, Object> merged = merge(current, patch);
        merged.put("lastUpdatedAt", Instant.now().toString());
        entity.setProfileJson(writeJson(merged));
        entity.setSummaryMd(buildSummary(merged));
        UserMemoryEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    public void deleteByUserId(String userId) {
        repository.deleteById(userId);
    }

    private MemoryResponse toResponse(UserMemoryEntity entity) {
        MemoryResponse response = new MemoryResponse();
        response.setUserId(entity.getUserId());
        response.setProfileJson(parseJsonMap(entity.getProfileJson()));
        response.setSummaryMd(entity.getSummaryMd());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private Map<String, Object> merge(Map<String, Object> current, Map<String, Object> patch) {
        boolean replace = Boolean.TRUE.equals(patch.get(RECONCILE_REPLACE_KEY));
        Map<String, Object> result = new HashMap<>(current);
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String key = entry.getKey();
            if (RECONCILE_REPLACE_KEY.equals(key) || !ALLOWED_FIELDS.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if ("brandPreferences".equals(key) || "dislikes".equals(key) || "notes".equals(key)) {
                if (replace) {
                    result.put(key, normalizeStringList(value));
                } else {
                    List<String> oldValues = normalizeStringList(result.get(key));
                    List<String> newValues = normalizeStringList(value);
                    Set<String> merged = new HashSet<>(oldValues);
                    merged.addAll(newValues);
                    result.put(key, new ArrayList<>(merged));
                }
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<String> normalizeStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString().trim());
                }
            }
            return result;
        }
        return List.of(value.toString());
    }

    private String buildSummary(Map<String, Object> profile) {
        List<String> pieces = new ArrayList<>();
        Object budgetMin = profile.get("budgetMin");
        Object budgetMax = profile.get("budgetMax");
        if (budgetMin != null || budgetMax != null) {
            pieces.add("预算 " + valueOrDash(budgetMin) + "-" + valueOrDash(budgetMax));
        }
        if (profile.get("scene") != null) {
            pieces.add("场景 " + profile.get("scene"));
        }
        if (profile.get("brandPreferences") != null) {
            pieces.add("偏好品牌 " + profile.get("brandPreferences"));
        }
        if (profile.get("dislikes") != null) {
            pieces.add("避免 " + profile.get("dislikes"));
        }
        if (profile.get("notes") != null) {
            List<String> notes = normalizeStringList(profile.get("notes"));
            if (!notes.isEmpty()) {
                pieces.add("备注 " + String.join("、", notes));
            }
        }
        return String.join("；", pieces);
    }

    private String valueOrDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String writeJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize profile json", e);
        }
    }
}
