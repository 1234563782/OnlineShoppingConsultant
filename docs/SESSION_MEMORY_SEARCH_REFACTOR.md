# 会话 / 记忆 / 搜索改造总览

> 本文汇总相对「尚未改造记忆系统、尚未加固品类与搜索」基线以来，项目所做的全部增量改造。  
> 相关专题文档：  
> - [CATEGORY_SLOT_REFACTOR.md](./CATEGORY_SLOT_REFACTOR.md) — 品类槽位 Replace 与 reconcile 历程  
> - [CATEGORY_PATCH_GUARD.md](./CATEGORY_PATCH_GUARD.md) — 品类证据门控  
> - [SEARCH_FIRST_PREFETCH.md](./SEARCH_FIRST_PREFETCH.md) — Orchestrator 先搜后答（预搜索）  
> - [PROMPT_MANAGEMENT.md](./PROMPT_MANAGEMENT.md) — Prompt Git 文件化与分层加载方案  
> - [OPEN_CLAUDECODE_BORROWINGS.md](./OPEN_CLAUDECODE_BORROWINGS.md) — P0/P1 借鉴路线图  

---

## 1. 改造前基线（问题起点）

当时系统已具备 P0 骨架（`TurnOutcome`、`UserInputProcessor`、`SessionStateMachine`、品类 Replace/reconcile、记忆 `recall` API 雏形），但存在以下缺口：

| 能力 | 改造前状态 |
|------|------------|
| 记忆读取 | 每轮全量 `getProfile()`，或 `recall` 但 `excludeKeys` 恒为空 |
| 记忆写入 | 基本 merge，不区分临时预算/场景与长期偏好 |
| 品类切换 | 有 Replace，但换品类仍继承旧 `budget`；追问只答场景时 LLM 可能写回旧品类 |
| 品牌搜索 | Agent 自行决定 `searchProduct.keyword`，无分层兜底 |
| 品牌识别 | 仅靠 LLM 抽取，无规则纠偏 |
| 测试 / 文档 | 品类有文档，记忆与品牌几乎无单测 |

### 1.1 典型故障（用户实测）

| # | 路径 / 现象 | 根因归类 |
|---|-------------|----------|
| 1 | 电脑 → 手机 → 预算3000 → **学习** → 又推电脑 | LLM 幻觉写状态 + 换品类 budget 污染 |
| 2 | 要小米 3000，先推荣耀 | 品牌未硬编码进搜索；无品牌时分预算内任意命中 |
| 3 | 强调小米后推「小米 14 Lite」等库外型号 | 纯靠 prompt 约束 Agent，无服务端校验 |

### 1.2 当时架构（简化）

```
用户输入
  → LLM extractPatch
  → mergeSessionPatch
  → getProfile / recall（全量或弱筛选）
  → buildEffectiveContext
  → Agent 自行 searchProduct + 自由生成回复
```

核心问题：**LLM 既提议状态，又执行搜索与话术，缺少代码层门控与搜索策略。**

---

## 2. 改造后目标架构

```
用户输入
  → LLM extractPatch（只产出候选 patch）
  → CategoryPatchNormalizer
  → CategoryIntentDetector.reconcile（品类规则纠偏）
  → CategoryPatchGuard（品类证据门控）
  → BrandIntentDetector.reconcile（品牌规则纠偏）
  → ContextMergeService.merge（品类 Replace；换品类清 budget）
  → CategoryResolutionService.resolve
  → processSlots 完成
  → MemoryRecallExcludePlanner → recall（按 session 去重）
  → finalizeWithProfile（ConstraintResolver + searchHints）
  → TurnOutcomeResolver 路由
  → READY_FOR_AGENT：Orchestrator 预搜索（见 SEARCH_FIRST_PREFETCH.md）
  → Agent 基于 prefetchedSearchResult 写话术（预搜索成功时禁止 searchProduct）
  → MemoryWriteFilter → 长期画像写入
```

设计原则：

> **LLM 提议，代码裁决；会话槽位管本轮，画像管跨会话偏好；搜索策略下沉到工具层。**

