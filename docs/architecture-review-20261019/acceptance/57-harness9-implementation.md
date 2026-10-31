# Hermes Harness² Stage 9 实施与真实 PPC 结果

## 结论

- 实施分支：`feat/hermes-harness-stage9`；预研与冻结任务书已在 Stage 8 分支归档提交 `887ed4c1`。`main` 未修改。
- 真实运行：`REAL_PPC`，15 个 probe、26 次 DeepSeek `deepseek-flash` 调用、35,500 tokens，按本阶段官方分时段价格计算 ¥0.06718974，均低于 20/60/200,000/¥10.00 硬上限。单次输出请求硬限 1,024 tokens（低于 2,000 上限）。
- 最终选择：保留 `baseline`，`REJECTED_AFTER_REAL_PPC`。这表示真实实验完成、候选未通过采纳门槛，**不表示 5 类任务质量全部通过，也不授权生产推广**。
- 证据：`experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/`（Git 忽略目录）。`checksums.sha256` 中 17 个 JSON 文件均通过 `shasum -a 256 -c`；目录内未发现本次 API Key 明文。

## 实施范围

`PpcOrchestrator` 使用真实 `AgentOrchestrator.chat`、`HarnessKernelBridge.ENFORCE`、请求级不可变凭单与五类能力夹具。`PpcComposer` 生成 baseline、仅改静态提示词的 A、仅将最大循环步数从 3 收紧至 2 的 B；C 仅在完整前 15 个 probe 且预计追加成本和质量门禁通过时解锁。选择时剔除未知用量/成本，检查 qrel 标记，进行匿名双向位置对调；`ragScore` 始终禁用。

`UsageCapturingChatModel` 按每次 provider 报告的 usage 分别计入缓存命中/未命中、输出 token 与北京时间峰谷费用；未知 usage 不作为零成本参与比较。请求级看门狗在每次模型调用前预留预算，在调用后累计真实用量。DeepSeek 客户端接受通用 `ChatOptions.getMaxTokens()`，Stage 9 Live 工厂另外锁定 1,024-token 输出上限。证据使用独立目录、同目录临时文件和 `ATOMIC_MOVE`；默认 Maven 测试排除 `@Tag("live")`。

## 逐题结果

| 配置 | 质量通过 | Tokens | 费用（元） | 主要事实 |
| --- | ---: | ---: | ---: | --- |
| baseline | 3/5 | 11,864 | 0.02175624 | RAG、TOOL、PROMPT_ONLY 通过；HISTORY、COMPOSITE 未通过 |
| candidate-a | 3/5 | 12,565 | 0.02462980 | 同样两题未通过，且总成本、tokens 均高于 baseline |
| candidate-b | 3/5 | 11,071 | 0.02080370 | 成本与 tokens 略低，但同样两题未通过，不能采纳 |

- `kb-qa-01`：三组均有 `RAG` 标签、真实知识库工具调用、`ragUsed=1/1`，回答包含 3 次退避与 SHA-256 幂等键。`chunk-pay-idem-01` 是**注入夹具 ID**；当前 proto/证据没有检索器返回的 chunk ID 字段，不冒称为真实检索 provenance。
- `tool-route-02`：三组均实际调用 `banking_transaction_query`，返回 `SUCCESS`、`ACC-9901` 与金额；质量通过。
- `param-guard-03`：三组均为 `PROMPT_ONLY`，指出 `25:99:99` 不合法；无外部工具或 RAG。
- `ctx-history-04`：三组均为 `HISTORY`，但各次 `completionTokens=reasoningTokens=1,024`，最终回答为空，质量失败。这是输出预算内可观察的失败，不能按已覆盖历史问答能力宣称通过。
- `comp-rag-tool-05`：三组均注入 RAG 且 `ragUsed=1/1`，但均未执行 `arch_metrics_calculator`；模型指出缺少 `clusterId`，质量失败。`chunk-arch-ebpf-01` 同样仅是注入夹具 ID。
- 两个质量失败阻止了候选 C 的追加门禁。Position-Swap 记录保留在 `ppc_summary.json`；HISTORY 相同空输出对应 `INCONSISTENT_TIE`，未作有利于候选的裁决。

## 验证

- Java 21 隔离环境：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，没有改变宿主默认 JDK。
- 单独 `qknow-hermes/qknow-hermes-proto test-compile`：`BUILD SUCCESS`。
- DeepSeek 请求体契约测试：10 项通过，0 failure/error。
- Stage 8 + Stage 9 联合回归：30 项通过，0 failure/error。
- 全仓默认 `mvn -B -ntp -f backend/pom.xml test`：`BUILD SUCCESS`；本次 Surefire 报告汇总 2,163 项、0 failure、0 error、10 skipped；未运行 Live 标签。
- 显式 `-Plive -Dtest=HermesHarnessStage9LivePpcTest`：1 项通过，0 failure/error。证据以 provider 真实 usage、`REAL_PRODUCTION` 模式和完整校验清单为准，不以单个测试成功替代质量判定。

## 残余边界

1. `ctx-history-04` 的 1,024-token 输出预算被思维链耗尽，以及 `comp-rag-tool-05` 未调用指定工具，属于本次实证发现。若要修改输出预算、问题夹具或工具参数契约并重跑 PPC，应另行冻结新实验，不得事后修改本轮结果。
2. 当前计费器按任务书给出的工作日/时钟窗口判定峰谷，未接入中国法定节假日日历。节假日工作日的峰时可能高估费用；本次运行在周六，不受此缺口影响。
3. `ragScore` 未计算；注入夹具 ID 不等于检索器返回的 chunk provenance。Stage 9 结果不能外推为真实向量检索质量结论。
4. 未触及 MCP stdio、HITL、生产启用或多轮自修改；本阶段也未把候选采纳到默认运行配置。
