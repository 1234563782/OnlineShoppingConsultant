# 评测失败原因说明

本文档解释的是一个很具体的问题：**为什么有些样本从直觉上看应该是对的，但最终评测结果却没有命中预期**。

这份说明不讨论“要不要改代码”，只解释“为什么会这样”。

## 先说结论

这次 200 条批量评测里，并不是系统整体失效，而是失败集中在少数几类固定场景：

1. 一部分是 **样本预期和当前代码策略不一致**。
2. 一部分是 **代码本身会更保守地追问，而样本预期偏乐观**。
3. 一部分是 **compare 链路里类目被误识别，导致后续目标解析失真**。

所以，很多“本来应该对”的结果之所以没对，不是单点随机错误，而是 **评测口径、样本设计、代码策略** 三者里至少有一项没有对齐。

---

## 本次评测概况

本次运行结果如下：

- `intentAccuracy`: `100%`
- `turnOutcomeAccuracy`: `82.5%`
- `categoryAccuracy`: `93.71%`
- `toolHitRate`: `84.25%`
- `hallucinationRate`: `6.85%`

这说明：

- **意图识别本身是稳定的**，不是“看不懂用户在干什么”。
- 真正掉分主要集中在：
  - 是否该追问
  - compare 是否能顺利进入
  - 类目是否被错误归一化

---

## 失败的三类根因

### 1. 样本预期偏乐观，但代码会先追问预算

这一类失败最多。

典型样本是 discovery 类问题，只给了“买什么 + 场景”，但没有明确预算，例如：

- `case_033_phone_discover_03`
- `case_041_phone_discover_11`
- `case_049_headphone_discover_03`
- `case_065_computer_discover_03`
- `case_081_tablet_discover_03`
- `case_113_tv_discover_03`

这些样本在评测文件里被写成了：

- 预期：`READY_FOR_AGENT`
- 实际：`NEED_CLARIFICATION`

为什么会这样？

因为当前代码在预算缺失时，会优先进入澄清分支。

对应逻辑在：

