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

`done.debug` 中可能包含：

- `categoryResolution`：Orchestrator 调用 catalog 归一化后的结果（`status`、`categoryId`、`confidence` 等）。

## Catalog Service (MCP + REST)

### `GET /api/v1/categories/normalize?raw=...`

将用户原词归一为标准类目（供 Orchestrator 在进 Agent 前写入 `sessionContext.categoryId`）。

Query:

| 参数 | 说明 |
|------|------|
| `raw` | 用户说的品类原词，如 `耳机`、`降噪耳机` |

Response 示例：

```json
{
  "categoryId": "cat_headphone",
  "categoryName": "耳机",
  "categoryRaw": "耳机",
  "confidence": 0.9,
  "status": "RESOLVED",
  "matchedBy": "alias"
}
```

`status` 为 `UNRESOLVED` 时表示目录中未匹配到类目。

Orchestrator 通过 `shopping.catalog.base-url`（默认 `http://localhost:8083`）调用此接口；置信度阈值见 `shopping.catalog.confidence-threshold`（默认 `0.85`），低于阈值会触发「是否指某品类」的追问（`missingFields` 含 `categoryConfirm`）。

## Memory Service

### `GET /api/v1/memory/{userId}`
Read profile.

### `PUT /api/v1/memory/{userId}`
Merge update fields in `profileJson`.

### `DELETE /api/v1/memory/{userId}`
Delete profile (demo reset).
