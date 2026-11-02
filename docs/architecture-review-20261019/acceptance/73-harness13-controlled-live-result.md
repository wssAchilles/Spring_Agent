# Stage 13 受控 live 第二批结果

- 状态：`NO_ADOPTED_EXPERIENCE`；这是完成预注册 bootstrap 筛选后的质量结论，不是前置门禁 `BLOCKED`。
- 代码：`b3ca8a41`（实验分支），全新本地 APFS 检出在同一代码上运行；未修改生产配置、`AgentOrchestrator` 或 `main`。
- runId：`crppc1-5b398395-a8cd-4106-a42b-5a678d410e2e`。此批次从 B01 重新开始，未续接或覆盖首次 `BLOCKED` 的 13 probes。
- 预算总账（含一次连通性检查）：30 probes / 47 model calls / 38,746 tokens / 计量费用 ¥0.10017048；上限分别为 50 / 100 / 200,000 / ¥10.00，单次输出上限 2,000 tokens。费用是 runner 根据 provider usage 和官方价格计算的值，不是账单结算证明。
- Bootstrap：30/30 probes `valid=true` 且 `safe=true`；baseline 8/10、A=`CONCISE_SYSTEM_PROMPT` 8/10、B=`ADAPTIVE_MEMORY_WINDOW` 9/10 通过固定 rubric。12 个 RAG probes 均有 `ragUsed>0`，12 个工具任务 probes 均留存实际调用证据；所有 probes 的 execution mode 均为 `CONTROLLED_FIXTURE`。
- A/B 均未达到预注册的候选自身 10/10 通过门槛。B01 的 baseline 仅第一轮失败，未形成两轮稳定改善；B03 的 A 两轮均失败、B 第二轮失败。未选出唯一胜者，因此按冻结任务书在 bootstrap 后停止，**未揭封或运行 20 个 holdout probes**，不能声称 50-probe 全程完成。
- `ADOPTED=0`；没有 `selection.json`、`provenance.json` 或 `experience/` 写入，不手工补经验。该受控结果不证明真实业务 Domain、生产收益或统计显著性。

验证：全新检出单步 `mvn -B -ntp -f backend/pom.xml -pl tests -am clean test` 为 2196 tests / 0 failures / 0 errors / 10 skipped；隔离 PostgreSQL 定向测试 12/12 通过。显式 `-Plive` 测试 1/1 通过。新 run 的 `checksums.sha256` 全部校验 `OK`；清单 SHA-256 为 `bb3f26defd91830c4fb221500f18fe562b163d98637887dcf4e19c80e5873830`，`run.json` SHA-256 为 `49ce0c3a903e295d986bfcebf4b694190067c1fca06c086a4a5b1ad1bbd12499`。隔离检出工作区 clean；原始证据保存在该检出的 gitignored `experiment/outputs/harness11b2a/<runId>/`。

本批使用的 Key 曾在聊天中暴露。实验已完成，应撤销并轮换该 Key，核对调用/账单；本报告不含凭据。后续若要改变 rubric、候选或开展新的 live，须重新冻结实验契约和预算，不把本批结果改写为采纳。
