# Hermes Harness Stage 20 实施与验收报告

- 分支：`feat/hermes-harness-stage20`
- 基线：Stage 19 合入后的 `main`，`1a70d81202c3ada0ccd601457d8227a510a7428f`
- 冻结任务书：`86-harness20-taskbook.md`
- 任务书 `body_sha256`：`7bea2b054787513656b69fa3642883c5d1ffade67642ab1acd328c2f7166767f`
- 任务书 `full_file_sha256`：`707241f8a13cafbe7056c98526b955e04226f731b67818f400824aa693f45cc7`；从 acceptance 目录执行 `shasum -a 256 -c 86-harness20-taskbook.md.sha256` 为 `OK`。
- 当前状态：Stage 20 `CLOSED`。实现提交 `7f817a693d19f708b56c48f257bb5de01229d19e` 已在全新本地 APFS 检出通过单步全量测试；本报告与日志校验和作为后续文档证据提交。
- 本阶段新增 14 个 DFA/dispatch 主干 Java 文件、10 个定向测试文件；没有新增外部依赖。

## Checkpoint

| Checkpoint | 状态 | 结果 |
|---|---|---|
| 20A DFA 核心 + ReAct | `PASS` | 静态 spec/registry、request session、只读 check、成功 commit、ToolInterceptor 接入；真实 AgentOrchestrator ReAct 路径由确定性 fake-model 测试验证一次工具调用及状态推进。 |
| 20B 统一 dispatch + Plan-and-Solve | `PASS` | Plan worker 接收当前请求的 dispatcher 和 effective resolver；ENFORCE ready tasks 顺序执行；WorkerAgent fake-model 测试验证 interceptor 实际派发。完整 Orchestrator recovery flow 未做端到端模拟。 |
| 20C Kernel ENFORCE | `PASS / C1` | Kernel guarded callback 位于共同 interceptor 内侧；后置拒绝 marker 不推进，成功调用推进。测试为确定性 callback/Kernel 集成，不调用真实模型。 |

## 覆盖矩阵

| 路径 | DFA 状态 | 检查与执行位置 | 测试/边界 |
|---|---|---|---|
| ReAct SINGLE / resolver | `COVERED` | AgentOrchestrator 共同 ToolInterceptor；ENFORCE 非法边在 handler 前拒绝 | fake-model AgentOrchestrator 集成覆盖一次稳定 `toolCallId`、一次 handler 执行和成功推进；dispatcher 核心测试覆盖拒绝、失败与模式行为。 |
| Plan-and-Solve initial workers | `COVERED` | `executePlanTask` 传入 effective resolver 与同一 request dispatcher；ENFORCE worker 串行工具执行 | resolver 选择/Kernel callback 测试与 fake-model WorkerAgent dispatch 测试通过；Orchestrator 的完整 Plan 生成到聚合流未做端到端测试。 |
| Plan-and-Solve recovery workers | `COVERED_BY_WIRING` | ReAct recovery 复用原 dispatcher/effective resolver | 源码路径已核对；未单独模拟 recovery 完整模型循环，故不宣称 recovery E2E。 |
| Kernel ENFORCE | `COVERED` | Kernel effective callback 外的共同 ToolInterceptor | 确定性 Kernel 集成验证 postcondition rejection 不推进，成功才推进；没有生产或 live 模型调用。 |
| MultiAgent SWARM / parallel workers | `UNCOVERED` | 不共享顺序 DFA 状态 | ENFORCE 在 planner/worker/model 执行前 fail-close；SHADOW 只记录 `DFA_PATH_UNCOVERED` 并保留执行。 |
| MultiAgent DEBATE | `NO_TOOL_PATH` | 当前 debate 路径不派发工具 | 未来新增工具执行时必须重新验收。 |
| MCP direct execution helper 等绕过 Agent ToolInterceptor 的入口 | `UNCOVERED` | 不在 AgentOrchestrator 共同 dispatcher 边界 | 不声称物理工具入口全局覆盖。 |

## 守卫边界

- DFA 只约束已批准静态 spec 中的工具顺序和请求内状态转移；不等同权限系统，也不证明业务语义、安全性或外部副作用成功。
- Hoare pre/post 仍由 Kernel 现有 guard 检查；Cycle Guard 仍由既有 wrapper/Kernel 检查。DFA 使用独立 request-local 状态，不改变旧 guard 的检查或失败推进语义。
- 处理时序为：身份提取与 reserve → DFA 只读 check → ENFORCE 拒绝或进入既有 callback/guard 栈 → 根据 framework error/明确 guard marker 判定 → 仅成功时 commit → typed receipt。
- 工具异常、framework error、Cycle/Hoare 明确拒绝、Kernel postcondition reject 均不推进 DFA。普通字符串/JSON 业务失败无法由通用 dispatcher 可靠判定，不声称此类语义覆盖。
- receipt 不包含参数正文、密钥或完整工具结果；identity 的参数摘要只用于同 ID payload 冲突检测，不作为调用身份。

