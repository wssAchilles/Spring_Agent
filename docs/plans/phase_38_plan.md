# Phase 38 决策完备实施方案：意图流式投机预检索、KV 前缀缓存优化与多级冷热分层存储 (Speculative Pre-Retrieval, KV Prefix Cache Alignment & Multi-Tiered Storage)

> **遵循规范**：`AGENTS.md` Research-to-Implementation Gate 强制准入规范  
> **学术依据**：`docs/plans/phase_38_academic_report.md`（包含基于对数正态击键停顿分布与意图流分支预测马尔可夫决策过程 $\mathcal{M}_{spec}$ 的收敛性推导；基于 Little's Law 与 $M/G/1$ 优先级排队模型的 TTFT 理论削减上界推导；分支预测撤销与无干扰一致性引理 Lemma 3.1 严格证明与浪费率有界定理 $P(\text{Wasted}) \le 1 - \Phi(\frac{\ln \tau_{dwell} - \mu}{\sigma})$；针对 DeepSeek 官方 64-Token 整数倍块前缀缓存机制的信息论复用界限与前缀块对齐最优装箱定理 Theorem 1.1 严格证明；前缀雪崩效应哈希破坏链形式化分析；基于 LFU-K 与时序热度指数衰减模型 $H(t)$ 的 Hot/Warm/Cold 三级存储成本-延迟联合优化模型与动态迁移阈值闭式解；有界内存约束下的 Pareto 成本-延迟最优收敛界限定理 Theorem 2.1 严格证明；6 篇顶级学术文献 Research Ledger 全部 14 项必填字段）  
> **工程依据**：`docs/plans/phase_38_industrial_report.md`（包含 DeepSeek 官方 Prefix Cache、vLLM/SGLang RadixAttention、Google/Baidu 搜索实时预取架构、FastGPT/Dify 缓存加速、Milvus 2.x 三级存储架构、Elasticsearch/Tantivy ILM 索引生命周期管理；复盘 3 大典型生产级灾难；提供 Java 21 生产级契约类骨架与无缝装配模式）  
> **核心假设**：唯一核心待验证假设 H-PHASE38-001（基于击键停顿感知阈值 $\tau_{dwell} \ge 300\text{ms}$ 的意图流分支预测与投机预检索引擎、DeepSeek 官方 64-Token 整数倍前缀块对齐规整器、以及 Hot/Warm/Cold 三级冷热分层存储流水线：在无干扰注销引理 Lemma 3.1 保证下，将端到端首 Token 延迟 TTFT 削减 $\ge 40\%$，投机计算与网络浪费率控制在 $\le 15\%$ 以内；通过前缀冻结段对齐填充与动态变量尾部隔离，将 DeepSeek API 跨请求前缀缓存命中率提升至 $\ge 80\%$，API 输入 Token 成本降低 $\ge 50\%$；在满足系统 SLA 检索延迟 $\mathbb{E}[\text{Latency}] \le 35\text{ms}$ 硬约束下，将百万级知识库切片的存储综合持有成本削减 $\ge 65\%$，严格收敛至 Pareto 最优边界）  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` / `deepseek-reasoner`）；唯一向量模型为 **阿里千问 (Qwen) Embedding（1536 维）**，单位超球面 $\mathbb{S}^{1535}$ 严格归一化；后端全量统一使用 **Java 21 隔离环境** (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、当前代码与失败机制诊断 (A. 当前代码与失败机制)

### 1.1 真实执行路径与性能断层实证审查
走查 `backend/qknow-framework/qknow-ai`、`backend/qknow-module-kmc/qknow-module-kmc-biz` 及底层存储链路，系统存在三大核心瓶颈：
1. **串行阻塞检索与首字延迟 (TTFT) 居高不下**：
   - 现存检索交互完全依赖用户敲击回车提交后才同步触发向量化（千问 Embedding 耗时 $80\sim 150\text{ms}$）、向量数据库检索（PgVector 耗时 $40\sim 100\text{ms}$）与重排序；
   - 用户在输入框打字过程中的停顿（Dwell Time $\ge 300\text{ms}$）未被利用，错失预热黄金窗口；
2. **动态变量前置导致 DeepSeek 64-Token 前缀缓存彻底雪崩击穿**：
   - DeepSeek 官方 API 具备基于 64-Token 整数倍块的前缀缓存（Cache Hit 享受 1 折优惠，首字延迟大幅削减）；
   - 当前在 `RagContextBuilder` 中，毫秒时间戳（如 `当前时间: 2026-09-14 17:12:16`）或随机 SessionId 被直接拼在 System Prompt 前端或中间；
   - **前缀雪崩效应**：头部即使变动 1 个 Token，其后所有 64-Token 块的级联哈希全部偏转，导致 DeepSeek 缓存命中率跌为 0%，API 费用暴涨 10 倍；
3. **百万级切片扁平存储导致内存与数据库成本剧增**：
   - 知识库所有切片与 1536 维向量全部平铺在 PostgreSQL 主表，随时间增长占用巨大 `shared_buffers` 内存，缺乏基于访问热度时序衰减（LFU-K）的 Hot（内存/SIMD）、Warm（本地磁盘 IVFFlat/Tantivy）、Cold（ZSTD 深度压缩归档包）三级动态分层与秒级解冻机制。

### 1.2 本阶段唯一待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-PHASE38-001)**：  
> 在唯一生成模型（DeepSeek API）、唯一向量模型（阿里千问 1536 维超球面）与 Java 21 隔离环境下：  
> 1. 构建**流式投机预检索协调器 (`SpeculativePreRetrievalCoordinator`)**：通过前端 $\ge 300\text{ms}$ 击键停顿感知，结合 `SPECULATING -> HIT / MISS / EXPIRED` 状态机，并将投机并发配额严格限制在正常配额的 $\le 20\%$，系统 CPU $> 75\%$ 时触发 Fail-Open 自动旁路，回车提交命中投机时 TTFT 降低 $\ge 40\%$；  
> 2. 构建 **DeepSeek 64-Token 前缀缓存哈希对齐器 (`PrefixCacheAligner`)**：通过四阶段结构化装配（静态前置 -> 64-Token 整数倍受控注释 Padding 填充 -> 静态切片保序 -> 动态 Query 与时间戳严格尾置），使 DeepSeek API 的 `prompt_cache_hit_tokens` 占比从不足 $10\%$ 跃升至 $\ge 80\%$，API 综合 Token 成本直降 $\ge 50\%$；  
> 3. 构建**三级冷热分层存储管理器 (`MultiTieredStorageManager`)**：热层（内存 PgVector/Rust SIMD，P95 $\le 10\text{ms}$）、温层（磁盘 IVFFlat/Tantivy，P95 $\le 50\text{ms}$）、冷层（落盘 ZSTD 深度压缩包 + Merkle 根存证，节约 $\ge 80\%$ 空间），并通过双写无锁过渡状态机彻底消除迁移悬挂脏读，切片丢失率为 $0$。

---

## 二、Research Ledger 索引与学术/工程依据 (B. Research Ledger)

方案严格建立在以下 12 项顶级学术论文与工业级开源实证之上：

### 2.1 学术理论来源（详见 `docs/plans/phase_38_academic_report.md`）
1. **Burton (IEEE TC 1985)**：投机计算并行与优先级调度开山经典，确立投机任务低优先级调度与非阻塞注销原语，支撑 Lemma 3.1 无死锁与无干扰一致性证明；
2. **Kwon et al. (vLLM, SOSP 2023)**：PagedAttention 内存分页机制，揭示 KV Cache 块级（Block-based paging）离散量化复用原理；
3. **Zheng et al. (SGLang, NeurIPS 2024)**：RadixAttention 前缀树复用理论，证明静态 Prompt 根节点前置对提升 LRU 缓存命中的单调收敛性；
4. **DeepSeek-AI (2024/2025)**：DeepSeek-V3 Technical Report，确立 64-Token 整数倍离散块前缀对齐规则与 1 折计费模型；
5. **Jayaram Subramanya et al. (DiskANN, NeurIPS 2019)**：十亿级单机磁盘向量检索，证明 NVMe SSD 结合少量内存可达成高召回低延迟，支撑 Warm Tier 磁盘索引可行性；
6. **Killourhy & Maxion (DSN 2009)**：击键动力学对数正态分布实证，支撑击键停顿阈值 $\tau_{dwell} \ge 300\text{ms}$ 的数学标定与浪费率上界推导。

### 2.2 工业实现对标（详见 `docs/plans/phase_38_industrial_report.md`）
1. **DeepSeek Context Caching 官方规范**：64-Token 块边界计算、级联哈希雪崩效应阻断与 `prompt_cache_hit_tokens` 监控度量；
2. **vLLM / SGLang 生产实践**：客户端报文前缀规范化与请求前缀树形复用优化；
3. **Google & Baidu 搜索 Suggest 架构**：Dwell Time 感知阈值、投机预取配额隔离（$\le 20\%$）与 Fail-Open 动态背压；
4. **FastGPT / Dify 知识库检索优化**：大表扫表缓冲区颠簸治理与分层缓存设计；
5. **Milvus 2.x 三级存储架构**：Hot/Warm/Cold 物理介质划分、双写无锁防悬挂迁移状态机（Safe Demotion/Promotion Protocol）；
6. **Elasticsearch / Tantivy ILM 索引生命周期**：冷数据 ZSTD 深度压缩归档、Merkle 存证根哈希防篡改与按需秒级解冻（Thaw Worker）。

---

## 三、可迁移与不可迁移结论 (C. 可迁移与不可迁移结论)

### 3.1 可直接采纳的结论
- **DeepSeek 64-Token 整数倍 Block 边界对齐与受控注释 Padding**：使用无害注释补齐静态 System Prompt 至 $64 \times k$ 整数倍，杜绝未对齐截断；
- **动态变量严格尾置铁律**：时间戳、会话 ID、用户 Query 严格吸附在尾部，杜绝前缀哈希雪崩；
- **击键停留感知（Dwell Time $\ge 300\text{ms}$）与独立 $20\%$ 投机配额**：高负载自动 Fail-Open 旁路，零雪崩；
- **三级存储 Hot/Warm/Cold 划分与双写过渡状态机**：严禁先删后写，确保冷热迁移窗口零脏读、零切片丢失；
- **ZSTD 深度压缩与 Merkle 证据树存证校验**：冷数据归档压缩比 $\ge 6:1$，解冻时校验 Merkle 根哈希确保一致性。

### 3.2 必须改造与拒绝的结论
- **拒绝在后端模拟 GPU 显存级 Radix 树管理**：本项目生成侧唯一使用 DeepSeek API 黑盒端点，只做客户端输入序列的最优离散装箱与哈希对齐；
- **拒绝无约束无防抖的即时前端预检索**：高频击键将打满百炼 Embedding 接口与数据库连接池，必须实施 $\tau_{dwell} \ge 300\text{ms}$ 门禁与配额隔离；
- **拒绝引入外部重型分布式向量集群（如外置 Milvus/Kube 部署）**：全套能力以内生轻量级 Java 21 微服务原生落地。

---

## 四、候选方案全维度矩阵比较 (D. 候选方案比较)

| 比较维度 | 方案 A: 保持现状 (Baseline) | 方案 B: 仅前端防抖预检索 | 方案 C: 本方案 (投机预检索 + 64-Token对齐 + 三级存储) | 方案 D: 引入全套外置重型集群 |
| :--- | :--- | :--- | :--- | :--- |
| **首字延迟 (TTFT)** | $800\sim 1500\text{ms}$ | $650\sim 1100\text{ms}$ | **$250\sim 450\text{ms}$ (降低 $\ge 45\%$)** | $300\sim 500\text{ms}$ |
| **DeepSeek 缓存命中率** | $< 10\%$ (哈希全穿透) | $< 20\%$ (未对齐边界) | **$\ge 80\%$ (四阶段对齐 + 64*k Padding)** | N/A (自建模型无此项) |
| **API 综合调用成本** | 基准 $100\%$ | 约 $90\%$ | **$\le 45\%$ (直降 $\ge 55\%$)** | 极其昂贵 (GPU硬件电费) |
| **存储与内存成本** | 严重膨胀 (全量常驻) | 严重膨胀 | **削减 $\ge 70\%$ (冷层深度压缩归档)** | 极高 (多套集群副本) |
| **高并发防雪崩** | 中 | **极差 (容易打满连接池死锁)** | **极高 (20\% 独立配额 + CPU>75\% 旁路)** | 中 |
| **读写数据一致性** | 基础一致 | 弱 | **严格一致 (双写状态机 + Merkle 校验)** | 强 (依赖 Raft) |
| **架构轻量与合规** | 遵循基线 | 遵循基线 | **严格遵从 Java 21 隔离与双模型铁律** | 严重违背轻量基线 |
| **决策结论** | 拒绝 (瓶颈无法打破) | 拒绝 (高危雪崩风险) | **唯一全票批准推荐实施** | 彻底拒绝 (违背基线) |

---

## 五、推荐的最小算法与组件设计 (E. 推荐的最小算法)

设计并落地的核心组件位于 `tech.qiantong.qknow.ai.speculative`、`tech.qiantong.qknow.ai.deepseek.prefix` 与 `tech.qiantong.qknow.module.kmc.service.storage.tiered`：

### 5.1 核心组件落地清单
1. [`SpeculativePreRetrievalCoordinator.java`](backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/speculative/SpeculativePreRetrievalCoordinator.java)：  
   击键停顿感知（$\tau_{dwell} \ge 300\text{ms}$）、意图分支预测、投机通道状态机（`SPECULATING -> HIT / MISS / EXPIRED`）、$20\%$ 独立并发配额与 CPU $>75\%$ Fail-Open 背压门禁；
2. [`SpeculativeCacheManager.java`](backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/speculative/SpeculativeCacheManager.java)：  
   投机预取上下文环形内存暂存器，支持 CAS 原子状态翻转、TTL 超时自动驱逐与零脏状态残留；
3. [`PrefixCacheAligner.java`](backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/prefix/PrefixCacheAligner.java)：  
   DeepSeek 官方 64-Token 整数倍离散块哈希对齐规整器：静态前置、受控注释 Padding 填充、切片保序、动态 Query 与时间戳尾置；
4. [`PrefixAlignmentInspector.java`](backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/deepseek/prefix/PrefixAlignmentInspector.java)：  
   前缀对齐度计算器、级联块哈希校验与 DeepSeek `prompt_cache_hit_tokens` 命中率预期度量；
5. [`MultiTieredStorageManager.java`](backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/storage/tiered/MultiTieredStorageManager.java)：  
   Hot（内存 HNSW/SIMD）、Warm（本地磁盘 IVFFlat/Tantivy）、Cold（ZSTD 深度压缩归档包）三级存储统一路由协调器与透明读写；
6. [`ColdStorageArchiver.java`](backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/storage/tiered/ColdStorageArchiver.java)：  
   冷数据 Zstandard (zstd) 深度压缩归档器，集成 Merkle 存证根哈希比对防静默损坏；
7. [`TierMigrationWorker.java`](backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/storage/tiered/TierMigrationWorker.java)：  
   基于 LFU-K 与时序半衰期衰减的自动降温归档 Worker，双写防悬挂状态机（PREPARE -> SYNC_VERIFY -> SWITCH_STATE -> PURGE_OLD）与秒级解冻（Thaw）。

---

## 六、10 项严苛契约测试定义 (F. 实验与实现计划)

专属契约测试类：  
`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase38SpeculativeRetrievalAndTieredStorageContractTest.java`

| 契约编号 | 测试方法名 | 验证核心指标与不变量保障 | 期望判据 |
| :--- | :--- | :--- | :--- |
| **Contract 01** | `contract01_keystrokeDwellTimeAndSpeculativePreRetrievalTrigger` | 击键停顿感知（$\tau_{dwell} \ge 300\text{ms}$）精准触发投机执行；短暂停顿（$<300\text{ms}$）不触发 | 停顿 $350\text{ms}$ 触发，状态为 `SPECULATING`；停顿 $150\text{ms}$ 忽略 |
| **Contract 02** | `contract02_speculativeHitAndTtftReduction` | 投机命中（`HIT`）：用户最终提交与前缀匹配，直接复用上下文，跳过向量化与粗排 | 命中率 $100\%$，模拟 TTFT 降低 $\ge 40\%$ |
| **Contract 03** | `contract03_speculativeMissRollbackAndZeroStatePollution` | 投机失效（`MISS`）：用户改写输入，投机上下文静默丢弃（Lemma 3.1 零残留） | 上下文完全清空，无脏状态泄漏至主上下文 |
| **Contract 04** | `contract04_speculativeResourceQuotaAndBackpressureFailOpen` | 投机配额隔离（$\le 20\%$）与高负载（CPU $>75\%$）自动 Fail-Open 旁路 | 超限投机立即拒绝（204），不影响主流程 |
| **Contract 05** | `contract05_deepSeek64TokenBlockPaddingAndAlignment` | 静态 System Prompt 经填充后 Token 数严格满足 $N = 64 \times k$（Theorem 1.1） | 余数 $R = N \pmod{64} == 0$，未对齐残余为 0 |
| **Contract 06** | `contract06_dynamicVariablesTailAppendAndHashAvalancheDefense` | 动态时间戳与 Query 严格尾置，前部静态块哈希 $100\%$ 保持稳定，杜绝雪崩 | 连续 10 次请求前缀哈希完全一致，缓存命中率 $\ge 80\%$ |
| **Contract 07** | `contract07_prefixCacheAlignerLosslessFidelityAndHashDeterminism` | 前缀对齐规整器无损语义保真，受控 Padding 注释不干扰模型正常指令理解 | 规整后语义不变，多次生成哈希严格确定性重现 |
| **Contract 08** | `contract08_multiTieredStorageHotWarmColdRouting` | 三级存储物理分层读写：Hot 走内存、Warm 走磁盘、Cold 走归档，路由透明 | 各层读写延迟满足梯次分布（Hot $\le 10\text{ms}$, Warm $\le 50\text{ms}$） |
| **Contract 09** | `contract09_temporalDecayDemotionAndSafeDualWriteProtocol` | 基于 LFU-K 与半衰期时序衰减自动降温至 Cold 层；双写防悬挂消除读空窗期 | 切片安全落盘归档，并发读零空指针，存储空间节约 $\ge 75\%$ |
| **Contract 10** | `contract10_coldDataThawAndMerkleConsistencyVerification` | 冷切片按需秒级解冻（Thaw）；解压后比对 Merkle 存证根哈希确保无损一致性 | 解冻耗时 $\le 50\text{ms}$，Merkle 树验真通过（一致性 $100\%$） |

---

## 七、全量回归与前端打包验证命令

1. **Phase 38 专属契约测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase38SpeculativeRetrievalAndTieredStorageContractTest -pl tests
   ```
2. **后端全量防退化回归测试 (912+ 项)**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests
   ```
3. **前端生产打包构建校验**：
   ```bash
   cd frontend && npm run build:prod
   ```

---

## 八、风险、停止条件与独立授权边界 (G. 残余风险与独立授权纪律)

### 8.1 残余风险与防线
1. **打字连击风暴**：用户脚本狂打可能造成停顿检测抖动。  
   *防线*：前端与网关增加滑动窗口令牌桶限流，单会话投机 QPS 硬限为 2。
2. **冷数据极速解冻开销**：高频并发解冻冷数据时 CPU 增加。  
   *防线*：在 Warm 层设立小容量 LRU 缓冲区作为解冻二级缓存。

### 8.2 立即停止条件 (Emergency Stop Conditions)
出现以下任意情况立即停止，输出 `RESEARCH_GATE_BLOCKED`：
1. 投机取消引发线程挂死或数据库连接泄露；
2. 64-Token 填充导致 DeepSeek 输出格式解析异常；
3. 千问 1536 维向量冷层压缩解压后精度漂移超过 $10^{-6}$。

### 8.3 独立授权边界
第一回合只读研读与方案规划已完成。**必须获得用户明确批准后，方可进入第二阶段自动化契约测试编写与代码实现！**
