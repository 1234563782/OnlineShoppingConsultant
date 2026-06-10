# 导购编排与记忆系统改造总览

> 本文记录相对「记忆系统增强前」基线所完成的改造，涵盖品类槽位、记忆读写、品牌搜索三层。  
> 相关专题文档：[CATEGORY_SLOT_REFACTOR.md](./CATEGORY_SLOT_REFACTOR.md)、[CATEGORY_PATCH_GUARD.md](./CATEGORY_PATCH_GUARD.md)、[OPEN_CLAUDECODE_BORROWINGS.md](./OPEN_CLAUDECODE_BORROWINGS.md)

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

**典型故障场景：**

1. 电脑 → 手机 → 预算3000 → 学习，又推回电脑  
2. 要小米 3000，先推荣耀，再编造库里没有的小米型号  

---

## 2. 改造后架构（一句话）

**改前**：用户输入 → LLM 抽一切 → 直接 merge → 全量画像 → Agent 自己搜、自己编  

**改后**：

```
用户输入
  → LLM 抽 patch
  → 品类/品牌 规则纠偏
  → 品类 Guard（证据门控）
  → 槽位状态机 merge（换品类清 budget）
  → 按 session 去重 recall 画像
  → 生成 searchHints（品牌 + 预算）
  → Agent 带 keyword 调 searchProduct（分层兜底）
  → 写入画像前 filter
```

---

## 3. 阶段一：品类槽位加固

**目标**：防止 LLM 幻觉静默改写 session 品类（如追问答「学习」又变回电脑）。

| 改动 | 文件 | 作用 |
|------|------|------|
| `CategoryPatchGuard` | 新增 | LLM 品类替换须有用户原话证据；`rule` 来源放行 |
| `CategoryIntentDetector` 扩展 | 修改 | `isCategorySupportedByUserMessage()` |
| `SessionStateMachine` | 修改 | reconcile 与 merge 之间接入 Guard |
| `ContextMergeService` | 修改 | **换品类时不再继承旧 budget** |
| `SessionContextKeys` | 修改 | `CATEGORY_SOURCE_LLM` / `CATEGORY_SOURCE_RULE` |
| 单测 | `CategoryPatchGuardTest`、`ContextMergeServiceTest` | 回归品类/budget |
| 文档 | `CATEGORY_PATCH_GUARD.md` | 第三轮改造说明 |

**关键规则**：品类替换不仅需要 patch 里有新值，还需要用户本轮原话有证据；`categorySource=rule` 不受限。

---

## 4. 阶段二：记忆工程增强（P1）

**目标**：读少而准，写严而稳；会话管本轮，画像管长期偏好。

### 4.1 读路径

| 改动 | 作用 |
|------|------|
| `MemoryRecallResult` DTO | 含 `profileSegments`、`recalledKeys`、`excludeKeys` |
| `MemoryClientService.recall()` | 支持 `excludeKeys`；`recall-top-k` 可配置 |
| `MemoryRecallExcludePlanner` | session 已有 budget/brand/scene 时 exclude 对应画像段 |
| `MemoryRecallSupport` | session 记录 `recalledMemoryKeys`（`SessionContextKeys.RECALLED_MEMORY_KEYS`） |
| `UserInputProcessor` | `processSlots` → plan exclude → recall → `finalizeWithProfile` |
| `SessionStateMachine` 拆分 | `processSlots()` + `finalizeWithProfile()` |
| `SlotProcessResult` DTO | slots 与 profile 合并前的中间结果 |
| `stateDebug.memoryRecall` | debug 可见 exclude/recalled/segmentFields |

**注意**：为保证 exclude 基于更新后的 session，recall 在 `processSlots` 之后执行（未做并行 prefetch）。

### 4.2 写路径

| 改动 | 作用 |
|------|------|
| `MemoryWriteFilter` | 临时 budget/scene/category 默认不写画像；稳定偏好词（以后/平时/一直）才写 budget |
| `LongTermMemoryWriteService` | merge 前后经 filter |

### 4.3 会话与画像优先级

- `ConstraintResolver`：**session 优先，profile 只补缺**
- `MemoryRecallExcludePlanner`：session 已有品牌时，不重复召回画像 brands 段（避免重复，非强制推画像品牌）

### 4.4 测试

- `MemoryWriteFilterTest`
- `MemoryRecallSupportTest`
- `MemoryRecallExcludePlannerTest`

### 4.5 配置

```yaml
shopping:
  memory:
    recall-top-k: 5   # SHOPPING_MEMORY_RECALL_TOP_K
```

---

## 5. 阶段三：品牌搜索兜底

**目标**：搜索策略由代码保证，不全靠 Agent prompt。

### 5.1 分层兜底（有品牌 keyword 时）

```
1. 同品类 + 同品牌 + 同预算           → exact
2. 同品类 + 同品牌 + 放宽预算         → same_brand_other_price
3. 同品类 + 同预算 + 其他品牌         → same_category_other_brand_same_budget
4. 同品类 + 其他品牌（任意价位）       → same_category_other_brand_any_price
```

**示例（当前库数据）**：手机仅小米 14(3999)、荣耀 200(2699)、iPhone 15(5999)。

