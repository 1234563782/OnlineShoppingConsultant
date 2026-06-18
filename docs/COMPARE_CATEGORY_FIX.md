# 对比场景类目误判修复说明

## 背景

评测中 `case_09_compare_computer` 的用户输入是：

```text
ThinkPad X1 Carbon 和 MacBook Air M3 比一下。
```

对比目标本身识别正确：

```json
["ThinkPad X1 Carbon", "MacBook Air M3"]
```

但类目被规则识别成了 `cat_tablet`。根因不是商品名抽取错误，而是类目规则扫描把 `ThinkPad` 里的 `Pad` 当成了平板线索，导致：

```json
{
  "categoryRaw": "平板",
  "categorySource": "rule",
  "categoryId": "cat_tablet"
}
```

## 修复目标

1. 显式商品名对比时，不再从商品名子串里硬扫类目，而是先屏蔽动态抽取出的商品名，再对剩余文本做类目识别。
2. 如果商品名能从商品目录解析到 SKU，则优先用商品本身所属类目校正会话类目。
3. 类目校正要发生在状态机的类目解析前，避免 `sessionContext`、`effectiveContext`、`stateDebug` 出现不一致。

## 具体改动

### 1. 先识别 compare，再做类目规则补偿

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java
```

调整处理顺序：

```text
categoryPatchNormalizer
compareIntentDetector
categoryIntentDetector
categoryPatchGuard
brandIntentDetector
```

原因：`CategoryIntentDetector` 需要先看到 `shoppingSubIntent=compare` 和 `compareTargets.productNames`，才能判断当前是不是显式商品名对比场景。

### 2. 显式商品名对比时屏蔽商品名再识别类目

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CategoryIntentDetector.java
```

新增通用预处理：

```text
shoppingSubIntent=compare 且 compareTargets.productNames 非空时，
先从 userMessage 中移除 compareTargets.productNames 里的商品名片段，
再对剩余文本执行原有类目检测逻辑。
```

原因：`ThinkPad`、`iPad Pro`、`MatePad` 这类商品名里可能包含 `Pad`，子串规则容易误伤；但如果用户在商品名之外明确说了“电脑”“平板”等类目，系统仍应该识别。因此这里不是写死某个品牌或直接跳过规则，而是对任意 `compareTargets.productNames` 做动态屏蔽。

### 3. 商品名解析先全局搜，再按会话类目兜底

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CompareTargetResolver.java
```

原逻辑主要依赖当前会话类目约束搜索商品名。如果会话类目已经错成 `cat_tablet`，就会拿错误类目去搜 `ThinkPad`。

新逻辑：

```text
先匹配上一轮推荐
再全局按商品名搜索
最后才按当前会话 categoryId 搜索
```

原因：显式商品名本身比当前会话类目更可信，尤其是在当前类目正是由误识别造成的时候。

### 4. 用已解析商品反向校正类目

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CompareTargetResolver.java
```

如果至少两个商品名都解析成功，并且这些商品属于同一个 `categoryId`，就写回：

```json
{
  "categoryId": "...",
  "categoryName": "...",
  "categoryRaw": "...",
  "resolvedCategoryRaw": "...",
  "categoryResolution": "RESOLVED",
  "categorySource": "compare_product"
}
```

原因：对比场景里，两个明确商品同属一个类目时，商品目录结果比类目子串规则更可靠。

### 5. 类目校正提前到状态机类目解析前

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java
```

新增 `applyCompareProductCategory(...)`，在 `CategoryResolutionService.resolve(...)` 前调用。

原因：如果只在 `TurnOutcomeResolver` 或预取阶段校正，虽然可能能拿到正确 SKU，但 `effectiveContext`、`categoryResolution`、`stateDebug` 可能已经带着旧类目生成。提前校正后，本轮状态会更一致。

### 6. 新增类目来源常量

文件：

```text
shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/support/SessionContextKeys.java
```

新增：

```java
public static final String CATEGORY_SOURCE_COMPARE_PRODUCT = "compare_product";
```

避免在业务代码和测试里散落裸字符串。

### 7. 补充测试

文件：

```text
shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/service/CategoryIntentDetectorTest.java
shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/service/CompareTargetResolverTest.java
```

覆盖两类行为：

1. 任意显式商品名对比时，类目规则检测不应从 `compareTargets.productNames` 的子串推断类目。
2. 如果商品名之外仍出现明确类目词，屏蔽商品名后仍应正常识别该类目。
3. 即使当前会话类目错误为 `cat_tablet`，商品名全局搜索解析到两个电脑商品后，也应把会话类目校正为 `cat_computer`。

## 影响范围

正向影响：

1. 修复 `ThinkPad -> Pad -> 平板` 这类商品名子串误判。
2. 对比场景更依赖商品目录事实，而不是文本规则猜测。
3. 减少 compare case 因错误类目导致的 `NEED_CLARIFICATION`。

需要注意：

1. 显式商品名对比会更早调用一次商品搜索，后续 outcome/prefetch 仍可能再次解析，存在少量重复搜索成本。
2. 只有当至少两个商品解析成功且同属一个类目时，才会反向校正类目；跨品类对比不会强行改类目。
3. 如果 catalog 服务不可用或商品名无法解析，则不会强行校正，系统会保持原有澄清/兜底逻辑。

## 验证建议

建议重点回归以下用例：

```text
case_09_compare_computer
case_130
case_134
case_136
case_138
case_140
case_142
case_146
case_150
case_152
```

关注字段：

```text
shoppingSubIntent
compareTargets.productNames
categoryId
categorySource
turnOutcome
prefetchedCompare.skuIds
```

预期 `case_09_compare_computer` 的类目应从错误的 `cat_tablet` 回到 `cat_computer`，并且 `turnOutcome` 应更倾向于 `READY_FOR_AGENT`。
