# P0-15 多智能体生产编排收口与 SWARM v2 实验封存报告 (23-p015-closure)

- **文档编号**：`23-p015-closure`
- **日期**：2026-10-24
- **主分支基线**：`main` @ `39ff8db0` (`feat(agent): 接入多智能体 SWARM/DEBATE 生产编排链路`)
- **实验封存分支**：`experiment/p015-swarm-v2` @ `4769abe6` (`experiment(agent): 封存SWARM v2软截止与工具预算实验`)
- **当前状态**：`CLOSED_WITH_EXPERIMENTAL_ARCHIVE`（工程接线验收通过；SWARM v1/v2 质量收益未证实，实验已封存，主基线默认关闭）

---

## 1. P0-15 最终结论

1. **功能接线已验证**：
   - 在主基线 `39ff8db0` 中，`ChatStreamServiceImpl` -> `MultiAgentOrchestrationService` -> `AgentOrchestrator`（`SINGLE` / `SWARM` / `DEBATE`）的生产级热路径接线、三级开关解析优先级（全局默认关闭 `hermes.orchestration.enabled=false` + Bot 级 `parameters.orchestrationMode` + 请求级 `extJson`）、硬超时回退（Fallback to SINGLE）及审计字段落库均已通过契约测试与真实在线 E2E 验证（详见 `16-p015-final-acceptance.md`）。
2. **SWARM v1/v2 质量收益未证实**：
   - **SWARM v1（Set A/B 90-run 评测）**：Set B 上 `SWARM` 触发 `83.3%` 超时回退（25/30），整体与有效样本质量均未显著优于 `SINGLE`。
   - **SWARM v2（P0-A/B/C 修复后 Set B `seed=101` 10 题小验证）**：`SINGLE` 10/10 有效，双盲质量均分 `2.35`；`SWARM v2` 仅 3/10 有效，有效样本质量均分 `2.00`（虽单题最大退化从 v1 的 `1.50` 缩小至 `0.50` 且 `B007` 首次验证了 `PARTIAL (completed=1, cancelled=1)` 部分聚合收益，但整体质量仍未跑赢 `SINGLE`）。
3. **v2 关键门禁未达标**：
   - **Fallback 率**：`70.0%`（7/10 触发 `ORCHESTRATION_TIMEOUT` 回退），未达到 `< 20%` 门禁；
   - **端到端延迟**：`P50 = 23513.31ms`（23.51s）、`P95 = 29922.42ms`（29.92s），未达到 `P95 <= 25s` 门禁。
4. **停止决策**：
   - **不再进行完整 90-run 复测**，不再在 15s 同步对话链路内继续调参；
   - **生产与主基线默认保持关闭**（`hermes.orchestration.enabled=false`，所有 Bot 维持 `SINGLE`）。

---

## 2. 证据路径索引

| 资产类别 | 路径 / 标识 | 说明 |
| :--- | :--- | :--- |
| 主基线提交 | `main` @ `39ff8db0` | 保留 P0-15 v1 编排接线与默认关闭配置，不含 v2 实验代码 |
| 实验封存分支 | `experiment/p015-swarm-v2` @ `4769abe6` | 完整封存 SWARM v2 代码（P0-A/B/C）、新增契约测试、研究台账与评测脚本/汇总 |
| 工程接线终验报告 | `docs/architecture-review-20261019/p015/16-p015-final-acceptance.md` | P0-15 v1 功能接线、契约与 E2E 验收记录（主分支跟踪） |
| 质量评测报告 (v1) | `docs/architecture-review-20261019/p015/17-p015-quality-eval.md` | 封存于 `experiment/p015-swarm-v2` 分支 |
| 质量评测独立复核 (v1) | `docs/architecture-review-20261019/p015/18-p015-quality-eval-review.md` | 封存于 `experiment/p015-swarm-v2` 分支 |
| SWARM v2 聚焦研究报告 | `docs/architecture-review-20261019/p015/20-p015-swarm-v2-research.md` | 封存于 `experiment/p015-swarm-v2` 分支（含 `20-p015-swarm-v2-research.ledger.json`） |
| SWARM v2 代码与小验证报告 | `docs/architecture-review-20261019/p015/21-p015-swarm-v2-code-report.md` | 封存于 `experiment/p015-swarm-v2` 分支 |
| SWARM v2 验收裁决报告 | `docs/architecture-review-20261019/p015/22-p015-swarm-v2-acceptance.md` | 封存于 `experiment/p015-swarm-v2` 分支 |
| 小样本脱敏汇总数据 | `experiment/outputs/p015_swarm_v2_pilot_b101/summary.{json,csv}` | 封存于 `experiment/p015-swarm-v2` 分支（`raw_runs.jsonl` 已由 `.gitignore` 排除） |
| 本地临时冷备快照 | `/tmp/p015-swarm-v2-tracked.patch`、`/tmp/p015-swarm-v2-assets/` | 阶段 0 安全备份副本 |

