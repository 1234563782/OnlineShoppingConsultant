# 效果测评

本项目的测评建议分层做：先用稳定的离线 JUnit 评测守住核心指标，再逐步接入真实 `/api/v1/chat` 调用、LLM 评审和线上 trace。

当前第一版是确定性的 JUnit 基线，不依赖 Redis、Nacos、catalog 服务或真实 LLM Key，因此可以稳定放进 CI。

## 当前指标

基线测试文件：

`shopping-orchestrator/src/test/java/com/onlineshopping/orchestrator/eval/ShoppingAgentQualityEvalTest.java`

当前覆盖四个指标：

| 指标 | 测什么 | 当前门槛 |
| --- | --- | --- |
| 意图识别准确率 | 单轮输入是否正确识别为购物、闲聊或非购物。 | `>= 0.90` |
| 品类归一化准确率 | `categoryRaw` 是否能归一到预期 `categoryId`，也包含无法归一的情况。 | `>= 0.90` |
| 工具命中率 | 助手推荐的商品是否都来自本轮工具预取结果。 | `>= 0.90` |
| 幻觉率 | 助手是否提到未授权 SKU，或把工具返回的价格说错。越低越好。 | `<= 0.05` |

## 运行方式

只跑 orchestrator 的测评基线：

```bash
mvn -pl shopping-orchestrator -am -Dtest=ShoppingAgentQualityEvalTest "-Dsurefire.failIfNoSpecifiedTests=false" test
```

运行 orchestrator 全部测试：

```bash
mvn -pl shopping-orchestrator test
```

批量调用真实接口并导出结果：

```bash
python scripts/eval_chat_batch.py --register
```

默认会读取：

- `evals/chat_cases.jsonl`
- 输出 `evals/chat_results.jsonl`
- 汇总到 `evals/chat_summary.json`

如果你已经有登录用户，也可以不加 `--register`，直接用 `--username` 和 `--password` 指定账号。

## 指标口径

意图识别准确率：

```text
意图识别正确样本数 / 意图评测总样本数
```

品类归一化准确率：

```text
categoryId 命中预期的样本数 / 品类归一化评测总样本数
```

工具命中率：

```text
至少推荐一个商品，且没有推荐工具结果之外 SKU 的回复数 / 工具命中评测总样本数
```

幻觉率：

```text
提到未授权 SKU 或价格不一致的回复数 / 幻觉评测总样本数
```

第一版先使用 SKU 作为商品 grounding 标准：回复中出现的 `SKU\d+` 必须存在于本轮 `prefetchedSearch.products` 中。

价格幻觉的判断方式是：如果回复提到了某个 SKU，那么该 SKU 附近必须出现工具返回的真实价格。

## 后续接真实对话

后面可以把同一套指标接到真实 `/api/v1/chat` 流程：

1. 为每条评测样本保存用户输入、预期意图、预期品类和允许推荐的商品集合。
2. 调用 `/api/v1/chat`，收集最终 `done` 事件。
3. 从 `done.debug.turnOutcome`、`done.debug.sessionContext`、`done.debug.categoryResolution` 读取实际意图和品类结果。
4. 从 `done.debug.prefetchedSearch` 读取本轮允许推荐的商品集合。
5. 用当前测试里的 grounding 和幻觉规则给最终回复打分。

这样可以保持评分口径稳定，同时把实际结果来源从固定样例逐步切换到真实 Agent 运行结果。
## 评测口径补充

- 商品名命中现在按短语边界判断，不再把相邻词黏连后再匹配。
- 这可以避免 `iPad`、`AirPods` 这类泛化提法误撞成 `iPad Air`。
- `SKU1234` 仍然按精确 SKU 形式匹配，价格判定也只看该 SKU 附近的真实价格。
