# Stage 12 Harness² Algorithm Closure (HAC-1)

## 结论与边界

- `11B-2A = DEFERRED_BY_DATA`：68/69 及受控 SQL 资产保留；未验证真实凭据、未执行 live、未向真实 Domain 写入经验。该阶段 `ADOPTED=0` 是未开始实验，不是候选评估失败。
- HAC-1 的**离线算法闭环通过**：六题确定性 benchmark 中，三道 bootstrap 题从基线失败改善，产生四条合成域 `ADOPTED`；两道 transfer 题复用了同域已采纳算子，一道无改善题如实返回 `NO_TRANSFER`。
- 这些分数只验证 PPC 状态转移、选择、经验回读与隔离。题型标签覆盖 RAG/TOOL/HISTORY/COMPOSITE，但 oracle 不执行真实检索、工具或模型；不得解释为这些能力的效果指标，更不是论文级泛化证据。
- 未修改 `AgentOrchestrator`、生产配置、68/69 文档或 `main`；没有调用 DeepSeek、生产 PostgreSQL/Redis 或付费 API。

## 固定基准与机制

夹具：`backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness12/benchmark.json`，标记 `SYNTHETIC_ALGORITHM_BENCHMARK`，SHA-256 为 `1b0f3cdf584f0762ebfe55f571ffd6a60c9678e70a605a4ff60cd784773fd4c1`。3 道 bootstrap、3 道 transfer，涵盖五种能力标签。oracle 仅计算 voucher 中两项可观察算子状态的满足比例，分数为 `[0,1]`；没有人工或模型评分。

`TaskLevelRecursivePpc` 复用 `AlgebraicHarnessMutator`、`ExperienceGuidedProposer` 与现有经验库。每题先测 baseline；第一轮提出两个算子并探测，按安全且严格高于当前分数选择；第二轮在已选 voucher 上应用剩余算子。每题最多两轮、四次 probe（含 baseline）。只有 bootstrap 的严格改善写入受控合成 Domain；transfer 只读，不回写经验。候选不安全、未改善或探针失败均不替换上一已验证 voucher。

| 题目 | 划分 | 标签 | 基线 → 最终 | 结果 |
|---|---|---|---|---|
| B1 | bootstrap | PROMPT_ONLY | 0 → 1 | 采纳 concise prompt |
| B2 | bootstrap | HISTORY | 0 → 1 | 采纳 memory window |
| B3 | bootstrap | RAG | 0 → 0.5 → 1 | 两轮组合并各写一条 ADOPTED |
| T1 | transfer | TOOL | 0 → 1 | 同域经验复用 |
| T2 | transfer | COMPOSITE | 0 → 1 | 同域经验复用 |
| T3 | transfer | RAG | 1 → 1 | `NO_TRANSFER`，不虚构改善 |

经验召回断言：bootstrap 后正向召回恰为四条，均为 `ADOPTED`、`failureObserved=false`、执行模式为合成 benchmark；transfer 后仍为四条。`REJECTED`、未决失败、`UNSCOPED_REPLAY` 和另一 Domain 均不能形成正向 proposal。第二轮失败测试确认第一轮 0.5 的结果与已采纳经验保留；预算不足时不启动不完整候选批次。

## 验证

局部使用 SDKMAN Temurin Java 21.0.5，单一 Maven 进程，无手工 proto 预构建：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

结果：43/43 Reactor 模块 `BUILD SUCCESS`；从本次 Surefire XML 汇总 **2183 tests、0 failures、0 errors、8 skipped**。`TaskLevelRecursivePpcTest` 为 4 tests、0 failures、0 errors、0 skipped。默认 `live` 测试组由父 POM 排除。

交付门禁：Stage 12 的代码、benchmark、测试和本报告已由 `dc566b6c` 提交；同一 HEAD 的全新本地 APFS 检出 `/Users/achilles/.codex/worktrees/hermes12-clean-gate/Agent` 再次单进程、无手工 proto 预构建执行上述单步 `clean test`，`BUILD SUCCESS`，Surefire XML 复算 **2183 tests、0 failures、0 errors、8 skipped**，检出工作区 clean。Stage 12 标记为 `CLOSED`。历史构建间歇故障根因仍为 `UNPROVEN`；此次通过是环境范围内的验证，不宣称根因已修复。

## 后续边界

HAC-1 只证明离线机制可以有界地改善并传递合成经验。Stage 13 的真实受控实验及 Stage 14 的论文级实验须另立数据、来源和评测契约；本报告不为其效果或生产接入背书。
