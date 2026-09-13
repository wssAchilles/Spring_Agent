# Knowledge Hub 阶段制研发总索引

> **流程**：研读 → `docs/plans/phase_XX_plan.md` → TDD 编码 → 测试通过 → 更新本表 → 下一阶段  
> **总计划文档**：`plans/RAG长期优化链路-v2.md`（用户指定）  
> **依据**：`AGENTS.md` + 总计划 + `plans/research/RAG深度文献调研-可利用思想-v2.md`  
> **铁律**：无 phase 方案不得写业务代码；算法类改动仍须本阶段方案内的唯一假设与契约

## 阶段序列

| Phase | 标题 | 范围（总计划 v2） | 依赖 | 状态 | 方案文档 |
|---|---|---|---|---|---|
| **01** | 评估地基 | Q0+Q1+指标脚本 | — | **Delivered** | `phase_01_plan.md` |
| **02** | 生产可观测与 CI | P0+P1 | 01 | **Delivered** | `phase_02_plan.md` |
| **03** | 安全 Hardening | P2 | — | **Delivered** | `phase_03_plan.md` |
| **04** | Agent 运行时治理与自适应路由 | T1+T2+R2 | 01 | **Delivered** | `phase_04_plan.md` |
| **04-B** | 运筹优化智能体 (OptiAgent) | Gurobi WLS 集成 | 04 | **Delivered** | `phase_04_b_optiagent_plan.md` |
| **04-E2E** | 全链路测试验证 | E2E QA 测试白皮书 | 04 | **Delivered** | `phase_04_e2e_test_plan.md` |
| **05** | CRAG AMBIGUOUS 与自我反思回路 | T2+T1 | **01 必须** | **Delivered** | `phase_05_plan.md` |
| **06** | **中文 IR 与检索底座原生加速** | N0–N2 | 01 | **Delivered** | `phase_06_plan.md` |
| **07** | **重排门控 + RRF 消融** | C 门控 | **01 必须** | **Delivered** | `phase_07_plan.md` |
| 08 | 生成侧评估与引用 | Q2+R1 | 01+04 | Planned | |

## 每阶段标准工作流

1. **双路研读**：学术向（理论/边界）+ 工程向（开源/生产模式）  
2. **沉淀** `docs/plans/phase_XX_plan.md`：证据链、接口/数据流、文件级 todo、测试验收  
3. **用户/门禁批准**（若含算法变更）  
4. **TDD 编码**：先测后码，全绿  
5. **Verify + Review**，更新本表状态为 Delivered

## 进度日志

| 日期 | 事件 |
|---|---|
| 2026-09-10 | 总索引建立；Phase 01 启动双路研读 |
| 2026-09-10 | Phase 01 双路研读完成；`phase_01_plan.md` Designed；**未编码** |
| 2026-09-11 | **Phase 01 Delivered**：Runner/Loader/v2(88)、ANN baseline short@10=0.77 overall@10=0.92 |
| 2026-09-11 | **Phase 02+03 Delivered**：Actuator/CI、SSRF/Security/APIKey/XSS |
| 2026-09-13 | **Phase 04/04-B/04-E2E Delivered**：CascadeRouter、StateGraphExecutor、Gurobi OptiAgent |
| 2026-09-13 | **Phase 05 Delivered**：CRAG AMBIGUOUS 双路合并与反思澄清回路，单测 3/3 全绿 |
| 2026-09-13 | **Phase 06 Delivered**：中文 IR 领域词库增强与受控同义词扩展，单测 4/4 全绿 |
| 2026-09-13 | **Phase 07 Delivered**：RRF 消融与重排自适应动态门控 (Reranking Gate)，单测 4/4 全绿，回归 75/75 全绿 |