---

## 3. 残余缺陷清单（封存于实验分支，不在同步链路继续修补）

| 缺陷编号 | 级别 | 现象与根因 | 影响范围 |
| :--- | :--- | :--- | :--- |
| `P0-NEW-1` | P0 | **单 `RAGWorker` 多轮同步生成超过软截止窗口**：单个 `RAGWorker` 执行 1~2 次知识库检索（Embedding + Milvus + Cross-Encoder 重排）叠加 2~3 轮 DeepSeek `ReactAgent` 串行生成耗时 `9~11s`，超过扣除 `Supervisor.planTask`（`2.1~2.8s`）后的软截止剩余窗口（约 `8.3s`），导致 `11000ms` 时出现 `completed=0, cancelled=2`。 | SWARM 同步模式（已封存） |
| `P0-NEW-2` | P0 | **`ContractNetDispatcher` 同 Phase 并发派给慢 Worker**：合同网竞标无视 Worker 是否已在当前 Phase 承担高耗时检索任务，将同 Phase 的多个子任务同时派给 `RAGWorker`，缺乏同 Phase 并发独占惩罚与无工具快速 `AnalysisWorker` 兜底通道。 | SWARM 同步模式（已封存） |
| `P1-NEW-1` | P1 | **Aggregator 同步全量生成撞硬超时**：`SupervisorAgent.aggregateResults` 为同步全量 LLM 调用（耗时 `4.5~6s`），即便在 `11000ms` 触发 `PARTIAL` 返回，叠加聚合耗时后仍可能触碰 `15000ms` 外层硬超时（如 `B005` 在 `15004ms` 超时回退）。 | SWARM 同步模式（已封存） |

> **说明**：上述三项缺陷仅存在于多智能体 `SWARM` 实验路径；由于生产基线默认关闭编排（`hermes.orchestration.enabled=false`）且全部 Bot 固定为 `SINGLE`，**对当前生产基线运行无任何影响**。

---

## 4. 后续演进与授权边界

1. **禁止在 15s 同步对话链路内继续调参**：在线同步问答链路（15s 预算）在物理时延上不适合承载“LLM 规划 + 多 Worker 多轮 ReAct 检索生成 + LLM 二次聚合”的三段式串行/并发调用。
2. **重启前置条件**：未来若需重启多智能体协同能力，必须另立**“异步深度研究模式（Async Deep Research / 60s~120s 预算 + 流式增量聚合）”**独立课题，重新通过 Research-to-Implementation Gate 审批。
3. **生产启用隔离边界**：
   - 严禁在线上环境默认开启 `hermes.orchestration.enabled`；
   - 任何针对特定 Bot 的灰度开启、A/B 实验或生产推广（Promotion），必须获得独立专项授权。

---

## 5. 主基线现场恢复验证证据

1. **Git 基线与工作区状态**：
   - 当前分支：`main`
   - HEAD Commit：`39ff8db0` (`feat(agent): 接入多智能体 SWARM/DEBATE 生产编排链路`)
   - 实验封存分支：`experiment/p015-swarm-v2` (`4769abe6`)
2. **默认配置核验**：
   - `backend/qknow-hermes/qknow-hermes-starter/src/main/resources/application.yml` 中 `hermes.orchestration.enabled` 为 `${HERMES_ORCHESTRATION_ENABLED:false}`。
3. **数据库与缓存状态核验**：
   - `kb_agent_config` 表中 ID `1..6` 的 `parameters` 全部确认为 `{}`；
   - `semantic_cache_store` 保持基线 `count=3, max(id)=70`；
   - `conversation` 表中 `p015-%` 临时评测会话数为 `0`。
4. **端口监听核验**：
   - 临时验证端口 `8098`、`9098` 均无监听进程（`free`）。
5. **主基线全量回归测试精确计数**：
   - 执行命令：
     ```bash
     JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
     PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
     mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
     ```
   - `qknow-tests` 模块精确计数：`Tests run: 1919, Failures: 0, Errors: 0, Skipped: 8`
   - 全模块（`-pl tests -am`）总计：`Tests run: 2059, Failures: 0, Errors: 0, Skipped: 8`（`BUILD SUCCESS`）。
