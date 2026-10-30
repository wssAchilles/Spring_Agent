# Hermes Harness² Stage 8-lite 实施与验证记录

- 日期：2026-10-31
- 分支：`feat/hermes-harness-stage8`（基线 `3038781a`）；未修改或合入 `main`
- 冻结契约：`53-harness8-taskbook.md`，正文 SHA-256 `6947491515fe12dfd8e969050cab4f580155e946c3cb36c5826faa8cb042dcf8`；全文件校验 `OK`
- 范围：Stage 9 离线 PPC 的最小使能底座；未做付费 Live E2E、MCP stdio、HITL 或在线自修改

## 实施结果

1. `UsageCapturingChatModel` 在请求级拦截 `ChatModel.call/stream` 的 `ChatResponse.metadata.usage`，主模型和工具路由模型共享计量。缺失 usage、异常或取消均使整次请求成为 `UNKNOWN`；证据中的 Token 不再用字符估算冒充 `KNOWN`。标准 usage 不提供人民币价格，因此 `actualCostCny=0` 只是未获价格信息的占位，不是零成本实测。
2. Bridge 对清洗后的 RAG、工具和历史执行 `PreparedExecution.applyTo`，返回有效请求与能力标签；Orchestrator 将其用于实际 Prompt、工具名和消息历史。空扩展载荷降级为 `PROMPT_ONLY`，显式声明却缺载荷在 ENFORCE 下 fail-close。
3. ENFORCE 工具只经 Bridge 一层 Guard；OFF/SHADOW 由原 ReAct 守卫处理。全模式 resolver 限制为当前有效工具名；拒绝调用不触达底层工具，证据记 `success=false, guardBlocked=true`。Kernel 的循环窗口和冷备缓冲改为请求级隔离。
4. `ragProvided` 取实际有效注入文档；`ragUsed` 依据最终答案的知识库名称/ID、成功的对应知识库工具调用或至少两个内容核心词元命中计算，并贯通 Orchestrator、Bridge、Kernel 与归因向量。它是可复现的文本/工具使用代理指标，不是因果使用证明。`ragScore` 仅在可用的检索评估置信度存在时传递；当前 `RAGContext` proto 没有相似度字段，未启用评估时为 `0`，不得解释为实测零相似度。
5. `HarnessEvidenceRecord` 向后兼容增加 usage、能力标签、Guard 阻断及 RAG 归因字段，并纳入 SHA-256 摘要与 `MessageDigest.isEqual` 验签。离线 PPC 在任一侧 usage 为 `UNKNOWN` 时拒绝预算比较与候选采纳；历史合成正向样本显式标为 `KNOWN`。
6. `GraphRagExecutionTest` 涉及真实数据库删除/写入、Neo4j 同步和可能的模型调用，补充 `@Tag("live")`；父 POM 原有默认排除 `live` 配置生效，测试逻辑未改。

## 验证

使用局部 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，未改宿主 JDK。

| 门禁 | 结果 |
|---|---|
| Harness 定向套件（`-pl qknow-hermes/qknow-hermes-core -am`，含 Stage 8 专属类） | 63 项，0 failure / 0 error / 0 skip，`BUILD SUCCESS` |
| 新增 Orchestrator 归因测试 + Stage 8 六项就绪测试 | 7 项，0 failure / 0 error / 0 skip，`BUILD SUCCESS` |
| `HermesHarnessStage8PpcReadinessTest` | 6 项，全部通过 |
| `qknow-hermes-core` 默认测试 | 165 项，0 failure / 0 error / 0 skip |
| 全仓 `mvn -B -ntp -f backend/pom.xml clean test` | 48 个 reactor 模块 `BUILD SUCCESS`；proto 生成 47 个 Java 文件（46 个消息/辅助类 + 1 个 gRPC 类） |
| 全仓默认 `mvn -B -ntp -f backend/pom.xml test` | Surefire XML 合计 2152 项，0 failure / 0 error / 10 skip（实际执行 2142 项）；`BUILD SUCCESS` |
| 静态检查 | `git diff --check` 通过；`experiment/outputs/harness4` 与 `harness5` 无工作区改动 |

首次默认全量测试被未标记的 `GraphRagExecutionTest` 阻断，原因是缺少 `NEO4J_PASSWORD`；该测试被标记 live 后不再进入默认门禁。首次全仓 `clean test` 在 proto 生成源码读取处发生一次间歇性失败；随后 proto 单模块 `clean compile` 与全仓 `clean test` 均成功。该构建波动仍需在后续 CI 重复运行观察，不能被一次通过抹除。

## 结论与边界

Stage 8-lite 的确定性实现和默认全量测试门禁已通过，可在当前实验分支归档；这不是 Stage 9 真实 PPC、付费 DeepSeek Probe 或生产认证。Stage 9 在把成本或 RAG 分数纳入比较前，必须先取得可信价格口径和检索分数来源；SHADOW 模式当前只执行白名单与既有单层熔断，没有新增逐次影子计数接口。`main` 保持不变。
