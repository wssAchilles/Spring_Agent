# Hermes Harness Stage 18 实施与验收报告

- 契约：`79-harness18-audit.md`、双哈希冻结的 `80-harness18-taskbook.md`。
- 分支：`feat/hermes-harness-stage18`。
- 实施提交：`872008a1698287ad0a5275848fdeaa21da93c650`。
- 交付状态：建议 `CLOSED`（仅 Stage 18 静态路由架构门禁；本报告提交后还须确认原工作树 clean）。原工作树历史 proto 间歇故障的根因仍为 `UNPROVEN`。

## 一、Profile 与确定性路由

只读 registry 注册四个版本为 `1`、路由域为 `DEFAULT` 的 profile。`baseline` 是唯一 `pipeline=null`、`capabilityTag=null` 的无变异通配 profile；其余三个均由代码白名单批准，不从数据库、请求或模型响应动态创建。

| 请求可观察能力 | profileId | 流水线 | 结果原因 |
|---|---|---|---|
| `PROMPT_ONLY` | `prompt-profile` | `CONCISE_SYSTEM_PROMPT` | `MATCHED_CAPABILITY` |
| `HISTORY` | `history-profile` | `ADAPTIVE_MEMORY_WINDOW` | `MATCHED_CAPABILITY` |
| `TOOL` | `tool-profile` | `TOOL_GUIDANCE_SYNTHESIS` | `MATCHED_CAPABILITY` |
| `RAG`、`COMPOSITE` | `baseline` | 无变异 | `NO_APPROVED_ROUTE` |
| 未知能力 | `baseline` | 无变异 | `UNKNOWN_CAPABILITY` |
| 非 `DEFAULT` 域 | `baseline` | 无变异 | `DOMAIN_MISMATCH` |

RAG 自动挂载的知识库工具不算独立 TOOL；显式非知识库工具必须可解析才算 TOOL。当前没有可信业务 Domain 字段，`DEFAULT` 仅表示固定路由命名空间。Stage 16 的 Prompt→Memory 组合继续有效，但本阶段没有注册其 profile。未批准、未知 ID 或能力不匹配的 profile 均不能被执行。

## 二、OFF / SHADOW / ENFORCE 与失败边界

- **OFF**：直接返回原 voucher，不分类、不路由、不变异、不生成路由 receipt；默认 SINGLE 路径不变。
- **SHADOW**：计算白名单路由与候选身份，实际执行原 voucher；选中 profile 的准备失败回退原 voucher。与 Kernel Bridge 同时启用时保留 Stage 17 的 SHADOW 回退语义。
- **ENFORCE**：仅对匹配且批准的非 baseline profile 顺序应用算子；正常不匹配回退 baseline。已选 profile 准备失败或与 Kernel Bridge 同时启用时，在模型调用前 fail-close 并记录 `FAIL_SAFE`。

正常回退与执行失败分开记录，不能将未知/不批准/profile 不匹配记作候选执行失败，也不能将 ENFORCE 准备失败伪装成正常 baseline。运行时不改变生产默认配置。

## 三、身份与证据

Profile fingerprint 覆盖静态 profile 的 ID、版本、域、能力、流水线、批准状态与来源；相同 profile 稳定，不同身份及重复指纹在 registry 中拒绝。Route fingerprint 覆盖域、能力、所选 profile fingerprint、原因和 fallback，不包含 requestId，使相同决策跨请求可复算。

active 模式 receipt 记录 `requestId`、`mode`、`profileId`、`profileVersion`、`profileFingerprint`、`pipelineFingerprint`、`routeReason`、`fallback`、`routeFingerprint`、`baselineFingerprint`、`candidateFingerprint`、`executedFingerprint`、`preparedSuccess`、`failClosed`、`failureCode`。OFF 不产生 receipt；证据不包含提示词正文或密钥。

## 四、验证记录

| 门禁 | 结果 |
|---|---|
| `80` 正文 / 全文件 SHA-256 | `d0edc3b8eb1aebef04f3911d89d58c918cbcd31680f4b8528a3eb16479d883df` / `eb76b5fdf370e711f6d162577eea1a79a1ae4c7ddfa8cca537be07b01c342f6b` |
| `shasum -a 256 -c 80-harness18-taskbook.md.sha256` | `OK` |
| 定向 `HarnessRouterTest,PipelineRuntimeBridgeTest` | `14/14 PASS` |
| 当前工作树首次单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` | **FAIL**：proto 插件报告生成 1 文件，实际 `generated-sources/protobuf/java` 为 0、proto `classes` 仅有 `hermes.proto`，core 编译缺少 `ChatRequest`；根因 `UNPROVEN`。原始日志：`/var/folders/j8/h1syfql125b1wdt9p6ywfkd40000gn/T/harness18-worktree-clean-test.H22k4tXHr5`。 |
| 当前工作树第二次同一单步命令（未预构建） | `BUILD SUCCESS`；432 份 Surefire 报告，2220 tests、0 failures、0 errors、10 skipped。原始日志：`/var/folders/j8/h1syfql125b1wdt9p6ywfkd40000gn/T/harness18-worktree-clean-retry.JbGE6HMABS`。这不证明首次故障已修复。 |
| 提交后全新本地 APFS 检出，同 HEAD 单步 `clean test` | `/Users/achilles/.codex/worktrees/hermes18-clean-gate/Agent`，APFS `/dev/disk3s5`，HEAD `872008a1698287ad0a5275848fdeaa21da93c650`；未预构建 proto、一次运行 `BUILD SUCCESS`。原始日志：`/var/folders/j8/h1syfql125b1wdt9p6ywfkd40000gn/T/harness18-clean-gate.TyNFWMo6ys`。 |
| Surefire 精确总数 / failures / errors / skipped | 当前工作树重试：432 份报告、`2220 / 0 / 0 / 10`；全新 APFS 检出：432 份报告、`2220 / 0 / 0 / 10`。 |
| 原工作树与验证检出 `git status` | 验证检出初始和测试后均 clean；原工作树实施提交后只剩本报告未跟踪，报告提交后须复核 clean。 |
| 真实 API 调用 / 生产默认启用 | 均未执行；默认保持 OFF。 |

## 五、结论边界

本阶段仅验证静态、确定性、白名单 profile 路由与 opt-in 运行时桥接；不验证真实模型效果，不实现多域 profile 管理、在线 PPC、自动采纳、学习型 router 或生产默认接入。提交后全新 APFS 检出的单步全量门禁通过，因此建议在本报告提交并复核原工作树 clean 后，将 **Stage 18 架构交付**标为 `CLOSED`。该判断不代表构建故障已修复：原工作树首次 proto 生成失败仍是已保留的历史风险，根因 `UNPROVEN`；后续出现同类失败须保全现场，不归因于路由实现，也不能用重试成功冒充稳定性修复。
