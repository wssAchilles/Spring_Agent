# Knowledge Hub 阶段制研发总索引

> **流程**：研读 → `docs/plans/phase_XX_plan.md` → TDD 编码 → 测试通过 → 更新本表 → 下一阶段  
> **总计划文档**：`plans/RAG长期优化链路-v2.md`（用户指定）  
> **依据**：`AGENTS.md` + 总计划 + `plans/research/RAG深度文献调研-可利用思想-v2.md`  
> **铁律**：无 phase 方案不得写业务代码；算法类改动仍须本阶段方案内的唯一假设与契约

## 阶段序列

| Phase | 标题 | 范围（总计划 v2） | 依赖 | 状态 | 方案文档 |
|---|---|---|---|---|---|
| **01** | 评估地基 | Q0+Q1+指标脚本 | — | **Designed（待批编码）** | `phase_01_plan.md` |
| 02 | 生产可观测与 CI | P0+P1 | 01（指标语义） | Planned | |
| 03 | 安全 Hardening | P2 | —（可与 02 并行） | Planned | |
| 04 | Agent 运行时治理 | R2+R0（cancel/工具预算） | 01（成本对比） | Planned | |
| 05 | CRAG AMBIGUOUS + 自适应路由 | T2+T1 | **01 必须** | Planned | |
| 06 | 中文 IR / 原生加速 | N0–N2 | 01 | Planned | |
| 07 | 重排门控 + RRF 消融 | C 门控 | **01 必须** | Planned | |
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