## 模式与幂等

- `OFF`：Orchestrator 不创建或附加 dispatcher，不创建 session、不检查、不推进、不写 DFA receipt；默认值仍为 `OFF`。
- `SHADOW`：每个首次逻辑调用都进入 handler，即使 DFA 会判为拒绝；成功且 transition 有效时推进，否则记录观察结果而不改变 handler 结果。并发 stale completion 不覆盖 session 状态。
- `ENFORCE`：非法 transition 在 handler 前拒绝；合法调用进入原 callback/guard 栈，只有成功才推进；MultiAgent 路径未覆盖时 fail-close。
- 同一已完成 `toolCallId` 与相同工具/参数摘要的 replay 复用缓存 response，不重放副作用；同 ID 冲突和 `IN_PROGRESS` 均返回确定性拒绝，不再执行 handler。此身份规则来自冻结任务书 §2.2，优先于 §2.4 对 SHADOW “所有调用仍执行”的概括：这里的“调用”按新的逻辑 dispatch 理解，已完成身份 replay 是明确幂等例外。若将 §2.4 解读为连已完成 replay 也必须重放，则冻结文本存在冲突，需要重新冻结后才能改变此行为。
- 幂等仅保证当前进程内 at-most-once dispatch；handler 完成外部副作用但在缓存 response 前进程崩溃时，不能提供跨进程 exactly-once。
- dispatcher 关闭时停止接收新调用，等待已接纳 handler 完成后再返回稳定快照。当前 drain 无超时，永久挂起的外部 handler 可能阻塞收尾。
- 请求开始时先清除同一 `requestId` 的旧 DFA 回执，避免后续 OFF 执行读取到前一次 ENFORCE/SHADOW 结果；集成回归覆盖了同 ID ENFORCE 后切换 OFF。

## 验证

Java 使用局部 `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`；Maven 单进程串行执行。所有新增测试使用 fake callback/model；未调用 DeepSeek/其他真实 API，未启用生产默认。

| 验证 | 结果 |
|---|---|
| 冻结任务书指定核心定向测试 | `21 tests, 0 failures, 0 errors, 0 skipped` |
| 扩展 DFA、dispatcher、resolver、Stage 16–19 定向回归 | `61 tests, 0 failures, 0 errors, 0 skipped` |
| Plan resolver + fake-model WorkerAgent dispatcher | `4 tests, 0 failures, 0 errors, 0 skipped` |
| AgentOrchestrator ReAct fake-model dispatch | `2 tests, 0 failures, 0 errors, 0 skipped` |
| 初始工作树全量单步 `clean test`（回执清理修正前） | `2273 tests, 0 failures, 0 errors, 10 skipped`；`BUILD SUCCESS` |
| 回执清理修正后定向测试 | `AgentOrchestratorToolActionDfaTest`：`2 tests, 0 failures, 0 errors, 0 skipped`；`BUILD SUCCESS` |
| 实现提交的全新本地 APFS 检出 | Java `21.0.5`、Maven `3.9.16`；`2273 tests, 0 failures, 0 errors, 10 skipped`；`BUILD SUCCESS`；测试后工作区 clean |
| APFS 全量日志 | `evidence/harness20/apfs-clean-test-7f817a69.log.gz`；gzip 完整性检查通过；SHA-256 `f60854d74ed4fde740bb1ee576f5802927e53370459ba33d43d8e03ef1886623` |
| `git diff --check` | 通过 |

全量命令：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

## 限制与最终门禁

- Stage 19 之前观测到的 proto/clean 间歇故障根因仍为 `UNPROVEN`；本次当前工作树 clean test 通过不构成根因修复证明。
- APFS 复验针对实现提交 `7f817a69`；后续仅报告和证据校验和文档进入单独提交，Java 源码与测试未再变化。
- Stage 20 结论限于：静态批准、request-local 工具转移守卫在覆盖矩阵列出的 ReAct、Plan worker 与 Kernel ENFORCE 路径具备确定性契约；不代表 MultiAgent/direct helper 全局覆盖、真实模型效果、业务语义安全、外部 exactly-once 或生产可用性。