---

## 3. 阶段一：品类槽位加固

**解决问题**：多轮切换品类后，追问只答「学习」等场景词时，品类回退到电脑；换品类后仍带旧预算。

| 改动 | 文件 | 说明 |
|------|------|------|
| 证据门控 | `CategoryPatchGuard.java`（新） | 无用户原话证据的 LLM 品类替换在 merge 前删除 |
| 品类支持判定 | `CategoryIntentDetector.java` | `isCategorySupportedByUserMessage()` |
| 接入状态机 | `SessionStateMachine.java` | reconcile 之后、merge 之前调用 Guard |
| 换品类清 budget | `ContextMergeService.java` | `preserveFieldsOnCategoryReplace` 不再复制旧 budget |
| 来源常量 | `SessionContextKeys.java` | `CATEGORY_SOURCE_LLM` / `CATEGORY_SOURCE_RULE` |
| 单测 | `CategoryPatchGuardTest`、`ContextMergeServiceTest` | |
| 文档 | `CATEGORY_PATCH_GUARD.md` | 专题说明 |

详见 [CATEGORY_PATCH_GUARD.md](./CATEGORY_PATCH_GUARD.md)。

---

## 4. 阶段二：记忆工程增强（P1）

**解决问题**：画像噪音大、临时上下文写入长期记忆、重复召回同一段。

### 4.1 读路径：按 session 去重召回

| 改动 | 文件 | 说明 |
|------|------|------|
| 召回结果 DTO | `MemoryRecallResult.java`（新） | `profileSegments` + `recalledKeys` + `excludeKeys` |
| 客户端增强 | `MemoryClientService.java` | 支持 `excludeKeys`；`recall-top-k` 可配置 |
| exclude 规划 | `MemoryRecallExcludePlanner.java`（新） | session 已有 budget → 不召回 budget 段；已有品牌偏好 → 不召回 brands 段等 |
| session 记录 | `MemoryRecallSupport.java`（新） | `recalledMemoryKeys` 写入 session（调试） |
| 流程调整 | `UserInputProcessor.java` | `processSlots` → plan exclude → `recall` → `finalizeWithProfile` |
| 状态机拆分 | `SessionStateMachine.java` | `processSlots()` + `finalizeWithProfile()` |
| 换品类副作用 | `ContextMergeService.java` | 换品类时清除 `recalledMemoryKeys` |
| 调试字段 | `stateDebug.memoryRecall` | 可见 exclude / recalled / segmentFields |
| 配置 | `application.yml` | `shopping.memory.recall-top-k` |

**读路径顺序**：

```
1. processSlots（更新 session，含 budget / brand / scene）
2. MemoryRecallExcludePlanner.plan(session) → excludeKeys
3. memory-service POST /recall
4. finalizeWithProfile(profileSegments)
```

**注意**：为保证 exclude 基于最新 session，recall 在 `processSlots` 之后执行（正确性优先于并行 prefetch）。

### 4.2 写路径：长期记忆门控

| 改动 | 文件 | 说明 |
|------|------|------|
| 写入过滤 | `MemoryWriteFilter.java`（新） | 默认不写临时 budget/scene/category；稳定偏好词（以后/平时/一直）才写 budget |
| 接入写入 | `LongTermMemoryWriteService.java` | extraction / session / merged patch 均经 filter |

| 字段 | 默认是否写入长期画像 |
|------|---------------------|
| categoryRaw / scene | 否 |
| 本轮 budget（「预算3000」） | 否 |
| budget（「以后都 3000」） | 是 |
| brandPreferences / dislikes（longTermMemoryPatch） | 是 |

### 4.3 单测

- `MemoryWriteFilterTest`
- `MemoryRecallSupportTest`
- `MemoryRecallExcludePlannerTest`

---

## 5. 阶段三：品牌搜索分层兜底

**解决问题**：指定小米却在预算内推荣耀；库内无匹配时 Agent 编造型号。

### 5.1 期望兜底策略（产品规则）

