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
| **08** | **生成侧评估与精准溯源 (Citation)** | Q2+R1 | 01+04 | **Delivered** | `phase_08_plan.md` |
| **09** | **系统安全防线与长事务稳定性加固** | P0 致命级安全与防雪崩 | 03+04 | **Delivered** | `phase_09_plan.md` |
| **10** | **智能体运行时反思熔断、工具截断与状态图自愈** | ReAct 循环韧性与防死循环熔断 | 04+05 | **Delivered** | `phase_10_plan.md` |
| **11** | **RAG 混合检索与语义缓存深层治理** | 混合向量图谱性能、Semantic Cache 淘汰防护与评测集扩容 | 01+06+07 | **Delivered** | `phase_11_plan.md` |
| **12** | **前端高保真单色毛玻璃与流式渲染平滑体验** | 流式 Markdown/KaTeX 渲染性能、平滑打字机与视觉一致性 | — | **Planned** | `phase_12_plan.md` |

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
| 2026-09-13 | **Phase 08 Delivered**：生成侧评估与精准溯源 (Citation)，严格上下文对齐杜绝幽灵引用，单测 4/4 全绿，全量 79/79 全绿 |
| 2026-09-13 | **Phase 09 Delivered**：传输层 SSRF 防御、切片长事务解耦、gRPC 反应式双向取消、FlyFlow/WebSocket 零信任鉴权、前端 Axios AbortController 成对清理，契约测试 18/18 全绿，全量回归 686/686 全绿，前端打包 0 错误通过 |
| 2026-09-13 | **Phase 10 Delivered**：ReAct 循环连续重复短路与 6 步滑动窗口熔断（ReActCycleGuard）、工具 10s 异步超时与 Head-Tail 16KB 智能截断（ToolResilienceDecorator）、Reflexion 反思重试反馈回填闭环（ReflectiveAgent）、彻底对齐 DeepSeek API 唯一模型基线，契约测试 8/8 全绿，全量回归 694/694 全绿！ |
| 2026-09-13 | **Phase 11 Delivered**：EnhancedSemanticCacheService（无锁 L1 缓存、极性翻转两级门禁、空结果哨兵防穿透、Jitter 随机防雪崩）、ResilientHybridRetrievalCoordinator（CompletableFuture 响应式编排、Neo4j 独立仓壁隔离线程池、250ms 软超时 Fail-Open 降级与 RRF 融合）、RagRetrievalService 伪并发与超时治理、EvalRegressionGate 自动化防退化门禁，契约测试 10/10 全绿，全量回归 704/704 全绿！ |
