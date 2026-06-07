# Architecture

## Service Topology

- `shopping-orchestrator` (8087): chat API, Web UI, Redis session store, A2A client discovery.
- `shopping-consult-agent` (8081): A2A server (`consult_agent`), MCP client via Nacos.
- `shopping-memory-service` (8086): long-term profile memory REST service (H2).
- `catalog-mcp-server` (8083): product search/detail MCP tools; **REST** `GET /api/v1/categories/normalize` for category normalization (used by orchestrator).
- `inventory-mcp-server` (8084): stock tools.
- `promotion-mcp-server` (8085): promotion tools.

## Nacos Responsibilities

- A2A agent-card registry/discovery for `consult_agent`.
- MCP service registry/discovery for tool servers.

## Request Flow

1. Client calls `POST /api/v1/chat` on orchestrator.
2. Orchestrator loads memory profile from memory-service.
3. Orchestrator loads recent session turns from Redis.
4. Orchestrator runs context extraction + session merge, then **category normalization** (HTTP to catalog `GET /api/v1/categories/normalize`) and writes `categoryId` / `categoryResolution` into session before building `effectiveContext`.
5. Orchestrator routes to `consult_agent` through A2A.
6. Consult agent calls MCP tools through Nacos-discovered tool callbacks.
7. Orchestrator appends turns back to Redis and returns reply.
