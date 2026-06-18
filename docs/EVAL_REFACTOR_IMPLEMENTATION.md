# 评测问题改造说明

本文记录本次针对评测结果暴露问题的代码改造范围、原因和预期影响。

## 改动目标

1. 减少“推荐手机/耳机/电脑但未给预算”这类泛探索请求被过度澄清。
2. 保留长期画像偏好参与检索，但不要在回复里暴露“按长期画像品牌搜了但没搜到”的内部过程。
3. 修正对比澄清话术中出现具体示例商品名导致的 hallucination 误判。
4. 让评测脚本只对真实推荐回复做商品 grounding 检查，不把澄清问题当推荐结果评估。

## 具体改动

### 1. 区分“画像预算”和“用户本轮预算”

涉及文件：

- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ConstraintResolver.java`
- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ContextMergeService.java`
- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java`
- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/support/SessionContextKeys.java`

改动内容：

- `ConstraintResolver` 增加 `allowBudgetFallback` 参数，允许调用方在换品类后禁止复用旧预算或画像预算。
- `ContextMergeService` 在计算 `missingFields` 时，不再把 `budgetSource=long_term_profile` 当作“用户已经给了预算”。
- `SessionStateMachine` 在品类替换后调用 `buildEffectiveContext(..., allowBudgetFallback=false)`，避免旧品类预算污染新请求。
- 新增 `budgetUncertain` session scratchpad 字段，用于标记用户明确表达“预算还没想好/没定/价格不确定”。

解决的问题：

- 之前长期画像预算会让 `missingFields` 认为预算已满足，导致“预算还没想好”这类应澄清 case 直接进入推荐。
- 之前换品类后预算可能被沿用，导致新类目请求拿旧预算检索。

为什么这么改：

- 长期画像预算可以作为检索辅助，但它不是用户本轮明确约束。
- 预算是否缺失和推荐是否可执行是两个概念：泛探索可以直接推荐；明确说预算没定时再追问。

### 2. 预算澄清从“缺预算就问”改成“明确预算不确定才问”

涉及文件：

- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/ClarificationBuilder.java`
- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/SessionStateMachine.java`

改动内容：

- `ClarificationBuilder` 不再用 `missingFields contains budget && !userUncertain` 触发预算追问。
- 新逻辑是：只有 `missingFields` 包含 `budget` 且 `budgetUncertain=true` 且未追问过 budget 时，才返回预算澄清。
- `SessionStateMachine` 用规则识别明确预算不确定表达，例如“预算没想好”“预算没定”“价格不确定”“价位先不定”。
- “先看看/随便看看/预算无所谓”不会被这条规则强制转成预算澄清。

解决的问题：

- 泛探索请求例如“推荐一台适合打游戏的手机”不应该因为没预算就先问预算，而应该进入推荐。
- 明确说“预算还没想好”的请求仍然可以按评测预期进入 `NEED_CLARIFICATION`。

为什么这么改：

- `userUncertain=true` 太宽，可能包含“先看看”“随便看看”，这些不是必须追问预算的表达。
- `budgetUncertain` 更窄，只表达“用户主动说预算字段不确定”。

### 3. 兜底推荐话术不暴露内部检索过程

涉及文件：

- `shopping-orchestrator/src/main/resources/prompts/fragments/product-narration-rules.md`
- `shopping-consult-agent/src/main/resources/prompts/fragments/product-narration-rules.md`

改动内容：

- 保留 matchType 解释，但将“目录没有该品牌/预算内没有该品牌”的用户可见表达改为自然过渡。
- 新增规则：品牌、关键词或长期画像偏好触发兜底时，不要说“没搜到该品牌”“目录没有该品牌”“抱歉没找到这个品牌”。
- 推荐使用：“我先按你的预算和偏好，给你看几款同品类里更合适的商品”。

解决的问题：

- 用户没有明确说品牌，但系统按长期画像品牌做检索是正确的；如果回复说“没搜到这个品牌”，用户会觉得突兀。

为什么这么改：

- 长期画像是内部辅助信息，不需要向用户暴露完整推理链。
- 回复应围绕用户显式请求表达，而不是解释内部 fallback 路径。

### 4. 对比澄清不再包含具体商品示例

涉及文件：

- `shopping-orchestrator/src/main/java/com/onlineshopping/orchestrator/service/TurnOutcomeResolver.java`

改动内容：

- 将原澄清话术中的“小米 14 / iPhone 15”示例删除。
- 新话术：“请告诉我你想对比的两个具体商品名称，或者直接说‘第一款和第二款’。”

解决的问题：

- 评测脚本会扫描回复里的商品名。澄清模板里出现具体商品名，可能被误判为未授权商品 hallucination。

为什么这么改：

- 澄清话术只需要告诉用户如何补充信息，不应该引入任何用户没提过、工具没授权的具体商品。

### 5. 评测脚本跳过澄清回复的 grounding 检查

涉及文件：

- `scripts/eval_chat_batch.py`

改动内容：

- 新增 `empty_grounding()`。
- 当实际 `turnOutcome=NEED_CLARIFICATION` 时，不调用 `evaluate_grounding()`。

解决的问题：

- 澄清回复不是推荐结果，不应该要求 tool hit，也不应该按商品清单做 hallucination 判断。

为什么这么改：

- grounding/hallucination 指标衡量的是“推荐内容是否来自授权商品列表”。
- `NEED_CLARIFICATION` 没有推荐商品，继续扫描会把模板话术、例子或用户原话错误地计入商品提及。

## 测试补充

新增/更新测试：

- `shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/service/ClarificationBuilderTest.java`
- `shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/service/ContextMergeServiceTest.java`
- `shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/service/ConstraintResolverTest.java`

覆盖点：

- 泛探索缺预算不触发预算澄清。
- 明确预算不确定触发预算澄清。
- `userUncertain=true` 但不是预算不确定时，不触发预算澄清。
- 长期画像预算仍然会在 `missingFields` 中标记 budget 缺失。
- 用户本轮/session 预算不会被标记缺失。
- 禁止预算 fallback 时，仍允许 scene 等非预算长期画像字段 fallback。

## 预期评测影响

- `turnOutcomeAccuracy`：泛探索请求减少误判为 `NEED_CLARIFICATION`；明确预算不确定请求减少误判为 `READY_FOR_AGENT`。
- `toolHitRate`：泛探索请求更容易进入推荐链路，工具命中率应提升。
- `hallucinationRate`：对比澄清不再出现固定商品示例；澄清回复不再参与 grounding 扫描，误报应下降。
- 用户体验：长期画像偏好仍可参与推荐，但兜底时回复更自然，不会暴露“系统暗中用了某品牌偏好但没搜到”的内部逻辑。

## 静态检查情况

- 已执行 `git diff --check`，未发现 whitespace error，仅有 Git 的 CRLF/LF 提示。
- 已用 Codex 自带 Python 对 `scripts/eval_chat_batch.py` 执行 `py_compile`，语法通过。
- 未执行 Maven 单元测试：按要求停止跑测试；此前尝试因全局 Maven 仓库权限受限中止。