当用户指定品牌（如小米）时，`searchProduct` 按序：

| 步骤 | 条件 | matchType |
|------|------|-----------|
| 1 | 同品类 + 同品牌 + 同预算 | `exact` |
| 2 | 同品类 + 同品牌 + 放宽预算 | `same_brand_other_price` |
| 3 | 同品类 + 同预算 + 其他品牌 | `same_category_other_brand_same_budget` |
| 4 | 同品类 + 其他品牌（任意价位） | `same_category_other_brand_any_price` |

无品牌时：同品类同预算 → 同品类其他价位 → 跨品类兜底（保留原逻辑）。

### 5.2 实现

| 改动 | 模块 / 文件 | 说明 |
|------|-------------|------|
| 兜底引擎 | `ProductSearchFallback.java`（新，catalog） | 上述分层逻辑 |
| 工具入口 | `CatalogMcpTools.java` | 有 `keyword` 时走新兜底；响应含 `brandKeyword` |
| 品牌规则识别 | `BrandIntentDetector.java`（新，orchestrator） | 原话识别小米/华为/荣耀等 → `brandPreferences` |
| 搜索关键词 | `BrandSearchKeywordResolver.java`（新） | 从 `brandPreferences` / `mustHave` 生成 keyword |
| 搜索提示 | `ConstraintResolver.java` | 输出 `searchHints`（brandKeyword、budget、fallbackPolicy） |
| 状态机 | `SessionStateMachine.java` | merge 前 `brandIntentDetector.reconcileBrandPatch` |
| Prompt | `ChatController.java`、`consult-agent/application.yml` | 强制传 `searchHints.brandKeyword`；按 matchType 解释 |

### 5.3 与示例数据的对照

`scripts/init-mysql.sql` 手机品类：

| 商品 | 品牌 | 价格 |
|------|------|------|
| 小米 14 | Xiaomi | 3999 |
| 荣耀 200 | Honor | 2699 |
| iPhone 15 | Apple | 5999 |

用户：**小米 + 预算约 3000**

- 步骤 1 无命中（小米 14 超预算）
- 步骤 2 → **小米 14，3999**（`same_brand_other_price`）
- 仅当库内完全没有小米时，才进入步骤 3 → 荣耀 200 等

### 5.4 单测

- `BrandIntentDetectorTest`
- `BrandSearchKeywordResolverTest`

---

## 6. 阶段四：工程配套

| 项 | 内容 |
|----|------|
| `shopping-orchestrator/pom.xml` | 增加 `spring-boot-starter-test` |
| `shopping-orchestrator` 单测 | 共 8 个测试类（品类 / 记忆 / 品牌） |
| `docs/CATEGORY_SLOT_REFACTOR.md` | §17 增加第三轮交叉引用 |

---

## 7. 三层状态与职责（改造后）

| 层 | 存储 | 内容 | 写入方 | 读取方 |
|----|------|------|--------|--------|
| Scratchpad | Redis `sessionContext` | categoryId、budget、scene、brand、pendingField | Orchestrator | Orchestrator + Agent（经 resolvedConstraints） |
| 长期画像 | MySQL `profileJson` | 稳定品牌偏好、默认预算等 | Orchestrator（经 WriteFilter） | recall 分段 |
| Transcript | Redis `turns` | 用户可见对话 | Orchestrator | 展示 / 调试 |
| Agent Thread | `sessionId:turn:N` | Agent 图状态 | Consult Agent | Consult Agent |

合并规则（不变）：**会话优先于画像**（`ConstraintResolver`）。

---

## 8. 验收场景

| # | 场景 | 期望 |
|---|------|------|
| 1 | 电脑 → 手机 → 预算3000 → 学习 | `categoryId=cat_phone`，`scene=学习`，不推电脑 |
| 2 | 换品类（电脑→手机） | `categoryReplaced=true`，旧 budget 清空 |
| 3 | 仅答「学习」 | `categoryReplaced=false`，品类不变 |
| 4 | 小米 + 预算3000 | `searchHints.brandKeyword=小米`；推小米 14 3999 或说明超预算 |
| 5 | 库内无小米 | `same_category_other_brand_*`；推其他品牌并说明无小米 |
| 6 | 只说「预算3000」「学习」 | 长期画像 `profileWritten` 不含 scene/临时 budget |
| 7 | debug | `stateDebug.memoryRecall` 可见 exclude / recalled |

