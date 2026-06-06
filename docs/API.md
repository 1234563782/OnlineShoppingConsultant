# API

## Orchestrator

### `POST /api/v1/chat`

SSE 流式响应（`Content-Type: text/event-stream`）。

Request:

```json
{
  "userId": "u001",
  "sessionId": "optional-session-id",
  "message": "想买个降噪耳机，预算2000"
}
```

事件类型（每条 `data` 为 JSON）：

| type | 说明 |
|------|------|
| `session` | `{ "type":"session", "sessionId":"..." }` |
| `delta` | 纯文本增量 `{ "type":"delta", "content":"..." }` |
| `done` | 结束 `{ "type":"done", "sessionId":"...", "reply":"...", "debug":{...} }` |
| `error` | 错误 `{ "type":"error", "message":"..." }` |

`delta.content` 与 `done.reply` 均为给用户看的自然语言，不包含 Agent 原始 JSON。

## Memory Service

### `GET /api/v1/memory/{userId}`
Read profile.

### `PUT /api/v1/memory/{userId}`
Merge update fields in `profileJson`.

### `DELETE /api/v1/memory/{userId}`
Delete profile (demo reset).
