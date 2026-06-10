# 会话编排与记忆/搜索改造总览

> 本文记录相对「未改记忆系统、未加固品类/品牌搜索」基线以来的全部改造。  
> 相关文档：[CATEGORY_SLOT_REFACTOR.md](./CATEGORY_SLOT_REFACTOR.md)、[CATEGORY_PATCH_GUARD.md](./CATEGORY_PATCH_GUARD.md)、[OPEN_CLAUDECODE_BORROWINGS.md](./OPEN_CLAUDECODE_BORROWINGS.md)

---

## 1. 改造前基线

系统已具备 P0 骨架（`TurnOutcome`、`UserInputProcessor`、`SessionStateMachine`、品类 Replace/reconcile），但存在以下问题：

| 能力 | 改造前 |
|------|--------|
| 记忆读取 | 每轮全量 `getProfile()`，或 recall 但 `excludeKeys` 恒为空 |
| 记忆写入 | 基本 merge，不区分临时预算/场景与长期偏好 |
| 品类切换 | 有 Replace，但换品类仍继承旧 budget；追问答「学习」时 LLM 可能写回旧品类 |
| 品牌搜索 | Agent 自行决定 `keyword`，无分层兜底 |
| 品牌识别 | 仅靠 LLM 抽取，无规则层 |
| 测试/文档 | 几乎无单测；品类有文档，记忆/品牌无 |

典型故障场景：

1. 电脑 → 手机 → 预算3000 → 学习，又推回电脑  
2. 要小米 3000，先推荣耀，再编造库里没有的小米型号  

---

## 2. 改造后架构（单轮流程）

```
用户输入
  → ContextExtractionService（LLM 抽 patch）
  → CategoryPatchNormalizer
  → CategoryIntentDetector.reconcile（品类规则纠偏）
  → CategoryPatchGuard（品类证据门控）
  → BrandIntentDetector.reconcile（品牌规则纠偏）
  → ContextMergeService.merge（品类 Replace；换品类清 budget）
  → CategoryResolutionService.resolve
  → MemoryRecallExcludePlanner → MemoryClientService.recall（按 session 去重）
  → ConstraintResolver（session 优先 + searchHints）
  → TurnOutcomeResolver → Agent / 追问 / 直接回复
  → LongTermMemoryWriteService + MemoryWriteFilter（写入门控）
```

核心原则：

> **LLM 提议，代码裁决。** 会话槽位管本轮；长期画像管跨会话偏好；搜索兜底在工具层按品牌分层执行。

---

## 3. 阶段一：品类槽位加固

**目标**：防止 LLM 在追问路径（如只答「学习」）静默把品类改回旧值。

| 改动 | 文件 | 作用 |
|------|------|------|
| `CategoryPatchGuard` | 新增 | 无用户原话证据的 LLM 品类替换在 merge 前删除 |
| `CategoryIntentDetector` 扩展 | 修改 | `isCategorySupportedByUserMessage()` |
| `SessionStateMachine` | 修改 | normalize → reconcile → **guard** → merge |
| `ContextMergeService` | 修改 | **换品类不再继承旧 budget** |
| `SessionContextKeys` | 修改 | `CATEGORY_SOURCE_LLM` / `CATEGORY_SOURCE_RULE` |
| 单测 | `CategoryPatchGuardTest`、`ContextMergeServiceTest` | 回归 |
| 文档 | `docs/CATEGORY_PATCH_GUARD.md` | 第三轮品类改造说明 |

### 品类槽位策略（最终）

| 槽位 | 换品类时 |
|------|----------|
| `categoryRaw` | REPLACE |
| `categoryId` 等派生字段 | 清空重算 |
| `budget` | **默认清空**（除非本轮 patch 带新 budget） |
| `scene` / `mustHave` | 清空 |
| `brandPreferences` 等 | 保留 |

---

## 4. 阶段二：记忆工程增强（P1）

**目标**：读少而准，写严而稳。

### 4.1 读路径

| 改动 | 文件 | 作用 |
|------|------|------|
| `MemoryRecallResult` | 新增 DTO | `profileSegments`、`recalledKeys`、`excludeKeys` |
| `MemoryClientService` | 修改 | `recall(userId, query, excludeKeys)`；`recall-top-k` 配置 |
| `MemoryRecallExcludePlanner` | 新增 | session 已有 budget/品牌/scene 时 exclude 对应画像段 |
| `MemoryRecallSupport` | 新增 | session 记录 `recalledMemoryKeys` |
| `UserInputProcessor` | 修改 | `processSlots` → plan exclude → recall → `finalizeWithProfile` |
| `SessionStateMachine` | 修改 | 拆分为 `processSlots()` + `finalizeWithProfile()` |
| `stateDebug.memoryRecall` | 修改 | debug 可见 exclude/recalled/segmentFields |

