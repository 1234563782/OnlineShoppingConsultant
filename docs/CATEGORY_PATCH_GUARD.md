# 品类槽位证据门控改造说明

> 本文记录第三轮针对「多轮对话品类错乱」的改造思路与实现细节。  
> 前置背景见 [CATEGORY_SLOT_REFACTOR.md](./CATEGORY_SLOT_REFACTOR.md)（槽位 Replace、reconcile、threadId 隔离等）。

---

## 1. 问题现象

### 1.1 用户路径（复现截图）

| 轮次 | 用户输入 | 期望 | 实际（改造前） |
|------|----------|------|----------------|
| 1 | 我想买电脑 | 进入电脑导购 | 正常 |
| 2 | 先看看 | 按多价位推电脑 | 正常 |
| 3 | 我想看看手机 | 切到手机 | 正常 |
| 4 | 预算3000 | 绑定手机预算，追问场景 | 正常（「这个**手机**主要用在什么场景？」） |
| 5 | 学习 | 手机 + 3000 + 学习 → 推手机 | **又推电脑**，且出现 `searchProduct 返回 matchType 为 same_keyword_other_price` |

关键矛盾：**第 4 轮已明确是手机**，第 5 轮用户只回答了场景词「学习」，不应发生品类回退。

### 1.2 两类叠加问题

1. **预算污染**：电脑预算 3000 在切到手机后仍可能被继承，导致搜错价格段。
2. **LLM 幻觉写状态**：在 `pendingField=scene` 的追问路径下，抽取器可能输出 `categoryRaw=电脑`（沿用更早会话），状态机直接 merge，品类被静默替换。

---

## 2. 根因分析

### 2.1 单轮处理链路（改造前）

```
extractPatch / extractPendingFieldPatch
  → CategoryPatchNormalizer
  → CategoryIntentDetector.reconcileCategoryPatch
  → ContextMergeService.mergeSessionPatch   ← 只要 patch 里有不同 categoryRaw 就 Replace
  → CategoryResolutionService.resolve
  → Agent(searchProduct)
```

第二轮改造已解决「用户明确说换品类但 LLM 不写 categoryRaw」的问题（`reconcile` + 子串检测）。  
**未解决**的是反向问题：**用户没说换品类，LLM 却写了旧品类**。

### 2.2 追问路径为何更容易翻车

当 `pendingField=scene` 时，走 `extractPendingFieldPatch()`。Prompt 允许「即使没回答 pendingField，也抽取其它购物信息」。  
LLM 看到 session 里曾有「电脑」历史，容易在只回答「学习」时仍输出 `categoryRaw: "电脑"`。

`CategoryIntentDetector` 的 `reconcile` 只在**用户原话**里检测到新品类时才覆盖 patch；对「学习」检测不到新品类，**不会纠正 LLM 的错误写入**。

`ContextMergeService.shouldReplaceCategory()` 只判断 patch 是否有与 session 不同的 `categoryRaw`，**不校验该值是否来自用户本轮原话**。

### 2.3 预算继承

`preserveFieldsOnCategoryReplace()` 曾把旧 `budget` 复制到新品类 session，与文档 §8「换品类默认清空 budget」不一致，会造成「手机导购却带电脑 3000 预算」的检索与话术污染。

---

## 3. 主流方案选型

业界稳定的多轮导购/对话系统通常采用 **「LLM 提议 + 代码裁决」**，而不是让 LLM 直接写持久状态：

| 做法 | 说明 | 本项目 |
|------|------|--------|
| 只靠加长 Agent prompt | 上游 `categoryId` 错了，下游必错 | 不采用 |
| 槽位状态机 + Replace 语义 | `category` 可替换，派生字段失效重算 | 已采用（前两轮） |
| 规则 reconcile | 用户原话显式换品类时强制覆盖 LLM | 已采用 |
| **证据门控（Evidence Gating）** | LLM 写的关键槽位必须有用户原话证据才允许写入 | **本轮新增** |
| 换品类清 budget | 预算默认不跨品类继承 | **本轮修正** |
| Agent thread 隔离 | 避免图状态延续旧推荐语境 | 已采用 |

