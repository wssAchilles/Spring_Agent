# Hermes Harness Stage 11B-1 离线机制实施与验收

- 结论：**11B-1 离线机制通过本环境验收**；11B-2 真实 DeepSeek/付费 PPC 仍 `BLOCKED`。
- 分支：`feat/hermes-harness-stage11b`；文档基线 `55af8a90`，机制提交 `7f1e6a24`；`main` 未修改。
- 契约：[65-harness11b-canonical-build-env.md](65-harness11b-canonical-build-env.md)、[66-harness11b-taskbook.md](66-harness11b-taskbook.md)；66 正文 SHA-256 为 `7050225d390ca2f0236e108af9e5004e6b56f31a9643fe2bfd6a799192a0f298`，`shasum -c` 通过。

## 实施边界

`ExperienceGuidedProposer` 在既有同域 cue 召回后进一步只接受 `ADOPTED`、无观察失败、未退役的经验；`UNSCOPED_REPLAY` 在读取经验库前返回空。经验条目的 run ID、候选凭单指纹、botId 和唯一强类型 Operator 必须与显式 provenance 对齐，不从 `lesson` 文本推断算子。这个校验是**字段一致性**，不是原始 run 的密码学来源认证。

`OfflineRecursivePpc` 先 Probe 基线，再按建议顺序最多试两种不同算子。每次使用同一用例 ID 集、逐题 `[0,1]` 有限质量分数及安全标志：只有全部安全、无逐题退化且至少一题严格改善才晋级；无改进、缺题、非法分数或 Probe 异常均保留上次验证凭单。不可用算子跳过；输出标记 `OFFLINE_CANDIDATE`，不写经验库。实际代码只新增 `harness/ppc/recursive` 下两类和一个合成域测试类；未修改 Stage 9 编排/Composer、Stage 10 Importer/Store、`AgentOrchestrator`、MCP、HITL、SWARM 或生产开关。

## 验证记录

| 验证 | 结果 |
|---|---|
| 定向 `OfflineRecursivePpcTest` | 11 tests，0 failure，0 error，0 skipped；覆盖 ADOPTED 过滤、跨域/UNSCOPED、来源错配、两轮上限、无改进、安全/异常/缺题回退和不可用算子。 |
| canonical 环境 | 同一代码 HEAD `7f1e6a2469c523fa97aca79ec474c3d56d264d80` 的全新本地 APFS 检出 `/Users/achilles/.codex/worktrees/hermes11b-clean-verify/Agent`；构建前 clean worktree，proto/core 无旧 `target/`。Temurin Java 21.0.5、Maven 3.9.16；单一 Maven 进程，无手动 proto 预生成。 |
| 单步全量命令 | `JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`；退出码 0，`BUILD SUCCESS`，耗时 01:11。 |
| 精确计数 | 六个测试模块汇总：**2179 tests，0 failures，0 errors，8 skipped**；新增测试 11/11；`HarnessExperienceStage10Test` 5/5、`ExperienceLibraryContractTest` 4/4。 |
| 构建链 | Reactor 43 模块；proto 34/43、core 39/43、qknow-tests 43/43。proto 编译 47 个 Java 源文件，构建后生成源码 47 个、class 108 个。 |
| Stage 9 隔离回归 | Stage 10 测试仍断言 7 条 `UNSCOPED_REPLAY`：5 `OBSERVED`、2 `REJECTED`、其中 2 条失败归因 `UNRESOLVED`；`ADOPTED=0`、`INVALID_FIXTURE=0`、正向召回为空。 |
| 外部调用 | 新测试只使用合成域和确定性 Java Probe；父 POM 默认排除 `live` 测试组。本轮未提供或调用 DeepSeek 付费凭据，也未启动真实模型实验。 |

完整 Maven 原始日志无损压缩为 [clean-test.log.gz](evidence/harness11b1/clean-test.log.gz)，归档 SHA-256 为 `072b669c366544319f1297685379a23a201ed59fc571895a64ba15c07ae193e8`；解压后原始字节 SHA-256 为 `cfcaaadbd9c23704f4d932bb9ca3d9dd4a243eed749f322edf6c744c4cf92b59`。在证据目录运行 `shasum -a 256 -c logs.sha256` 可复核归档；`gzip -t` 与解压哈希均通过。日志在归档前完成基本凭据模式扫描，无匹配项。

## 保留风险与下一门禁

1. 11A 的历史 proto/target 间歇故障根因仍为 **`UNPROVEN`**。本次干净 APFS 单步成功是环境内观察，不是因果修复；重现时必须按 65 停止并保全现场。
2. 本机制仅验证合成固定用例下的有界状态转移；不证明真实模型质量、跨域泛化、统计显著性或全局最优。已接受候选只有本次 Probe 的逐题非退化性质。
3. provenance 由离线调用者显式提供，现阶段只核对字段。若未来接生产，需另行建立可验证 run manifest、授权边界与风险评估；本轮无生产接线。
4. 11B-2 的真实 DeepSeek、token/cost 和付费 PPC 必须另立并冻结任务书、预算及授权；本报告不解封 11B-2。
