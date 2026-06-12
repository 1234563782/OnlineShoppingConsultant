# Online Shopping Consultant

电商智能导购多 Agent 系统（Spring Boot + Spring AI Alibaba）。

## 当前架构（初版）

- `shopping-orchestrator`：总控 Agent，对外 `POST /api/v1/chat`（SSE 流式）+ **Vue3 前端**（登录/注册/聊天）
- `shopping-consult-agent`：咨询导购 Agent（A2A Server）
- `shopping-compare-agent`：商品对比 Agent（A2A Server）
- `shopping-memory-service`：用户长期画像（MySQL，REST）
- `shopping-catalog-mcp-server`：商品搜索/详情工具（MCP）+ **类目归一化 REST**（供 orchestrator 调用）
- `shopping-inventory-mcp-server`：库存工具（MCP）
- `shopping-promotion-mcp-server`：优惠工具（MCP）

## Nacos 用法

初版按 `spring-ai-alibaba-multi-agent-demo-main` 对齐：

- **A2A 注册发现**：consult 注册 `consult_agent`、compare 注册 `compare_agent`；orchestrator 通过 `AgentCardProvider` 发现
- **MCP 注册发现**：catalog/inventory/promotion 注册为 MCP 服务；consult 通过 `loadbalancedMcpSyncToolCallbacks` 调用

## 运行依赖

- Redis：会话上下文（`6379`）
- Nacos：A2A + MCP 注册发现（`8848`、`9848`）
- MySQL：长期用户画像（默认库：`shopping_consultant`）

使用根目录 compose 启动：

```bash
docker compose up -d
```

## 模块端口

- orchestrator: `8087`
- consult-agent: `8081`
- compare-agent: `8082`
- memory-service: `8086`
- catalog-mcp-server: `8083`
- inventory-mcp-server: `8084`
- promotion-mcp-server: `8085`

## 环境变量

复制 `.env.example` 到 `.env` 并按需配置：

- `SPRING_AI_DASHSCOPE_API_KEY`
- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `SHOPPING_MEMORY_DB_URL`
- `SHOPPING_MEMORY_DB_USERNAME`
- `SHOPPING_MEMORY_DB_PASSWORD`
- `SHOPPING_CATALOG_BASE_URL`（orchestrator 调 catalog 做类目归一化，默认 `http://localhost:8083`）
- `SHOPPING_CATALOG_CONFIDENCE_THRESHOLD`（可选，默认 `0.85`）

> 不要把 `.env` 提交到 Git。

## MySQL 初始化

表结构与演示数据请用脚本一次性初始化（服务启动**不会**自动建表或灌数）：

```bash
mysql -u root -p < scripts/init-mysql.sql
```

各使用 MySQL 的模块采用 **MyBatis-Plus** 访问数据库（`mapper` 包 + `BaseMapper`）；表结构由 `scripts/init-mysql.sql` 维护，不会自动建表。

MySQL 脚本包含：`product_category`、`product`、`product_inventory`、`product_promotion`、`user_account`、`user_memory`。

## PostgreSQL + pgvector（语义搜索，可选但推荐）

向量表与 MySQL 商品 **sku_id 对齐**；embedding 向量需 MySQL 有商品后 rebuild：

```bash
psql -U postgres -d postgres -f scripts/init-postgres-pgvector.sql
```

catalog 服务启动且 `SHOPPING_VECTOR_ENABLED=true` 时，重建索引：

```bash
curl -X POST http://localhost:8083/api/v1/catalog/product-embeddings/rebuild
```

（需 `SHOPPING_VECTOR_ADMIN_REBUILD_ENABLED=true`，且配置 `SPRING_AI_DASHSCOPE_API_KEY`。）

## 前端（Vue3）

源码在 `shopping-web/`，构建产物输出到 orchestrator 的 `static/` 目录：

```bash
cd shopping-web
npm install
npm run build
```

开发时可 `npm run dev`（5173 端口，已代理 `/api` 到 8087）。

浏览器访问 **http://localhost:8087** ，先注册/登录；登录态为 **httpOnly Cookie**，会话 Token 存在 **Redis**（`auth:token:*`）。

## 鉴权 API（orchestrator）

| 接口 | 说明 |
|------|------|
| `POST /api/v1/auth/register` | 注册，Set-Cookie |
| `POST /api/v1/auth/login` | 登录，Set-Cookie |
| `POST /api/v1/auth/logout` | 登出，清 Cookie + Redis |
| `GET /api/v1/auth/me` | 当前用户（需 Cookie） |
| `POST /api/v1/chat` | 聊天 SSE（需 Cookie，**不再传 userId**） |
