# Stage 13 Controlled Real PPC (CR-PPC-1) 预 live 验证

## 结论

- Stage 12 已由 `dc566b6c` 提交并在全新本地 APFS 检出单步 `clean test` 通过，交付状态为 `CLOSED`；构建历史间歇故障根因仍是 `UNPROVEN`。
- Stage 13 的 **pre-live 接线通过**，真实实验仍为 `BLOCKED`。真实 DeepSeek 调用 0 次、真实 probe 0 个、费用 ¥0、受控 `ADOPTED=0`；这是实验未开始，不能写成 `NO_ADOPTED_EXPERIENCE`。
- 用户在聊天中提供的密钥已暴露，本轮未使用、未写入代码或证据。必须先撤销并轮换，再通过执行进程的 `DEEPSEEK_API_KEY` 安全注入。主仓库 `.env` 存在且被忽略，但不含 `DEEPSEEK_API_KEY`；Maven 进程环境亦未设置该变量。
- 不接生产、不写真实业务 Domain、不进入 11B-2B 或 Stage 14，不宣称 RAG、工具或模型效果。

## 受控接线与边界

受控源只接受独立 `h11b2a_fixture_*` PostgreSQL 库；JDBC 读取前对 68 报告的七类有序数据快照逐项核验，对 69 冻结的 `fixture.sql` 和 `verify-retrieval.sql` 校验文件 SHA-256。10 题按 BOOTSTRAP/HOLDOUT 各 5 题加载；4 条 RAG 命中来自 SQL 检索结果，document/segment ID、正文 SHA-256 与实际返回正文分别存证。工具 callback 从冻结表装配，并验证真实入参；HISTORY 按表中轮次装配并保存 source ID。模型首个 Prompt 必须包含实际检索正文与有序 HISTORY；Agent 证据必须标记 `CONTROLLED_FIXTURE`、RAG 题 `ragUsed>0`、工具执行无未授权或失败项。受控 Domain 为 `(110201,110201,controlled_harness11b2a)`，不是生产映射。

baseline、A=`CONCISE_SYSTEM_PROMPT`、B=`ADAPTIVE_MEMORY_WINDOW` 的 canonical 凭单及 SHA-256 指纹在模型调用前核验，完整 canonical 内容写入隔离 `vouchers.json`。排程固定为 bootstrap 30 probes（每题 baseline/A/B 重复 2 次）；只有预注册规则选出的唯一胜者进入 holdout 20 probes（每题 baseline/胜者重复 2 次），并在 holdout 前写入 `selection.json`。受控 runner 复用 `TaskLevelRecursivePpc` 的有界探测机制，但每题仅一轮、重复两次；**不声称每题完成 Stage 12 的两轮递归搜索**。

每次模型调用前检查 50 probes / 100 calls / 200000 tokens / 单次 2000 输出 tokens / ¥10 的上限，调用后按 provider usage 计量；usage 不可得时费用记为未知（`null`），立即停止，不把未知写成零费用。只有 50 个有效 probe、唯一胜者、固定 rubric 与安全门禁全部满足，才可在隔离 store 写最多一条 `ADOPTED`，并绑定受控 provenance 与可回读的经验条目。默认测试不触发 live；真实调用只在显式 `-Plive -Dstage13.live=true` 的专门测试中启用。

## 验证记录

- 隔离 PostgreSQL 库：`h11b2a_fixture_stage13_20261102`；冻结 SQL 的 10 题、4 条检索命中、4 组工具绑定、2 组 HISTORY 与七类快照一致。带真实 JDBC、真实 `AgentOrchestrator` 和 fake ChatModel 的定向测试：`ControlledPpcRunnerTest` **11 tests、0 failures、0 errors、0 skipped**。其中 50 个 fake-model probes 完成 30+20 排程，唯一选择 A，未写 `ADOPTED`；另覆盖数据漂移拒绝、无凭据阻断、合成来源阻断、未授权工具、usage 缺失及调用前预算停止。
- Java 21.0.5 Temurin、Maven 3.9.16、单一 Maven 进程、无手工 proto 预构建，默认单步命令：`mvn -B -ntp -f backend/pom.xml -pl tests -am clean test`。当前 Stage 13 工作树结果 `BUILD SUCCESS`；Surefire XML 复算 **2194 tests、0 failures、0 errors、10 skipped**。日志 `/tmp/stage13-prelive-release-clean-20261102.log` 的 SHA-256 为 `41eb0ae276d44b5e24365401316f7f0c19fd7a51548e419b81e0f4ba79242d91`。
- 提交 `f2998d74` 后，全新本地 APFS 检出 `/Users/achilles/.codex/worktrees/hermes13-clean-gate/Agent` 在同一 HEAD 再次单进程单步 `clean test`：`BUILD SUCCESS`，Surefire XML **2194 tests、0 failures、0 errors、10 skipped**；日志 `/tmp/stage13-clean-gate-f2998d74.log` 的 SHA-256 为 `fba0f2e22a1103dcf5da9a833a6c19ef02114a2c09a21325583096841a105e04`。同一检出连接隔离 PostgreSQL 的定向测试 **11/11 通过**，检出工作区仍 clean。此前历史构建间歇故障根因仍是 `UNPROVEN`。
- 2026-11-02 读取 DeepSeek 官方[模型与价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing)及[思考模式](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)：目标模型 `deepseek-flash`，显式 `thinking.type=disabled`，当前高峰未缓存输入 ¥2/百万、输出 ¥8/百万。live 前仍须重核官方价格、参数与节假日计费。

## Live 放行条件

1. 已暴露密钥撤销并轮换；新密钥只由执行环境安全注入，不写仓库、命令参数或日志。
2. 每次 live 前确认执行 HEAD 与上述已验证提交一致；如源码变更，必须在全新本地 APFS 检出重新通过默认单步 `clean test`，工作区 clean。任何 proto/clean 故障先保全现场，根因仍记 `UNPROVEN`。
3. 在正式 live 前重新确认官方价格与模型参数；一次连通性检查计入 100 调用/200000 tokens/¥10 预算。
4. 真实 30+20 排程完成后才判定 `ADOPTED=1` 或 `NO_ADOPTED_EXPERIENCE`；预算、usage、来源或证据缺口分别按 `BUDGET_EXHAUSTED`/`BLOCKED` 停止，不手工补经验。