**exclude 策略说明**：按 **当前 session 已填槽位** 排除画像段（非简单沿用上一轮 recalledKeys），避免「预算3000」轮仍重复灌画像 budget，也避免误排除仍需要的段。

### 4.2 写路径

| 改动 | 文件 | 作用 |
|------|------|------|
| `MemoryWriteFilter` | 新增 | 临时 budget/scene/category 默认不写画像；稳定偏好词（以后/平时/一直）才写 budget |
| `LongTermMemoryWriteService` | 修改 | merge 前后经 filter |

### 4.3 配置

```yaml
# shopping-orchestrator application.yml
shopping:
  memory:
    recall-top-k: 5   # SHOPPING_MEMORY_RECALL_TOP_K
```

### 4.4 单测

- `MemoryWriteFilterTest`
- `MemoryRecallSupportTest`
- `MemoryRecallExcludePlannerTest`

---

## 5. 阶段三：品牌搜索兜底

**目标**：用户指定品牌时，搜索顺序由代码保证，不全靠 Agent prompt。

### 5.1 分层兜底（有 brand keyword 时）

```
1. 同品类 + 同品牌 + 同预算           → exact
2. 同品类 + 同品牌 + 放宽预算         → same_brand_other_price
3. 同品类 + 同预算 + 其他品牌         → same_category_other_brand_same_budget
4. 同品类 + 其他品牌（任意价位）       → same_category_other_brand_any_price
```

无品牌 keyword 时：同品类同预算 → 同品类其他价位 → 跨品类兜底（保留原逻辑）。

### 5.2 与示例数据的对照

`scripts/init-mysql.sql` 手机品类：

| 商品 | 品牌 | 价格 |
|------|------|------|
| 小米 14 | Xiaomi | 3999 |
| 荣耀 200 | Honor | 2699 |
| iPhone 15 | Apple | 5999 |

用户：「小米手机 + 预算约 3000」

- 步骤 1 无命中（预算内无小米）
- 步骤 2 → **小米 14，3999**（`same_brand_other_price`）
- 若库内无小米 → 步骤 3 → **荣耀 200，2699**（并说明无小米）

### 5.3 Orchestrator 侧

| 改动 | 文件 | 作用 |
|------|------|------|
| `BrandIntentDetector` | 新增 | 规则从原话识别小米/华为/荣耀等 → `brandPreferences` |
| `BrandSearchKeywordResolver` | 新增 | 生成 `searchHints.brandKeyword` |
| `ConstraintResolver` | 修改 | 输出 `searchHints`（brandKeyword、budget、fallbackPolicy） |
| `SessionStateMachine` | 修改 | 接入 `brandIntentDetector.reconcileBrandPatch` |
| `ChatController` | 修改 | 强制 Agent 传 `searchHints.brandKeyword` 为 keyword |

### 5.4 Catalog 侧

| 改动 | 文件 | 作用 |
|------|------|------|
| `ProductSearchFallback` | 新增 | 分层搜索逻辑 |
| `CatalogMcpTools` | 修改 | 有 keyword 时走新兜底；返回新 matchType |

### 5.5 Consult Agent

`shopping-consult-agent/src/main/resources/application.yml`：更新 matchType 说明与 keyword 传参要求。

### 5.6 单测

- `BrandIntentDetectorTest`
- `BrandSearchKeywordResolverTest`

---

## 6. 品牌与画像优先级（FAQ）

**Q：我说要小米，长期画像偏好华为，会推华为吗？**

不会（在品牌正确抽取的前提下）。`ConstraintResolver`：**session 优先，画像只补缺**。用户本轮明确品牌时，画像品牌不覆盖。

**Q：品牌怎么识别？**

- 本轮：LLM 抽取 + `BrandIntentDetector` 规则（小米/华为/荣耀等别名）
- 长期：画像存储的 `brandPreferences` 字符串
- 搜索：`searchHints.brandKeyword` 传给 `searchProduct.keyword`
- **尚无**品牌 normalize API / `brandId`（与品类 `categoryId` 不同）