核心原则：

> **LLM 只产出候选 patch；是否允许替换品类，由后端根据用户本轮输入证据决定。**

---

## 4. 改造思路

### 4.1 设计目标

1. `学习`、`办公`、`通勤` 等**纯场景回答**不得触发品类 Replace。
2. `我想看看手机`、`换成电脑` 等**显式换品类**仍须正常工作。
3. 规则层（`categorySource=rule`）检测结果优先于 LLM，不被门控误杀。
4. 换品类时清空旧 budget；用户本轮若同时说新预算，仍正常写入。
5. 改动面小：在 merge 前增加一层 Guard，不重写整条管道。

### 4.2 插入位置

在 `SessionStateMachine.process()` 中，**merge 之前**：

```
normalize → reconcile → 【CategoryPatchGuard】 → merge → resolve
```

顺序原因：

- 先 `reconcile`：用规则从用户原话写入正确品类（`categorySource=rule`）。
- 再 `guard`：删掉 LLM 幻觉导致的**无证据**品类替换。
- 最后 `merge`：只对通过门控的 patch 做状态合并。

---

## 5. 具体改动

### 5.1 `CategoryPatchGuard`（新增）

文件：`shopping-orchestrator/.../service/CategoryPatchGuard.java`

`removeUnsupportedCategoryReplace(userMessage, sessionContext, patch)` 逻辑：

| 条件 | 行为 |
|------|------|
| session 尚无品类 | 不拦截（首轮设品类） |
| patch 无 `categoryRaw` | 不拦截 |
| `categorySource=rule` | **放行**（规则层已基于用户原话） |
| 候选品类与 session 相同 | 放行（非 Replace） |
| 候选品类有用户原话证据 | 放行 |
| 否则 | **删除** `categoryRaw` 与 `categorySource`，保留 scene/budget 等其它字段 |

「有证据」判定委托 `CategoryIntentDetector.isCategorySupportedByUserMessage()`：

1. 用户原话与候选品类字面重叠（如原话含「手机」）；
2. 或 `detectCategoryRaw(原话)` 归一化后与候选品类等价。

### 5.2 `CategoryIntentDetector`（扩展）

新增方法：

- `isSameCategoryAsSession()`：供 Guard 判断是否为 Replace。
- `isCategorySupportedByUserMessage()`：供 Guard 做证据校验。

`reconcileCategoryPatch()` 写入规则结果时使用常量 `SessionContextKeys.CATEGORY_SOURCE_RULE`。

### 5.3 `ContextMergeService`（修正）

`preserveFieldsOnCategoryReplace()` **不再复制** `budget`。

换品类时保留：`brandPreferences`、`dislikes`、`notes`、`intentType`。  
换品类时清空（既有逻辑）：`categoryId` 等派生字段、`scene`、`mustHave`、`pendingField`。

用户本轮 patch 若带有效 `budget`，在 merge 后续步骤仍会 `copyIfPresent` 写入。

### 5.4 `SessionContextKeys`（常量）

```java
CATEGORY_SOURCE_LLM  = "llm"
CATEGORY_SOURCE_RULE = "rule"
```

统一来源标记，避免魔法字符串分散。

### 5.5 `SessionStateMachine`（接入）

构造函数注入 `CategoryPatchGuard`，在 `reconcile` 与 `merge` 之间调用 guard。

---

## 6. 改造后单轮示例

用户：**「学习」**（session：`手机` + `budget=3000`，`pendingField=scene`）

