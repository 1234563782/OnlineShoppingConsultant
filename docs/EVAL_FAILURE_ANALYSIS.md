# 最新评测失败案例分析

这份文档覆盖旧版分析，基于当前最新一轮评测结果重新整理。

## 结论先说

本轮 200 条评测里，系统整体已经比较稳定，当前真正还在失败的只有 5 个 case：

- 4 个是“用户已经明确说了预算还没定，但系统没有进入澄清”的问题
- 1 个是“`non_shopping` 被判成 `small_talk`”的意图口径问题

另外，旧文档里提到的 `compare` 类问题已经不是当前失败集的一部分了，说明那一块已经修复，不能再按旧结论看。

## 本轮汇总

最新评测汇总：

- `intentAccuracy`: `199/200`，`99.5%`
- `turnOutcomeAccuracy`: `195/200`，`97.5%`
- `categoryAccuracy`: `159/159`，`100%`
- `toolHitRate`: `142/146`，`97.26%`
- `hallucinationRate`: `2/146`，`1.37%`

这说明：

- 类目识别已经全对，当前不再是“类目错分”主导问题
- 工具命中和幻觉也都在较低水平
- 目前主要失分点集中在 `turnOutcome`，也就是“该澄清还是该直接推荐”的判断

## 当前失败案例清单

| Case | 期望 | 实际 | 主要原因 |
| --- | --- | --- | --- |
| `case_157_computer_clarify_01` | `NEED_CLARIFICATION` | `READY_FOR_AGENT` | 预算不确定未被稳定识别 |
| `case_158_computer_clarify_02` | `NEED_CLARIFICATION` | `READY_FOR_AGENT` | 预算不确定未被稳定识别 |
| `case_159_tablet_clarify_01` | `NEED_CLARIFICATION` | `READY_FOR_AGENT` | 预算不确定未被稳定识别 |
| `case_162_watch_clarify_02` | `NEED_CLARIFICATION` | `READY_FOR_AGENT` | 预算不确定未被稳定识别 |
| `case_191_nonshopping_09` | `NON_SHOPPING` | `SMALL_TALK` | 意图分类口径不一致 |

## 逐个原因分析

### 1. 预算明确不确定，但系统没有触发澄清

对应 case：

- `case_157_computer_clarify_01`
- `case_158_computer_clarify_02`
- `case_159_tablet_clarify_01`
- `case_162_watch_clarify_02`

这些样本的共同特征是：

- 用户都明确表达了“预算还没确定”
- 评测期望是先澄清预算，不要直接推荐
- 但系统最后都走成了 `READY_FOR_AGENT`

从结果里看，问题不是“完全没看到预算信息”，而是“看到了预算缺失，但没有把它当成必须澄清的强信号”。

这类问题的核心位置在澄清判断链路，重点看：

- [`shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ClarificationBuilder.java`](/C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ClarificationBuilder.java)
- [`shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java`](/C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java)

从当前行为看，系统更像是在依赖“预算不确定”这个显式信号是否被识别到，而不是只要看到“预算还没定 / 再想想 / 先不说 / 再考虑一下”就强制澄清。

所以这里的本质是：

- **不是类目问题**
- **不是推荐结果问题**
- **是预算不确定识别和澄清策略的口径偏窄**

更直白一点说，就是：

- 评测希望“先问清楚预算”
- 代码实际是“只有在识别到足够明确的不确定信号时才问”

这就导致样本看起来“明明应该澄清”，但系统却直接进入推荐流程。

### 2. `讲一个笑话` 被判成了 `small_talk`

对应 case：

- `case_191_nonshopping_09`

这个 case 的期望是：

- `intentType = non_shopping`
- `turnOutcome = NON_SHOPPING`

但实际是：

- `intentType = small_talk`
- `turnOutcome = SMALL_TALK`

这个问题更像是**意图分类口径不一致**，不是推荐链路的问题，也不是类目解析的问题。

从代码侧看，`TurnOutcomeResolver` 会直接根据上游 `intentType` 分流：

- `small_talk` -> `SMALL_TALK`
- `non_shopping` -> `NON_SHOPPING`

见：

- [`shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/TurnOutcomeResolver.java`](/C:/Users/Administrator.DESKTOP-D2GSM8I/Desktop/OnlineShoppingConsultant/shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/TurnOutcomeResolver.java)

也就是说，当前问题出在更上游的意图抽取或评测标注口径上：

- 如果评测认为“讲笑话”必须算 `non_shopping`
- 那么抽取层就应该稳定输出 `non_shopping`
- 如果系统现在更倾向把这类输入归到 `small_talk`
- 那么就是**标注口径和模型输出口径没有统一**

这个 case 本质上不是“答错内容”，而是“该走哪条意图分支”的分类不一致。

## 目前不是主问题的部分

### compare 问题已经不在当前失败集里

旧版文档里比较重点提到的 compare 类误判，现在已经修掉了，不属于本轮 5 个失败 case 之一。

这点很重要，因为它说明：

- 之前的 `compare` 类路由和类目误判已经被修正
- 当前文档里的失分，不能再按旧版 compare 结论去理解

### 类目识别本轮是满分

`categoryAccuracy = 159/159`，说明当前没有新的类目识别系统性问题。

所以本轮的失败不是“品类识别坏了”，而是更细的：

- 澄清策略是否应该触发
- 非购物意图该归到哪一类

## 根因归类

可以把当前问题分成两类：

### A. 代码逻辑问题

主要是 4 个预算澄清 case。

原因不是 SQL、接口、或者商品召回，而是澄清条件判断偏窄，导致“用户说预算没定”时没有稳定走到 `NEED_CLARIFICATION`。

### B. 意图口径问题

主要是 `case_191_nonshopping_09`。

原因是 `non_shopping` 和 `small_talk` 的分界在当前链路里不够统一，导致评测期望和实际输出不一致。

## 建议的修正方向

1. 把“预算不确定”识别做宽一点，不要只依赖少量固定表达。
2. 澄清判断最好同时看“缺字段”与“用户显式犹豫表达”，而不是只看其中一个。
3. 明确 `small_talk` 和 `non_shopping` 的定义边界，保证评测标注、提示词、代码分流三者一致。
4. 旧的 compare 分析可以保留为历史记录，但不要再当成当前问题集。

## 结论

本轮评测的剩余问题已经不多，而且类型很集中：

- 4 个是预算澄清没触发
- 1 个是意图分类口径不一致

所以现在的重点不是“大改系统”，而是把这两个判断口径再收紧、再对齐一次。
