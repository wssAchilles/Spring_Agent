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
| **12** | **前端高保真单色毛玻璃与流式渲染平滑体验** | 流式 Markdown/KaTeX 渲染性能、平滑打字机与视觉一致性 | — | **Delivered** | `phase_12_plan.md` |
| **13** | **数据运维底座、Embedding 维度漂移防御与索引健康自愈体系** | 维度与模型签名防御门禁、双向反熵对齐自愈、冷启动结构化短路与批量删除孤儿治理 | 01+02+03 | **Delivered** | `phase_13_plan.md` |
| **14** | **长文本层次化 Chunking 策略、Markdown 结构感知分块与 Parent-Child Small-to-Big 检索闭环** | 标题栈面包屑注入、表格行级表头复制传播、代码块/版本号保护、Parent Max-Pooling 检索得分继承与 20KB 预算优雅降级 | 01+06+07+11 | **Delivered** | `phase_14_plan.md` |
| **15** | **生产级可观测性、Prometheus 核心埋点与大模型调用成本/延迟治理体系** | RAG 7阶段微观Timer与门控Counter、DeepSeek纳元定点无锁成本累加器、Agent ReAct轮次与熔断监控、LangFuse异步非阻塞批处理 | 01+02+04 | **Delivered** | `phase_15_plan.md` |
| **16** | **生产级灾备容灾、异构数据一致性备份恢复与线上真实 Query 难例自动挖掘体系** | PostgreSQL/PGVector/Neo4j 异构备份与自愈恢复、recall_log 月度范围分区与冷热归档、线上真实 Query 难例挖掘与无感结构保留脱敏流水线、不可变基准与 EvalRegressionGate 双轨门禁 | 01+02+13+15 | **Delivered** | `phase_16_plan.md` |
| **17** | **多知识库联合检索并发编排、跨库得分校准重排、多租户 RBAC 零泄露隔离与统一全局上下文预算熔断治理** | 跨多库 CompletableFuture 响应式编排、单库 2500ms 软超时 Fail-Open 降级与独立仓壁隔离、跨库 RRF (k=60) 分数校准精排与全局 20KB 预算硬截断、PermissionFilter 彻底根治管理员返回 null 漏洞与语义缓存权限哈希防侧信道 | 01+06+07+11+14 | **Delivered** | `phase_17_plan.md` |
| **18** | **原生加速深水区 (N1+N2)：Rust SIMD 向量批量核与 Tantivy 中文 BM25 原生检索引擎闭环** | `vecsim-jni` AVX2/NEON SIMD 并行批量点积核（1536维对齐、堆外 DirectByteBuffer 零拷贝）、`tantivy-server` REST/Axum + jieba-rs 中文倒排分词服务化（IndexWriterActor 节流批量提交与租户硬隔离）、`KeywordRetriever` 优先召回与 PG 双轨容灾降级（二级键防抖稳定排序） | 06+11+17 | **Delivered** | `phase_18_plan.md` |
| **19** | **全业务模块高保真 UI/UX Pro Max 体验升华与微交互打磨** | 严格调用 `.shared/ui-ux-pro-max` 规范，对 10 大前台模块（知识库、切片调试、智能体编排、系统设置等）全面落地 Monochromatic Glassmorphism 单色毛玻璃、骨架屏与流畅动效 | 12 | **Delivered** | `phase_19_plan.md` |
| **20** | **生产级容器编排一键交付、异构灾备实战演练与全链路压测验证** | Docker Compose / K8s 全栈编排闭环、PostgreSQL+PGVector+Neo4j 灾备恢复实战演练、全链路高并发与端到端评测回归 | 02+15+16+18 | **Delivered** | `phase_20_plan.md` |
| **21** | **动态自适应 Agentic 记忆图谱与时序衰减遗忘机制** | 动态艾宾浩斯强化遗忘递推（Theorem 1.1&1.2）、Neo4j 用户偏好实体图与 2-Hop 激活扩散（Theorem 2.1~2.3）、多目标 Pareto 重排与温度 Sigmoid 余弦标定（Theorem 3.1&3.2）、异步 Worker 线程池调度与 Redis 会话分布式写锁安全增量裁剪防消息丢失 | 04+10+11 | **Delivered** | `phase_21_plan.md` |
| **22** | **多智能体动态分工协作、蜂群通信协议与分层任务意图分解** | 基于 HTN 与 Kahn 算法的 DAG 阶段分层调度（TopologicalPhasedDispatcher）、扩展 FIPA-CNP 合同网竞标路由引擎（ContractNetDispatcher）、CAS 版本化响应式共享黑板总线（SharedBlackboard）、蜂群调用深度与语义振荡死锁熔断器（SwarmLoopGuard） | 04+10+21 | **Delivered** | `phase_22_plan.md` |
| **23** | **反应式工作流编排引擎、非阻塞人机协同审批 (HITL) 与 SAGA 事务状态补偿自愈体系** | 彻底废除 approvalFuture.get 24小时物理阻塞线程池、基于 Delimited Continuation 的 0 线程快照挂起恢复、条件分支动态递归剪枝（SKIPPED 传播）与汇聚网关自适应防死锁、基于转置图 G^R 拓扑序的分布式 SAGA 事务状态补偿回滚与类型安全上下文访问器 | 04+10+22 | **Delivered** | `phase_23_plan.md` |


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
| 2026-09-13 | **Phase 12 Delivered**：StreamingMarkdownEngine（未闭合代码/公式/标签虚拟补全、DeepSeek `<think>` 思考流解耦脉冲卡片、DJB2 代码高亮缓存）、useChatScrollController（Scroll Lock 防强制拽回、单色毛玻璃回到底部浮标、未读统计）、Monochromatic Glassmorphism 单色毛玻璃分层规范体系（L0~L3）全面落地、SSE RAF 16.6ms 帧预算微批合并调度消除主线程微任务饿死，契约测试 4/4 全绿，前端生产构建 0 错误通过，全量回归 708/708 全绿！ |
| 2026-09-13 | **Phase 13 Delivered**：EmbeddingDimensionGuard 严格校验 1536 维基线与不可变模型指纹防漂移、VectorReconciliationEngine 双向反熵自愈管道（Keyset 游标补漏 + 孤儿向量物理清理）、彻底修复 KmcDocumentSegmentServiceImpl 批量删除仅删单条的重大孤儿遗留 Bug、KmcKnowledgeBaseServiceImpl 级联物理清理 vector_store、RagZeroState 冷启动与零召回短路契约（杜绝透传 "null" 脏上下文与幻觉推理），契约测试 4/4 全绿，全量回归 712/712 全绿！ |
| 2026-09-14 | **Phase 14 Delivered**：StructureAwareMarkdownSplitter 结构感知分块器（标题栈多级面包屑注入、表格完整性与超长行级表头跨块复制传播、代码块围栏与版本号防误切）、Parent-Child Small-to-Big 检索装配闭环（Parent 继承最高子块得分 Max-Pooling、严格保序去重、20KB 预算超限自适应优雅降级回 Child 原文），分块器单测 6/6 全绿，门禁测试 4/4 全绿，既有上下文单测 6/6 全绿，全量回归 722/722 全绿！ |
| 2026-09-14 | **Phase 15 Delivered**：RagMetricsService（8阶段微观耗时 Timer、缓存与重排门控 Counter、有界基数 ≤40 Meters 杜绝 TSDB 爆炸）、DeepSeekCostGovernor（金融级纳元定点整数、LongAdder 无锁分段累加、消除浮点累加漂移、并发请求 Gauge 与官方 V3/R1 费率实时记账）、AgentMetricsService（ReAct 步数分布、单轮耗时、工具执行状态与死循环熔断拦截统计）、LangFuseTracingService（有界阻塞队列 2048 缓冲 + 后台守护 Worker 线程消除主线程 HTTP 阻塞与 Thread.sleep 吞吐暴跌），契约测试 4/4 全绿，全量回归 726/726 全绿！ |
| 2026-09-14 | **Phase 16 Delivered**：跨异构存储（PostgreSQL + PGVector + Neo4j）一致性灾备恢复工程、HNSW 三阶段解耦构建（恢复吞吐提升4~8倍并杜绝OOM）、`kmc_knowledge_recall_log` 范围分区 DDL 与冷热归档模板、线上真实 Query 四维漏斗难例挖掘服务（RealQueryMiningService）与无感结构保留加盐脱敏流水线（QuerySanitizer，柯西-施瓦茨扰动保真界限）、不可变基准回放集（rag-real-queries-v1.jsonl）与 EvalRegressionGate 双轨绝对红线门禁，专属契约测试 5/5 全绿，全量回归 731/731 全绿！ |
| 2026-09-14 | **Phase 17 Delivered**：MultiKbRetrievalCoordinator 跨库并发编排与 2500ms 软超时 Fail-Open 降级、CrossKbScoreCalibrator 跨库 RRF 融合精排与 20KB 统一预算硬截断、PermissionFilter 彻底修复管理员返回 null 漏洞与强类型三元交集过滤、EnhancedSemanticCacheService 密码学权限哈希防侧信道，专属契约测试 5/5 全绿，全量回归 736/736 全绿！ |
| 2026-09-14 | **Phase 18 Delivered**：Rust `vecsim-jni` SIMD（AVX2/NEON 8路展开 + DirectByteBuffer 零拷贝，单机点积吞吐超千万次/秒，误差界 $\le 1.16 \times 10^{-5}$）、Rust `tantivy-server`（Axum REST + JiebaTokenizer 中文分词 + IndexWriterActor 双阈值节流提交 + 租户硬隔离）、Java 端 `TantivyClient` 250ms 软超时 Fail-Open 与探活轻量缓存、`KeywordRetriever` 双轨容灾与 `(score DESC, segmentId ASC)` 二级键防抖稳定排序，专属门禁测试 5/5 全绿，全量回归 741/741 全绿！ |
| 2026-09-14 | **Phase 19 Delivered**：严格遵循 `.shared/ui-ux-pro-max` 规范，实现全业务模块 Monochromatic Glassmorphism 单色钛金毛玻璃、深浅色模式双通道闭环、4 级材质层级规范；构建卡片/表格 1:1 几何孪生骨架屏组件 `GlassSkeleton`、底部悬浮批量操作栏 `FloatingActionBar`、单色极简空状态 `GlassEmpty` 与 300ms 防抖搜索 `useDebounceSearch`；重构知识库、文档管理、分段切片、智能体编排、Bot 列表与详情、模型市场、可观测性监控等 10 大前台模块，消除写死黑白文本，前端全量生产构建 100% 成功（0 错误通过）！ |
| 2026-09-14 | **Phase 20 Delivered**：生产级全栈容器编排全面现代化（彻底淘汰过时的 MySQL 5.7 与 Weaviate，统一部署官方 `pgvector/pgvector:pg16`、`neo4j:5.26`、`redis:7-alpine`、`tantivy-server`，Spring Boot 3 Java 21 容器内存感知 `-XX:MaxRAMPercentage=75.0` 与 `service_healthy` 级联探针，Nginx 显式配置 SSE 流式打字机彻底关闭缓冲 `proxy_buffering off` 杜绝卡顿）；自动化异构灾备实战演练脚本（`verify_dr_drill.sh`）实现探针写入、异构原子快照、灾难销毁、解耦自愈恢复与 1536 维向量检索对账（余弦距离 < 1e-4）100% 精度闭环；全链路高并发压测套件（`stress_test_e2e.py`）达标通过（错误率 0.00%，P95 < 800ms）；后端 44 模块编译与门禁测试全部 SUCCESS，前端 Vite 生产构建 33.30s 极速成功（0 错误通过）！**全项目 Phase 01 ~ Phase 20 长链路全要素深度优化圆满大功告成！** |
| 2026-09-14 | **Phase 21 Delivered**：动态自适应 Agentic 记忆图谱与时序衰减遗忘机制全面交付：`DynamicEbbinghausDecay`（半衰期单调递增、重要度极值调制与渐进不遗忘性）、`UserMemoryGraphService`（Neo4j 用户偏好实体图、度数截断防拓扑爆炸、3s 超时熔断降级）、`LongTermMemory` 增强重构（多目标 Pareto 重排、温度 Sigmoid 标定、SHA-256 签名幂等查重、唤醒强化递推）、`SleepTimeMemoryAgent`（线程池调度、双阈值 80%/85% 内存水位防护、Redis 会话分布式写锁与安全 LTRIM 增量裁剪，并发前台消息 0 丢失）；专项测试 11/11 全绿，全量防退化回归测试 750/750 全绿（0 失败 0 错误），前端 Vite 生产构建顺利通过！ |
| 2026-09-14 | **Phase 22 Delivered**：多智能体动态分工协作、蜂群通信协议与分层任务意图分解全面交付：基于 HTN 与 Kahn 算法的 DAG 阶段分层调度（`TopologicalPhasedDispatcher`）、扩展 FIPA-CNP 合同网竞标与自适应兜底引擎（`ContractNetDispatcher`）、CAS 版本化响应式共享黑板总线（`SharedBlackboard`）、蜂群调用深度与语义振荡死锁熔断器（`SwarmLoopGuard`）；专属契约测试 12/12 全绿，全量防退化回归测试 762/762 全绿（0 失败 0 错误），前端 Vite 生产构建 31.67s 极速通过！ |
| 2026-09-14 | **Phase 23 Delivered**：反应式工作流编排引擎、非阻塞人机协同审批 (HITL) 与 SAGA 事务状态补偿自愈体系全面交付：彻底废除 `approvalFuture.get(24, TimeUnit.HOURS)` 物理线程阻塞，实现基于 Delimited Continuation 的 0 线程快照挂起恢复；基于 WF-Nets 健全性与剪枝标记代数 $\bot$ 实现条件分支动态递归剪枝（`computePrunedNodes`）与汇聚网关自适应过滤（`isBranchSkipped`），彻底杜绝 AND-Join 永久死锁；基于转置依赖图 $G^R$ 拓扑序（LIFO）与指数退避重试实现分布式 SAGA 逆向事务状态补偿引擎（`SagaCompensationEngine`）；提供类型安全上下文访问器（`TypeSafeContextAccessor`）与 DB CAS 乐观锁防重审批流转（`ApprovalController`）；专属契约测试 10/10 全绿，Hermes 模块单测 45/45 全绿，全量防退化回归测试 772/772 全绿（0 失败 0 错误），前端 Vite 生产构建 31.50s 顺利通过！ |