**注意**：已被旧 session 污染的对话需新开 session 或清 Redis 后再测。

---

## 9. 配置项

```yaml
# shopping-orchestrator application.yml
shopping:
  memory:
    base-url: http://localhost:8086
    recall-top-k: 5   # 环境变量 SHOPPING_MEMORY_RECALL_TOP_K
```

---

## 10. 相关文件索引

### shopping-orchestrator

```
controller/ChatController.java
service/
  SessionStateMachine.java
  UserInputProcessor.java
  ContextMergeService.java
  ContextExtractionService.java
  ConstraintResolver.java
  CategoryIntentDetector.java
  CategoryPatchGuard.java
  CategoryPatchNormalizer.java
  BrandIntentDetector.java
  BrandSearchKeywordResolver.java
  MemoryClientService.java
  MemoryWriteFilter.java
  MemoryRecallExcludePlanner.java
  LongTermMemoryWriteService.java
  TurnOutcomeResolver.java
  ClarificationBuilder.java
support/
  SessionContextKeys.java
  MemoryRecallSupport.java
dto/
  MemoryRecallResult.java
  SlotProcessResult.java
  TurnOutcome.java
  TurnDecision.java
test/...（8 个单测类）
```

### shopping-catalog-mcp-server

```
CatalogMcpTools.java
search/ProductSearchFallback.java
```

### shopping-memory-service

本轮未改核心逻辑（P0 已有 `POST /api/v1/memory/{userId}/recall`）。

### shopping-consult-agent

```
src/main/resources/application.yml   # searchProduct / matchType 说明更新
```

### docs

```
CATEGORY_SLOT_REFACTOR.md
CATEGORY_PATCH_GUARD.md
SESSION_MEMORY_SEARCH_REFACTOR.md   # 本文
SEARCH_FIRST_PREFETCH.md            # 先搜后答专题
```

---

## 11. 尚未完成（后续可做）

| 优先级 | 项 | 说明 |
|--------|-----|------|
| P0 | `ProductReplyValidator` | 校验回复商品名/价格必须来自 `prefetchedSearch.products`，防编造 |
| ~~P1~~ | ~~Orchestrator 先搜后答~~ | **已完成**，见 [SEARCH_FIRST_PREFETCH.md](./SEARCH_FIRST_PREFETCH.md) |
| P1 | 品牌别名配置外置 | `shopping.brands.aliases` 替代代码内常量 |
| P2 | recall 并行 prefetch | 在 exclude 准确前提下与 slots 并行 |
| P2 | SSE `tool_start` / `state` 事件 | 前端可见工具执行与槽位变更 |
| P2 | `sessionContext` 强类型化 | 减少 Map 魔法字符串 |

---

## 12. 总结对比表

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 品类 Replace | 有，但不稳 | + Guard + 换品类清 budget |
| 记忆读取 | 全量 / 弱筛选 | 分段 recall + session 去重 exclude |
| 记忆写入 | 无门控 | MemoryWriteFilter |
| 品牌识别 | 仅 LLM | + BrandIntentDetector 规则 |
| 搜索兜底 | Agent 自行 keyword | ProductSearchFallback 分层 |
| 搜索执行 | Agent 调 MCP | Orchestrator 预搜索 + Agent 只叙述 |
| 搜索参数 | 无 searchHints | resolvedConstraints.searchHints |
| 单测 | 几乎没有 | 9 个测试类（含 CatalogSearchPrefetchServiceTest） |
| 文档 | 品类专题 | + Guard + 先搜后答 + 本文总览 |

一句话：

> **从「LLM 包办一切」改为「LLM 提议 + 状态机裁决 + 工具层搜索策略 + 画像读写门控」。**
