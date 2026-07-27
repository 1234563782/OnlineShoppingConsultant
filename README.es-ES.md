# Online Shopping Consultant

Sistema multi-agente de guía de compras inteligente para e-commerce (Spring Boot + Spring AI Alibaba).

## Arquitectura Actual (Versión Inicial)

- `shopping-orchestrator`: Agente orquestador general, expone `POST /api/v1/chat` (SSE streaming) + **Frontend Vue3** (registro/inicio de sesión/chat).
- `shopping-consult-agent`: Agente de consulta y guía de compras (A2A Server).
- `shopping-compare-agent`: Agente de comparación de productos (A2A Server).
- `shopping-memory-service`: Perfil de usuario a largo plazo (MySQL, REST).
- `shopping-catalog-mcp-server`: Herramientas de búsqueda y detalles de productos (MCP) + **REST de normalización de categorías** (llamado por el orchestrator).
- `shopping-inventory-mcp-server`: Herramientas de inventario (MCP).
- `shopping-promotion-mcp-server`: Herramientas de promociones (MCP).

## Uso de Nacos

La versión inicial está alineada con `spring-ai-alibaba-multi-agent-demo-main`:

- **Registro y Descubrimiento A2A**: `consult` se registra como `consult_agent`, `compare` como `compare_agent`; el `orchestrator` los descubre a través de `AgentCardProvider`.
- **Registro y Descubrimiento MCP**: `catalog`/`inventory`/`promotion` se registran como servicios MCP; `consult` los invoca a través de `loadbalancedMcpSyncToolCallbacks`.

## Dependencias de Ejecución

- Redis: Contexto de sesión (`6379`).
- Nacos: Registro y descubrimiento A2A + MCP (`8848`, `9848`).
- MySQL: Perfiles de usuario a largo plazo (base de datos predeterminada: `shopping_consultant`).

Inicie utilizando compose en el directorio raíz:

```bash
docker compose up -d
```

## Puertos de los Módulos

- orchestrator: `8087`
- consult-agent: `8081`
- compare-agent: `8082`
- memory-service: `8086`
- catalog-mcp-server: `8083`
- inventory-mcp-server: `8084`
- promotion-mcp-server: `8085`

## Variables de Entorno

Copie `.env.example` a `.env` y configure según sea necesario:

- `SPRING_AI_DASHSCOPE_API_KEY`
- `NACOS_SERVER_ADDR`
- `NACOS_USERNAME`
- `NACOS_PASSWORD`
- `SHOPPING_MEMORY_DB_URL`
- `SHOPPING_MEMORY_DB_USERNAME`
- `SHOPPING_MEMORY_DB_PASSWORD`
- `SHOPPING_CATALOG_BASE_URL` (El orchestrator llama al catalog para la normalización de categorías, por defecto `http://localhost:8083`)
- `SHOPPING_CATALOG_CONFIDENCE_THRESHOLD` (Opcional, por defecto `0.85`)

> No suba el archivo `.env` a Git.

## Inicialización de MySQL

Utilice los scripts para inicializar la estructura de las tablas y los datos de demostración (el servicio **no** creará las tablas ni cargará los datos automáticamente al iniciar):

```bash
mysql -u root -p < scripts/init-mysql.sql
```

Los módulos que utilizan MySQL acceden a la base de datos mediante **MyBatis-Plus** (paquete `mapper` + `BaseMapper`); la estructura de las tablas se mantiene en `scripts/init-mysql.sql` y no se crean automáticamente.

El script de MySQL incluye: `product_category`, `product`, `product_inventory`, `product_promotion`, `user_account`, `user_memory`.

## PostgreSQL + pgvector (Búsqueda Semántica, Opcional pero Recomendado)

Las tablas vectoriales están **alineadas por sku_id** con los productos de MySQL; los vectores de embedding deben reconstruirse después de que los productos existan en MySQL:

```bash
psql -U postgres -d postgres -f scripts/init-postgres-pgvector.sql
```

Cuando el servicio de catalog esté iniciado y `SHOPPING_VECTOR_ENABLED=true`, reconstruya el índice:

```bash
curl -X POST http://localhost:8083/api/v1/catalog/product-embeddings/rebuild
```

(Requiere `SHOPPING_VECTOR_ADMIN_REBUILD_ENABLED=true` y la configuración de `SPRING_AI_DASHSCOPE_API_KEY`.)

## Frontend (Vue3)

El código fuente se encuentra en `shopping-web/`, y los artefactos de construcción se exportan al directorio `static/` del orchestrator:

```bash
cd shopping-web
npm install
npm run build
```

Durante el desarrollo puede usar `npm run dev` (puerto 5173, con proxy de `/api` hacia el 8087).

Acceda a través del navegador a **http://localhost:8087**, regístrese o inicie sesión primero; el estado de sesión se maneja mediante **httpOnly Cookie** y el Token de sesión se almacena en **Redis** (`auth:token:*`).

## API de Autenticación (orchestrator)

| Interfaz | Descripción |
|------|------|
| `POST /api/v1/auth/register` | Registro, Set-Cookie |
| `POST /api/v1/auth/login` | Inicio de sesión, Set-Cookie |
| `POST /api/v1/auth/logout` | Cerrar sesión, limpia Cookie + Redis |
| `GET /api/v1/auth/me` | Usuario actual (requiere Cookie) |
| `POST /api/v1/chat` | Chat SSE (requiere Cookie, **ya no se envía userId**) |