**Q：`excludeKeys` 会误伤吗？**

按 session 已填槽位排除，而非简单排除上一轮全部 recalledKeys。session 尚无 budget 时，仍会召回画像 budget 段。

---

## 7. 工程配套

| 项 | 内容 |
|----|------|
| `shopping-orchestrator/pom.xml` | 增加 `spring-boot-starter-test` |
| `docs/CATEGORY_SLOT_REFACTOR.md` | §17 第三轮 Guard 交叉引用 |

---

## 8. 验收场景

| # | 场景 | 期望 |
|---|------|------|
| 1 | 电脑 → 手机 → 预算3000 → 学习 | 始终 `cat_phone`，不推回电脑 |
| 2 | 仅答「学习」（session 已是手机） | `categoryReplaced=false`，`scene=学习` |
| 3 | 小米 + 预算3000 | `same_brand_other_price` → 小米 14 3999；不说预算内有小米 |
| 4 | 库内无小米 + 预算3000 | `same_category_other_brand_same_budget` → 荣耀等，并说明无小米 |
| 5 | 只说「预算3000」「学习」 | 长期画像不写入 scene/budget（除非稳定偏好词） |
| 6 | 推荐手机且 session 无品牌 | 可召回画像 brands 段作兜底 |

调试：SSE `done.debug` 关注 `stateDebug.memoryRecall`、`resolvedConstraints.searchHints`、`categoryReplaced`。

**注意**：已被污染的旧 Redis session 需新开对话或清 session 后再测。

---

## 9. 已知未做项

| 项 | 说明 |
|----|------|
| 回复校验器 | Agent 仍可能编造工具未返回的型号；需 `ProductReplyValidator` 或模板渲染 |
| Orchestrator 先搜后答 | 搜索仍主要由 Agent 调 MCP；`searchHints` 已缓解漏传 keyword |
| 品牌别名配置化 | `BrandIntentDetector` 别名在代码内，未外置 yml |
| 记忆并行 prefetch | 为保证 exclude 准确，改为 slots 后再 recall |
| 品牌 normalize / brandId | 未实现 |
| SSE `tool_start` / `state` 事件 | P1 可观测性，未做 |

---

## 10. 相关文件索引

### orchestrator

```
service/
├── SessionStateMachine.java      # slots + finalize；Guard/Brand reconcile
├── UserInputProcessor.java       # recall 编排
├── ContextMergeService.java      # 品类 Replace；换品类清 budget/recalledKeys
├── CategoryPatchGuard.java
├── CategoryIntentDetector.java
├── BrandIntentDetector.java
├── BrandSearchKeywordResolver.java
├── ConstraintResolver.java       # searchHints
├── MemoryClientService.java
├── MemoryRecallExcludePlanner.java
├── MemoryWriteFilter.java
├── LongTermMemoryWriteService.java
├── TurnOutcomeResolver.java
└── ClarificationBuilder.java

controller/ChatController.java
support/SessionContextKeys.java, MemoryRecallSupport.java
dto/MemoryRecallResult.java, SlotProcessResult.java, TurnDecision.java, TurnOutcome.java

test/... (8 个单测类)
```

### catalog-mcp-server

```
search/ProductSearchFallback.java
CatalogMcpTools.java
```

### memory-service

本轮基本未改（P0 已有 `POST /api/v1/memory/{userId}/recall`）。

### docs

- `CATEGORY_SLOT_REFACTOR.md` — 品类状态机历程  
- `CATEGORY_PATCH_GUARD.md` — 品类证据门控  
- `OPEN_CLAUDECODE_BORROWINGS.md` — P0/P1 借鉴路线图  
- **本文** — 总览 changelog  

---

## 11. 总结

| 维度 | 改造前 | 改造后 |
|------|--------|--------|
| 品类追问 | LLM 可静默改品类 | Guard 证据门控 |
| 换品类 budget | 继承旧预算 | 默认清空 |
| 记忆读 | 全量/无 exclude | 按 query + session 去重 recall |
| 记忆写 | 易污染画像 | WriteFilter 门控 |
| 品牌搜索 | Agent 随意 keyword | 分层兜底 + searchHints |
| 品牌识别 | 仅 LLM | LLM + 规则别名 |

一句话：**状态机加固 + 记忆读写治理 + 品牌搜索下沉**，把「记什么、搜什么、能推荐什么」从 LLM 手里收回到 Orchestrator 与工具层。