```
1. extractPendingFieldPatch
   → 可能得到 { categoryRaw:"电脑", scene:"学习", categorySource:"llm" }  // LLM 幻觉

2. reconcileCategoryPatch("学习")
   → 原话无新品类，不覆盖

3. CategoryPatchGuard
   → categorySource=llm，候选「电脑」≠ session「手机」
   → isCategorySupportedByUserMessage("学习","电脑") = false
   → 删除 categoryRaw / categorySource
   → patch 剩余 { scene:"学习" }

4. mergeSessionPatch
   → categoryReplaced=false，保留 session.categoryId=cat_phone，写入 scene=学习

5. resolve → resolvedConstraints.categoryId=cat_phone

6. Agent → searchProduct(cat_phone, budget 3000, scene 学习)
```

用户：**「我想看看手机」**（session：`电脑`）

```
1. extractPatch → LLM 可能写 categoryRaw

2. reconcileCategoryPatch
   → 子串「手机」→ patch.categoryRaw=手机, categorySource=rule

3. Guard → rule 来源，直接放行

4. merge → categoryReplaced=true，清旧 budget/scene，写手机

5. resolve → cat_phone
```

---

## 7. 测试

| 测试类 | 覆盖点 |
|--------|--------|
| `CategoryPatchGuardTest` | 场景回答「学习」时剔除无证据 LLM 换品类；rule 放行；原话支持时 LLM 放行；同品类重复不删 |
| `ContextMergeServiceTest` | 换品类清旧 budget；换品类且 patch 带新 budget 时采用新预算 |

运行：

```bash
mvn -pl shopping-orchestrator -Dtest=CategoryPatchGuardTest,ContextMergeServiceTest test
```

---

## 8. 验收场景（本轮）

| # | 场景 | 期望 |
|---|------|------|
| 1 | 电脑 → 先看看 → 手机 → 预算3000 → 学习 | 始终 `cat_phone`，推手机，不出现电脑推荐 |
| 2 | 手机 session 下只答「学习」 | `categoryReplaced=false`，`scene=学习` |
| 3 | 电脑 → 我想看看手机 | `categoryReplaced=true`，旧 budget 清空 |
| 4 | 电脑 → 我想看看手机，预算5000 | 切手机且 budget=5000 |
| 5 | LLM 幻觉写旧品类 + 用户原话无类目 | Guard 删除 categoryRaw，不改品类 |

**注意**：若 Redis 中已有被污染的旧 session（品类已错写成电脑），需**新开对话**或清 session 后再测完整路径。

---

## 9. 调试

SSE `done.debug.stateDebug` 关注：

- `extractedPatch`：Guard 之后是否仍含错误 `categoryRaw`
- `categoryReplaced` / `categoryReplaceReason`
- `sessionContextBefore` / `sessionContextAfter` 的 `categoryId`
- `resolvedConstraints.categoryId`

第 5 轮「学习」期望：`categoryReplaced=false`，`categoryId` 保持 `cat_phone`。

---

## 10. 相关文件

```
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/
├── service/
│   ├── SessionStateMachine.java      # 接入 Guard
│   ├── CategoryPatchGuard.java         # 新增：证据门控
│   ├── CategoryIntentDetector.java   # 扩展：证据判定
│   ├── CategoryPatchNormalizer.java  # CATEGORY_SOURCE_LLM
│   └── ContextMergeService.java      # 换品类不继承 budget
├── support/
│   └── SessionContextKeys.java       # CATEGORY_SOURCE_* 常量
└── test/java/.../service/
    ├── CategoryPatchGuardTest.java
    └── ContextMergeServiceTest.java
```

---

## 11. 总结

| 维度 | 第二轮后 | 第三轮后 |
|------|----------|----------|
| 用户明说换品类 | reconcile 覆盖 LLM | 不变 |
| LLM 静默写错品类 | **仍会 merge** | **Guard 拦截** |
| 换品类 budget | 文档写清空，代码仍继承 | **代码与文档一致** |
| 追问只答 scene | 可能翻回旧品类 | **保持当前品类** |

一句话：

> **品类替换不仅需要「有新值」，还需要「用户本轮原话有证据」；规则检测结果不受此限。**
