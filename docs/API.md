# API

## Orchestrator

### `POST /api/v1/chat`

Request:

```json
{
  "userId": "u001",
  "sessionId": "optional-session-id",
  "message": "想买个降噪耳机，预算2000"
}
```

Response:

```json
{
  "sessionId": "generated-or-input-session-id",
  "reply": "推荐结果...",
  "debug": {
    "toolMode": "a2a+nacos"
  }
}
```

## Memory Service

### `GET /api/v1/memory/{userId}`
Read profile.

### `PUT /api/v1/memory/{userId}`
Merge update fields in `profileJson`.

### `DELETE /api/v1/memory/{userId}`
Delete profile (demo reset).