- [`ClarificationBuilder.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ClarificationBuilder.java#L61-L66)

这里的规则很直接：

- 如果 `missingFields` 里有 `budget`
- 且当前不是“用户自己已经表达不确定”
- 且还没问过预算
- 就会先追问预算

所以这类 case 并不是代码“答错了”，而是：

- **样本认为“可以直接推荐”**
- **代码认为“预算不够，先问清楚再推”**

这属于**评测样本和产品策略没有对齐**。

---

### 2. 样本预期需要澄清，但代码会从历史信息或 profile 里补齐预算后直接推荐

这一类也不少，典型是明确说“预算还没定”“价格先不定”的澄清样本，例如：

- `case_153_phone_clarify_01`
- `case_154_phone_clarify_02`
- `case_155_headphone_clarify_01`
- `case_156_headphone_clarify_02`
- `case_157_computer_clarify_01`
- `case_158_computer_clarify_02`
- `case_159_tablet_clarify_01`
- `case_160_tablet_clarify_02`
- `case_161_watch_clarify_01`
- `case_162_watch_clarify_02`
- `case_163_tv_clarify_01`
- `case_164_tv_clarify_02`

这些样本的预期是：

- `NEED_CLARIFICATION`

但实际经常变成：

- `READY_FOR_AGENT`

原因不是评测脚本错了，而是当前实现会尝试 **profile fallback**：

- 如果会话里预算不完整
- 但系统认为可以从长期偏好或上下文补回来
- 就会把预算补齐，然后继续走推荐

对应逻辑在：

- [`ConstraintResolver.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ConstraintResolver.java#L39-L48)
- [`ConstraintResolver.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ConstraintResolver.java#L56-L60)
- [`ConstraintResolver.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ConstraintResolver.java#L115-L133)

这里的核心意思是：

- **预算缺失不一定意味着必须追问**
- 系统允许“先用长期信息补齐，再继续推荐”

所以这类失败的本质是：

- **样本期望的是“严格澄清”**
- **代码策略是“能补就补，补到就直接推荐”**

这不是随机 bug，而是 **策略口径不一致**。

---

### 3. compare 样本里出现了真实代码问题：类目被误判，导致后续解析偏掉

这一类是最值得注意的，因为它不是“样本太严格”，而是链路里真的有误判。

典型 case：

- `case_130_phone_compare_02`
- `case_134_headphone_compare_02`
- `case_138_computer_compare_02`
- `case_142_tablet_compare_02`
- `case_146_watch_compare_02`
- `case_150_tv_compare_02`

这些 case 的现象很一致：

- 输入明明是手机 / 耳机 / 电脑 / 平板 / 手表 / 电视对比
- 但 debug 里类目却被解析成了别的品类，典型是 `cat_watch`
- 最终 compare 目标不够 2 个，系统就退回到澄清

这一段不是纯评测问题，而是代码链路本身有明显风险：

1. 用户消息先经过类目识别
2. 类目识别如果偏了，后面 compare 目标解析就会跟着偏
3. compare 目标不够时，`TurnOutcomeResolver` 会直接转澄清

对应代码在：

- [`CategoryIntentDetector.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CategoryIntentDetector.java#L80-L92)
- [`CategoryIntentDetector.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CategoryIntentDetector.java#L119-L160)
- [`CategoryResolutionService.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CategoryResolutionService.java#L68-L120)
- [`CompareTargetResolver.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/CompareTargetResolver.java#L33-L49)
- [`TurnOutcomeResolver.java`](C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/TurnOutcomeResolver.java#L54-L68)

尤其是 `TurnOutcomeResolver` 这里：

- 如果 compare 目标解析不到 2 个 SKU
- 就会直接判成 `NEED_CLARIFICATION`

这就解释了为什么有些 compare case 看起来“应该直接比”，最后却变成“请告诉我具体商品”。

本质上不是 compare 本身不会做，而是前面的类目和目标解析已经歪了。

---

## 为什么会出现“明明看起来应该对，却不符合预期”

可以把原因分成 4 种理解：

### A. 预期写得比代码更激进

样本认为“只要有商品类型和场景，就应该直接推”。

但代码认为：

- 没预算，先澄清
- 类目不稳，先澄清

这时失败不是 bug，而是 **预期太乐观**。

---

### B. 代码比样本更保守

样本写的是“应该问预算”。

但代码允许：

- 从 profile 补预算
- 从历史上下文补预算
- 继续走推荐

这时失败不是模型不会，而是 **实现比样本更愿意向前推进**。

---

### C. compare 链路的输入被污染

一旦类目识别歪了，后面的 SKU 解析、compare 目标解析都会受影响。

这时你看到的结果会很像：

- 明明问的是手机对比
- 结果系统反而像在问别的品类

这种属于 **上游错误传导到下游**。

---

### D. 评测指标是“严格口径”，不是“人类直觉”

这次评测不是看“回复大概像不像对”，而是看：

- 意图是否命中
- outcome 是否完全一致
- 类目是否完全一致
- compare 是否真的拿到了 2 个 SKU
- 是否出现了未授权商品或价格幻觉

所以哪怕回复“看起来合理”，只要走错了 outcome，评测就会记失败。

---

## 这次最关键的判断

如果只回答你一个最核心的问题：

**不是所有失败都是代码坏了。**

更准确地说：

- `discover` 里的一批失败，是样本期望和当前澄清策略不一致
- `clarify` 里的一批失败，是系统允许 profile fallback，导致它不一定会停下来追问
- `compare` 里的失败，有真实代码问题，主要是类目误判和 compare 目标解析链路不稳

---

## 你后面怎么看这份结果

以后遇到“我觉得它应该对，但结果不对”，建议先分三步看：

1. 先看这个样本到底是 `discover`、`clarify` 还是 `compare`
2. 再看代码当前策略是“先问”还是“先补齐再推荐”
3. 最后看是不是类目已经被错分了

这样你就能快速判断：

- 是改样本
- 是改评测口径
- 还是改代码

---

## 结论

这批失败不是单一原因造成的。

最主要的问题是：

- **样本期望与当前产品策略没有完全对齐**
- **部分 compare 样本暴露了真实的类目解析问题**

所以，很多“本来应该对”的 case 之所以不对，不是因为系统整体不行，而是因为：

- 评测标准更严格
- 样本假设更理想
- 代码策略更保守或更依赖上下文

这三者没对齐。
