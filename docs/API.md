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

### `POST /api/v1/catalog/product-embeddings/rebuild`（可选）

- **条件**：`shopping.vector.enabled=true`、已配置 PostgreSQL JDBC、`SPRING_AI_DASHSCOPE_API_KEY` 非空，且 **`shopping.vector.admin-rebuild-enabled=true`**（仅建议内网/开发开启）。  
- **作用**：从 MySQL 读取全部 `product`，调用 DashScope `text-embedding-v2` 写入 PG 表 `embedding_product`。  
- **响应**：`{"indexed": <写入或更新的行数>}`（跳过 `content_hash` 未变的行不计入）。  
- **前置**：在目标库执行 `scripts/init-postgres-pgvector.sql`。

MCP 工具 `searchProduct` 增加可选参数 **`semanticQuery`**：在向量能力启用且 PG 已有索引时，优先在「`category_id` + 价格」过滤下按语义排序；否则行为与原先一致。

## Memory Service

### `GET /api/v1/memory/{userId}`
Read profile.

### `PUT /api/v1/memory/{userId}`
Merge update fields in `profileJson`.

### `DELETE /api/v1/memory/{userId}`
Delete profile (demo reset).