| 用户需求 | 预期 matchType | 预期推荐 |
|----------|----------------|----------|
| 小米 + 预算约 3000 | `same_brand_other_price` | 小米 14，3999（说明超预算） |
| 库里无小米 | `same_category_other_brand_same_budget` | 荣耀 200，2699（说明无小米） |

### 5.2 Orchestrator 侧

| 改动 | 作用 |
|------|------|
| `BrandIntentDetector` | 规则从原话识别小米/华为/荣耀等 → `brandPreferences` |
| `BrandSearchKeywordResolver` | 生成 `searchHints.brandKeyword` |
| `ConstraintResolver.attachSearchHints()` | 输出 `searchHints`（brandKeyword、budget、fallbackPolicy） |
| `SessionStateMachine` | 接入 `brandIntentDetector.reconcileBrandPatch()` |
| `ChatController.buildUserInput()` | 强制传 `searchHints.brandKeyword` 为 keyword |

### 5.3 Catalog 侧

| 改动 | 作用 |
|------|------|
| `ProductSearchFallback` | 实现分层兜底逻辑 |
| `CatalogMcpTools` | 有 keyword 时走新兜底；响应含 `brandKeyword`、新 matchType |

### 5.4 Consult Agent

`shopping-consult-agent/src/main/resources/application.yml`：更新 matchType 说明与 keyword 传参要求。

### 5.5 测试

- `BrandIntentDetectorTest`
- `BrandSearchKeywordResolverTest`

---

## 6. 工程配套

| 项 | 内容 |
|----|------|
| `shopping-orchestrator/pom.xml` | `spring-boot-starter-test` |
| `docs/CATEGORY_SLOT_REFACTOR.md` | 第三轮 Guard 交叉引用 |

---

## 7. 涉及文件索引

### shopping-orchestrator

```
service/
  SessionStateMachine.java      # Guard、品牌 reconcile、slots/finalize 拆分
  UserInputProcessor.java       # recall 编排
  ContextMergeService.java      # 换品类清 budget、清 recalledMemoryKeys
  ConstraintResolver.java       # searchHints
  MemoryClientService.java      # MemoryRecallResult、excludeKeys
  LongTermMemoryWriteService.java
  CategoryPatchGuard.java       # 新增
  CategoryIntentDetector.java
  BrandIntentDetector.java      # 新增
  BrandSearchKeywordResolver.java
  MemoryWriteFilter.java
  MemoryRecallExcludePlanner.java
  TurnOutcomeResolver.java
  ClarificationBuilder.java
  SmallTalkReplyBuilder.java
controller/ChatController.java
support/SessionContextKeys.java, MemoryRecallSupport.java
dto/MemoryRecallResult.java, SlotProcessResult.java, TurnDecision.java, TurnOutcome.java
test/...                        # 8 个单测类
```

### shopping-catalog-mcp-server

```
search/ProductSearchFallback.java   # 新增
CatalogMcpTools.java                # 品牌兜底接入
```

### shopping-memory-service

本轮基本未改（P0 已有 `POST /api/v1/memory/{userId}/recall`）。

### docs

- `CATEGORY_PATCH_GUARD.md`（新增）
- `CATEGORY_SLOT_REFACTOR.md`（补充第三轮）
- `ORCHESTRATION_AND_MEMORY_CHANGELOG.md`（本文）

---

## 8. 验收场景

| # | 场景 | 期望 |
|---|------|------|
| 1 | 电脑 → 手机 → 预算3000 → 学习 | 始终 `cat_phone`，不推回电脑 |
| 2 | 追问答「学习」 | `categoryReplaced=false`，`scene=学习` |
| 3 | 换品类 | 旧 budget 清空；`categoryReplaced=true` |
| 4 | 画像有鞋码，问「推荐手机」 | recall 不重复灌无关 notes（视 exclude 规则） |
| 5 | 只说「预算3000」「学习」 | 长期画像默认不写 scene/budget |
| 6 | 小米 + 预算3000 | `same_brand_other_price` → 小米 14 3999 |
| 7 | 库里无小米 | `same_category_other_brand_*` → 其他品牌并说明 |

**调试**：SSE `done.debug.stateDebug` 关注 `memoryRecall`、`categoryReplaced`、`resolvedConstraints.searchHints`。

**注意**：已被污染的旧 Redis session 需新开对话或清 session 后再测。

---

## 9. 尚未完成（后续可做）

| 项 | 说明 |
|----|------|
| `ProductReplyValidator` | 校验回复商品名/价格必须来自工具 JSON，防编造 |
| Orchestrator 先搜后答 | 搜索由 Orchestrator 执行，Agent 只生成话术 |
| 品牌别名配置化 | `BrandIntentDetector` 外置到 `application.yml` |
| 记忆并行 prefetch | 在 exclude 准确前提下与 slots 并行 |
| SSE `tool_start` / `state` | 可观测性增强 |

---

## 10. 三句话总结（可用于 PR）

1. **状态机加固**：品类/预算/追问路径不再被 LLM 幻觉带偏。  
2. **记忆读写治理**：按需召回、写入过滤，会话与画像职责分离。  
3. **搜索策略下沉**：品牌分层兜底 + 规则识别，减少推错品牌与编造商品。

---

*文档版本：2026-06-10 · 对应当前工作区未提交改动*
