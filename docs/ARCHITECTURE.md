# 架构说明

> 延伸阅读：[Open-ClaudeCode 高价值借鉴指南](./OPEN_CLAUDECODE_BORROWINGS.md) · [品类槽位改造](./CATEGORY_SLOT_REFACTOR.md) · [项目演进](./EVOLUTION.md)

## 服务拓扑

- `shopping-orchestrator` (8087)：聊天 API、Web UI、Redis 会话存储、A2A 客户端发现。
- `shopping-consult-agent` (8081)：A2A 服务端（`consult_agent`），通过 Nacos 连接 MCP 客户端。
- `shopping-compare-agent` (8082)：A2A 服务端（`compare_agent`），通过 Nacos 连接 MCP 客户端。
- `shopping-memory-service` (8086)：长期画像记忆 REST 服务（MySQL）。
- `catalog-mcp-server` (8083)：商品搜索/详情 MCP 工具；提供用于类目归一化的 **REST** 接口 `GET /api/v1/categories/normalize`（由 orchestrator 使用）。
- `inventory-mcp-server` (8084)：库存工具。
- `promotion-mcp-server` (8085)：促销工具。

## Nacos 职责

- 为 `consult_agent` 提供 A2A agent-card 的注册与发现。
- 为工具服务器提供 MCP 服务的注册与发现。

## 请求流程

1. 客户端向 orchestrator 发起 `POST /api/v1/chat`。
2. orchestrator 从 memory-service 加载长期记忆画像。
3. orchestrator 从 Redis 加载最近的会话轮次。
4. orchestrator 先做上下文抽取和会话合并，再进行 **类目归一化**（通过 HTTP 调用 catalog 的 `GET /api/v1/categories/normalize`），并在构建 `effectiveContext` 之前把 `categoryId` / `categoryResolution` 写回 session。
5. orchestrator 根据 `shoppingSubIntent` 通过 A2A 路由到 `consult_agent` 或 `compare_agent`。
6. consult agent 通过 Nacos 发现的工具回调调用 MCP 工具。
7. orchestrator 将轮次追加回 Redis，并返回回复。
