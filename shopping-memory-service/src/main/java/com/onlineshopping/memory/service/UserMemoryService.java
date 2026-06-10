package com.onlineshopping.memory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineshopping.memory.dto.MemoryRecallRequest;
import com.onlineshopping.memory.dto.MemoryRecallResponse;
import com.onlineshopping.memory.dto.MemoryResponse;
import com.onlineshopping.memory.mapper.UserMemoryMapper;
import com.onlineshopping.memory.model.UserMemoryEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

    private static final String SEGMENT_BUDGET = "budget";
    private static final String SEGMENT_BRANDS = "brands";
    private static final String SEGMENT_SCENE = "scene";
    private static final String SEGMENT_NOTES = "notes";

    private static final Map<String, List<String>> SEGMENT_FIELDS = Map.of(
            SEGMENT_BUDGET, List.of("budgetMin", "budgetMax"),
            SEGMENT_BRANDS, List.of("brandPreferences", "dislikes"),
            SEGMENT_SCENE, List.of("scene"),
            SEGMENT_NOTES, List.of("notes")
    );

    private final UserMemoryMapper userMemoryMapper;
    private final ObjectMapper objectMapper;

    public UserMemoryService(UserMemoryMapper userMemoryMapper, ObjectMapper objectMapper) {
        this.userMemoryMapper = userMemoryMapper;
        this.objectMapper = objectMapper;
    }

    public MemoryResponse getByUserId(String userId) {
        UserMemoryEntity entity = userMemoryMapper.selectById(userId);
        if (entity == null) {
            UserMemoryEntity empty = new UserMemoryEntity();
            empty.setUserId(userId);
            empty.setProfileJson("{}");
            empty.setSummaryMd("");
            return toResponse(empty);
        }
        return toResponse(entity);
    }

    public MemoryResponse mergeUpdate(String userId, Map<String, Object> patch) {
        UserMemoryEntity entity = userMemoryMapper.selectById(userId);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new UserMemoryEntity();
            entity.setUserId(userId);
            entity.setProfileJson("{}");
            entity.setSummaryMd("");
        }
        Map<String, Object> current = parseJsonMap(entity.getProfileJson());
        Map<String, Object> merged = merge(current, patch);
        merged.put("lastUpdatedAt", Instant.now().toString());
        entity.setProfileJson(writeJson(merged));
        entity.setSummaryMd(buildSummary(merged));
        Instant now = Instant.now();
        if (isNew) {
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            userMemoryMapper.insert(entity);
        } else {
            entity.setUpdatedAt(now);
            userMemoryMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    public void deleteByUserId(String userId) {
        userMemoryMapper.deleteById(userId);
    }

    public MemoryRecallResponse recall(String userId, MemoryRecallRequest request) {
        Map<String, Object> full = getByUserId(userId).getProfileJson();
        String query = request == null || request.getQuery() == null ? "" : request.getQuery();
        int topK = request == null || request.getTopK() <= 0 ? 5 : request.getTopK();
        Set<String> exclude = request == null || request.getExcludeKeys() == null
                ? Set.of()
                : new HashSet<>(request.getExcludeKeys());

        LinkedHashSet<String> candidates = new LinkedHashSet<>(selectSegmentKeys(query));
        candidates.removeAll(exclude);

        Map<String, Object> segments = new LinkedHashMap<>();
        List<String> recalledKeys = new ArrayList<>();
        for (String segmentKey : candidates) {
            if (recalledKeys.size() >= topK) {
                break;
            }
            List<String> fields = SEGMENT_FIELDS.get(segmentKey);
            if (fields == null) {
                continue;
            }
            boolean hasData = false;
            for (String field : fields) {
                Object value = full.get(field);
                if (value != null) {
                    segments.put(field, value);
                    hasData = true;
                }
            }
            if (hasData) {
                recalledKeys.add(segmentKey);
            }
        }

        MemoryRecallResponse response = new MemoryRecallResponse();
        response.setProfileSegments(segments);
        response.setRecalledKeys(recalledKeys);
        return response;
    }

    private List<String> selectSegmentKeys(String query) {
        String text = query == null ? "" : query.toLowerCase();
        LinkedHashSet<String> keys = new LinkedHashSet<>();

        if (containsAny(text, "预算", "多少钱", "价位", "价格", "便宜", "贵")) {
            keys.add(SEGMENT_BUDGET);
        }
        if (containsAny(text, "品牌", "牌子", "喜欢", "讨厌", "不喜欢", "偏好")) {
            keys.add(SEGMENT_BRANDS);
        }
        if (containsAny(text, "场景", "用途", "通勤", "办公", "运动", "游戏", "学习")) {
            keys.add(SEGMENT_SCENE);
        }
        if (containsAny(text, "鞋", "衣服", "尺码", "备注", "其他")) {
            keys.add(SEGMENT_NOTES);
            keys.add(SEGMENT_BRANDS);
        }
        if (containsAny(text, "手机", "电脑", "平板", "耳机", "手表", "相机", "电视", "冰箱", "洗衣机", "推荐", "买")) {
            keys.add(SEGMENT_BUDGET);
            keys.add(SEGMENT_BRANDS);
        }

        if (keys.isEmpty()) {
            keys.add(SEGMENT_BUDGET);
            keys.add(SEGMENT_BRANDS);
        }
        return new ArrayList<>(keys);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
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
