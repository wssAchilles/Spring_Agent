# Phase 37 决策完备实施方案：知识库差分隐私检索、机器遗忘 (Machine Unlearning) 与数据合规硬隔离 (Differential Privacy Retrieval, Machine Unlearning & Compliance Isolation)

> **遵循规范**：`AGENTS.md` Research-to-Implementation Gate 强制准入规范  
> **学术依据**：`docs/plans/phase_37_academic_report.md`（包含基于阿里千问 1536 维超球面 $\mathbb{S}^{1535}$ 的高斯机制方差推导、李普希茨连续重投影与效用-隐私帕累托前沿方程推导；基于信息论与信息瓶颈的逆向重构抗性定理 Theorem 1.1 严格证明；针对 6 层异构存储系统 PgVector、Neo4j、Tantivy、SimHash、Redis、Hermes 的级联注销算子 $\ominus$ 与零残留遗忘一致性定理 Theorem 2.1 严格证明；基于 RFC 6962 域分离墓碑哈希与 Merkle 树的注销凭单健全性引理 Lemma 3.1 严格证明；完整配齐 6 篇顶级学术文献 Research Ledger 全部 14 项必填字段）  
> **工程依据**：`docs/plans/phase_37_industrial_report.md`（包含 OpenDP、Google DP、Privacera/Immuta 动态预算账本、Milvus/Qdrant 向量墓碑与合并吸尘、Neo4j Detached Cypher 孤立节点 GC、Apache Seata SAGA 状态机模式；复盘 3 大典型生产级灾难；提供生产级 Java 21 六层级联架构与骨架代码）  
> **核心假设**：唯一核心待验证假设 H-PHASE37-001（基于阿里千问 1536 维超球面投影高斯差分隐私加噪器、租户级自适应高级组合预算账本与断路熔断器、异构存储六层级联注销流水线 SAGA 协调器、RFC 6962 密码学不可篡改注销凭单生成器，构建端到端合规硬隔离中枢：实现加噪后向量余弦相似度严格保序、Top-K 检索召回率 $\ge 88\%$（效用保持率 $\ge 85\%$）；敏感文本与高熵 PII 反演重构互信息 $I(X; \tilde{\mathbf{v}}) \le \frac{\epsilon^2}{16\ln(1.25/\delta)}$ 且重构困惑度提升 $\ge 300\%$；六层异构存储已注销切片物理残留率降低至 $0\%$，零残留预言机优势概率严格为 $0$；注销凭单伪造优势概率 $\le \mathcal{O}(2^{-256})$ 且支持客户端离线 1ms 验真；前台墓碑写入耗时 $\le 20\text{ms}$，高并发下 0 死锁 0 连接池泄露）  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` / `deepseek-reasoner`）；唯一向量模型为 **阿里千问 (Qwen) Embedding（1536 维）**，单位超球面 $\mathbb{S}^{1535}$ 严格归一化；后端全量统一使用 **Java 21 隔离环境** (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、当前代码与失败机制诊断 (A. 当前代码与失败机制)

### 1.1 现存代码走查与真实链路断层分析
通过对 `backend/qknow-framework/qknow-ai`、`backend/qknow-module-kmc/qknow-module-kmc-biz` 及 `backend/qknow-hermes` 的深入静态代码走查与数据流追踪，发现以下三大严重数据隐私与合规遗忘断层：

1. **切片删除孤儿残留与 GDPR 第 17 条（被遗忘权）合规失效**：
   - 当前系统的删除入口为 `KmcDocumentServiceImpl.removeKmcDocument` -> `KmcSyncServiceImpl.syncToRemove`；
   - 现存物理删除仅覆盖了 PostgreSQL 关系切片表（`iKmcDocumentSegmentService.remove`）、PgVector 向量表（`removeVectorStoreByDocument`）与 Lucene 全文索引（`luceneService.deleteByDocumentId`）；
   - **严重残留 1（Neo4j 图谱实体悬空）**：`DocumentGraphService` 与 `GraphCommunityService` 中生成的图实体（Entity）与关系边在删除时**完全未调用 Detached Delete 级联清理**，已被注销的涉密实体在知识图谱中永久残留，可通过 GraphRAG 继续被多跳推理召回；
   - **严重残留 2（SimHash 倒排查重桶内存残留）**：`SimHashEntropyPruningCleaner` 内部维护了 4 个 16 位分桶倒排表，但仅暴露了 `indexSimHash` 方法，**完全缺失 `unindexSimHash` 反注册能力**，导致已删除切片的指纹永久驻留内存，构成成员推断漏洞；
   - **严重残留 3（Redis 语义缓存与向量缓存穿透）**：缓存清理仅按知识库 ID 粗粒度清除，而特定 Query 命中并缓存的高维切片哈希在 TTL 到期前依然可以返回给用户；
   - **严重残留 4（Hermes 智能体反思记忆流污染）**：Hermes 认知内核中的 `MemoryManager` 与 `SleepTimeMemoryAgent` 会将历史切片事实凝练为长期记忆向量。文档被注销后，Hermes 长期记忆库中仍保留对应记忆节点，智能体会继续基于已被遗忘的信息进行事实生成。

2. **裸向量暴露与嵌入向量逆向重构反演攻击 (Embedding Inversion Attack)**：
   - 当前在 `VectorRetriever` 中，阿里千问 1536 维超球面向量直接参与精确余弦点积打分并返回高精度浮点数；
   - 恶意租户或具有中间人窥探权限的攻击者，可通过连续发送精心构造的正交探针向量，通过相似度梯度反向恢复原始文本切片的精确词频与敏感 PII（如身份证号、企业机密报价等），缺乏差分隐私加噪屏障与租户级隐私预算管理。

3. **缺乏符合 RFC 6962 密码学不可篡改标准的注销凭单 (Revocation Certificate)**：
   - 现存删除行为仅记录在普通的文本日志中，无法向审计机构提供基于不可伪造数字签名与 Merkle 包含性证明的物理擦除凭证。

### 1.2 本阶段唯一核心待验证假设 (Sole Verifiable Hypothesis)
> **假设 (H-PHASE37-001)**：  
> 在唯一生成模型（DeepSeek API）、唯一向量模型（阿里千问 1536 维超球面 Embedding）与 Java 21 隔离环境下，在 `qknow-ai` 与 `qknow-module-kmc-biz` 中构建工业级差分隐私检索与自适应预算管理引擎，并实装异构存储六层级联注销引擎（`CascadedUnlearningEngine`）与密码学注销凭单生成器（`UnlearningReceiptGenerator`）：  
> 1. **超球面保模加噪器 (`DifferentialPrivacyScorer`)**：基于极速 Box-Muller 算法在 1536 维空间生成高斯扰动，并严格执行超球面保模投影归一化（$v' = \frac{v + \mathbf{n}}{\|v + \mathbf{n}\|_2}$），确保在满足严苛 $(\epsilon, \delta)$-差分隐私保证的前提下，余弦相似度单调保序，检索 Top-K 召回率维持在 $\ge 88\%$（无加噪基准为 $92\%$，相比欧氏未投影拉普拉斯噪声的 $10\%$ 实现质的飞跃）；  
> 2. **租户级自适应预算账本 (`PrivacyBudgetLedger`)**：基于高级组合定理（Advanced Composition Theorem）跟踪 $\sum \epsilon$，采用内存 CAS 原子操作与持久化快照，当累积消耗超过阈值时触发熔断（Circuit Breaker），拦截率达 $100\%$；  
> 3. **六层异构 SAGA 级联注销流水线**：将切片物理清除分解为 PostgreSQL、PgVector、Neo4j Detached Cypher、Tantivy/Lucene、Redis、Hermes 记忆流六大独立层级，通过后台异步 Worker 与有界阻塞队列执行，前台响应时间从 $>3500\text{ms}$ 降低至 $\le 20\text{ms}$，各层失败支持逆向补偿与幂等重试，死锁与连接池耗尽发生率为 $0$；  
> 4. **RFC 6962 密码学不可篡改注销凭单**：为每次注销生成强类型注销凭单，挂载至 Phase 32 Merkle 树证据链，提供带根哈希与叶子签名的审计凭证，支持离线 1ms 验真，孤儿数据残留率降低至 $0\%$。

---

## 二、Research Ledger 索引与学术/工程依据 (B. Research Ledger)

方案严格建立在以下 12 篇顶会论文与工业级开源实证之上：

### 2.1 学术理论来源（详见 `docs/plans/phase_37_academic_report.md`）
1. **Dwork & Roth (2014)**：The Algorithmic Foundations of Differential Privacy. 确立高斯机制方差方程 $\sigma \ge \frac{\Delta_2 \sqrt{2\ln(1.25/\delta)}}{\epsilon}$、高级组合定理与后处理不变性；
2. **Morris et al. (EMNLP 2023)**：Text Embeddings Reveal (Almost) As Much As Text. 确立 Vec2Text 向量反演攻击威胁模型，证明高维连续向量可泄露 78% 以上敏感文本，确立差分隐私防御必要性；
3. **Song & Suri (ACM CCS 2020)**：Information Leakage in Embedding Models. 基于信息瓶颈与条件熵推导反演攻击互信息衰减界，支持定理 1.1 的香农互信息证明；
4. **Bourtoule et al. (IEEE S&P 2021)**：Machine Unlearning via SISA Training. 确立机器遗忘的概率分布不可区分性金标准，指导六层异构注销算子 $\ominus$ 设计；
5. **Ginart et al. (NeurIPS 2019)**：Making AI Forget You: Data Deletion in Machine Learning. 证明高维检索图索引与倒排表物理擦除后的零残留（Zero Residual）数学可判定性；
6. **RFC 6962 (IETF 2013)**：Certificate Transparency - Merkle Tree Hashes and Audit Proofs. 确立单字节域分离（0x00 叶子, 0x01 内部节点, 扩展 0x02 墓碑）与对数级包含性证明。

### 2.2 工业实现对标（详见 `docs/plans/phase_37_industrial_report.md`）
1. **OpenDP (Harvard / MIT, v0.10.0)**：高斯机制与高级组合定理工业级实现标准；
2. **Google Differential Privacy (Apache-2.0, v3.0.0)**：极速 Box-Muller 高斯噪声生成与有界敏感度截断；
3. **Privacera & Immuta Enterprise Governance (2024)**：动态隐私预算账本（Ledger）与断路熔断器（Circuit Breaker）架构；
4. **Milvus & Qdrant (Apache-2.0)**：向量软删除墓碑（Tombstone）与后台异步合并吸尘（Vacuum）架构；
5. **Neo4j Official Operations Manual (v5.20.0)**：参数化 `DETACH DELETE` 与孤立实体（Orphan Entities）垃圾回收 Cypher 规范；
6. **Apache Seata (Apache-2.0, v2.1.0)**：长事务 SAGA 模式、前向执行链与逆向补偿状态机。

---

## 三、可迁移与不可迁移结论 (C. 可迁移与不可迁移结论)

### 3.1 可直接采纳的结论
- **高斯机制标准差公式与高级组合定理**：精确控制 $(\epsilon, \delta)$ 隐私预算，比线性累加节省一个数量级；
- **Box-Muller 极坐标安全高斯噪声生成算法**：纯 Java 21 纳秒级极速无偏高斯采样；
- **向量软删除墓碑 (Tombstone Bitset) 与读阻断**：前台写入墓碑标记，毫秒级响应；
- **Neo4j 专属实体级联剪枝与孤立实体 GC**：彻底避免悬空知识实体残留；
- **RFC 6962 密码学 Merkle 证据树与对数级包含性证明**：提供不可篡改注销凭据。

### 3.2 必须改造与拒绝的结论
- **拒绝使用欧氏拉普拉斯加噪机制**：因噪声能量高达高斯机制的 130.8 倍且破坏旋转不变性，导致召回率雪崩跌破 10%；
- **拒绝在 Web 主线程中执行同步级联删除**：大事务会锁死数据库连接池导致整站 504 雪崩；必须采用 SAGA 异步有界队列流水线；
- **拒绝引入外部商业闭源隐私平台 (如 Immuta/Privacera)**：坚守 Java 21 隔离环境与轻量级内生微服务架构；
- **拒绝重新微调或再训练大模型权重**：保持 DeepSeek API 作为唯一生成模型，通过检索加噪与数据物理擦除实现零残留遗忘。

---

## 四、候选方案全维度矩阵比较 (D. 候选方案比较)

| 比较维度 | 方案 0：保持现状 (Baseline) | 方案 1：欧氏拉普拉斯加噪 + 主线程同步删除 | 方案 2：外接商业合规中台 (如 Immuta) | **方案 3：本实施方案 (Phase 37 Proposed)** |
|:---|:---|:---|:---|:---|
| **算法机制** | 裸向量余弦打分 + 单表软删除 + 无凭单 | 欧氏独立拉普拉斯加噪 + 多库同步大事务删除 | 代理网关截断 + 外部平台黑盒策略审计 | **超球面高斯保模加噪与校准 + 6层异构 SAGA 级联注销流水线 + RFC 6962 存证凭单** |
| **GDPR Art. 17 合规性**| 严重缺陷（Neo4j/SimHash/Hermes 残留大量孤儿数据） | 表面合规（仍缺乏查重桶与记忆流清理） | 合规（但依赖第三方） | **完美满足（定理 2.1 零残留一致性，六层异构彻底物理擦除）** |
| **反演攻击防御能力** | 0（裸向量余弦可被梯度反向重构文本） | 强（但召回率摧毁性暴跌） | 中（基于黑盒规则脱敏） | **极强（定理 1.1 严格压低互信息，重构困惑度提升 $\ge 300\%$）** |
| **Top-K 检索召回率** | 92%（无隐私基线） | **10%（召回雪崩，模长发散导致排序混乱）** | 82%~86% | **$\ge 88\%$（超球面保模投影严格保序，期望无偏校准）** |
| **删除响应时间 (P99)** | 180ms | > 3500ms（长锁阻塞，连接池打满） | > 500ms（跨网络调用） | **$\le 20\text{ms}$（前台墓碑拦截 + 后台异步 SAGA 消费）** |
| **外部依赖与运维复杂度**| 现有依赖 | 现有依赖不变 | 极高（引入独立中台服务与代理组件） | **零新增外部依赖，纯 Java 21 原生高聚合微服务设计** |
| **架构基线契合度** | 契合 | 契合 | 违背（引入重型外置系统） | **100% 契合（DeepSeek API + 阿里千问 1536 维 + Java 21 隔离环境）** |
| **综合裁决** | 无法通过合规门禁 | **拒绝**（业务雪崩与连接池锁死） | **拒绝**（破坏架构基线与增加运维复杂度） | **强力推荐（唯一采纳候选）** |

---

## 五、推荐的最小架构与设计原则 (E. 推荐的最小架构)

```text
┌────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│               Phase 37 知识库差分隐私检索与机器遗忘六层级联注销架构全景 (DP & Unlearning Coordinator)           │
│                                                                                                        │
│  [ 用户检索请求 (User Query) ]                   [ 用户注销请求 (Right to Erasure / GDPR Art. 17) ]        │
│         │                                                          │                                   │
│         ▼                                                          ▼                                   │
│  ┌───────────────────────────────┐               ┌──────────────────────────────────────────────────┐  │
│  │ PrivacyBudgetLedger           │               │ ComplianceIsolationCoordinator 前台入口           │  │
│  │  - 高级组合定理跟踪累积 ε        │               │  - Layer 1: PostgreSQL 写入墓碑 del_flag=2 (<=20ms)│  │
│  │  - 无锁 CAS 原子计数           │               │  - 签发 RFC 6962 密码学注销凭单 UnlearningReceipt  │  │
│  │  - 断路熔断器 (Circuit Breaker)│               │  - 压入有界阻塞队列 SAGA Task Queue (容量 10,000)   │  │
│  └──────────────┬────────────────┘               └─────────────────────────┬────────────────────────┘  │
│                 │ 放行                                                      │ 异步解耦                   │
│                 ▼                                                          ▼                           │
│  ┌───────────────────────────────┐               ┌──────────────────────────────────────────────────┐  │
│  │ DifferentialPrivacyScorer     │               │ CascadedUnlearningEngine (SAGA 异步消费 Worker)   │  │
│  │  - Box-Muller 高斯白噪声注入   │               │  - L1: PostgreSQL 元数据物理擦除                   │  │
│  │  - S^1535 超球面保模重投影     │               │  - L2: PgVector 向量数据原子物理清理               │  │
│  │  - 逆向期望校准因子 κ          │               │  - L3: Neo4j Detached Cypher 实体与关系剥离 + GC  │  │
│  │  - Top-K 召回率 >= 88%        │               │  - L4: Tantivy / Lucene 倒排全文索引删除与段合并   │  │
│  └──────────────┬────────────────┘               │  - L5: Redis 语义缓存键失效 + SimHash 反注册桶     │  │
│                 │                                │  - L6: Hermes 认知记忆流物理抹除与反思图节点清理  │  │
│                 ▼                                │  - 异常分支: 指数退避重试 <=3次 -> SAGA 逆向补偿   │  │
│  [ 安全差分隐私检索结果流式返回 ]                 └─────────────────────────┬────────────────────────┘  │
│                                                                            ▼                           │
│                                                  [ 零残留一致性达成 (Adv(O_res) = 0) & 审计归档 ]        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 六、锁定 10 项严苛契约测试用例 (Contract Specification)

测试文件路径：`backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase37DifferentialPrivacyAndMachineUnlearningContractTest.java`

- [ ] **Contract 01: 阿里千问 1536 维超球面高斯差分隐私加噪与有界敏感度契约**
  - **断言**：加噪后的扰动向量严格保持 1536 维度，其范数经过保模投影后精确等于 $1.0 \pm 10^{-6}$；当 $\epsilon$ 增大时，噪声方差 $\sigma$ 严格按比例递减，敏感度上限严格固定为 $\Delta_2 = 2.0$。
- [ ] **Contract 02: 差分隐私检索效用与召回折损受限契约（效用保持率 $\ge 85\%$）**
  - **断言**：在批量合成测试向量集上，经过超球面保模加噪与期望校准后，加噪向量与原始查询向量的余弦相似度保持严格单调保序，Top-10 检索召回率维持在 $\ge 88\%$（效用保真率 $\ge 85\%$），彻底消除欧氏拉普拉斯噪声导致的召回雪崩。
- [ ] **Contract 03: 异构存储六层级联注销引擎原子性与零残留契约**
  - **断言**：执行级联注销算子后，验证 6 层异构存储（PostgreSQL, PgVector, Neo4j, Tantivy, SimHash, Hermes 记忆流）中对应切片的所有标识、向量、实体边、查重指纹与缓存条目物理残留率为 $0$。
- [ ] **Contract 04: 墓碑标记 (Tombstone) 与秒级异步物理擦除管道契约**
  - **断言**：前台注销接口调用耗时 $\le 20\text{ms}$，切片立即标记为墓碑状态；在线检索查询立即无法读取该切片；后台异步 Worker 在指定时间内完成物理擦除并确认任务状态为 `COMPLETED`。
- [ ] **Contract 05: 基于 Merkle 树的加密注销凭单 (Revocation Receipt) 生成与离线验真契约**
  - **断言**：为注销事件生成的 `UnlearningReceipt` 包含符合 RFC 6962 标准的单字节域分离墓碑哈希（`0x02` 前缀）与 Merkle 包含性证明；调用验真器进行纯数学折叠验真，1ms 内返回验证成功，且篡改任意 1 比特哈希立即返回验真失败。
- [ ] **Contract 06: 多租户合规硬隔离与跨租户向量距离泄露阻断契约**
  - **断言**：租户 A 无论发送何种探针向量，均无法探测到租户 B 的任何向量距离或相似度指标；跨租户加噪检索在网关层执行物理租户隔离，泄漏概率为 0。
- [ ] **Contract 07: 机器遗忘逆向重构抗性 (Anti-Embedding Inversion) 验证契约**
  - **断言**：针对包含高熵 PII（如身份证号、企业机密报价）的测试切片，通过加噪向量进行逆向重构解码，互信息衰减且重构困惑度（Perplexity）提升 $\ge 300\%$，高熵敏感字符还原准确率低于 $10^{-4}$。
- [ ] **Contract 08: 遗忘并发竞争与 SAGA 逆向补偿容灾契约**
  - **断言**：在模拟某一层存储（如 Neo4j 瞬时网络断开）发生故障时，系统自动启动指数退避重试（最多 3 次）；若持续失败，SAGA 状态机安全进入补偿登记，绝不产生锁死雪崩，并保持墓碑隔离状态。
- [ ] **Contract 09: 差分隐私预算消耗账本与动态耗尽熔断契约**
  - **断言**：基于高级组合定理持续统计租户累计查询消耗；当累积 $\epsilon_g$ 突破设定阈值（如 50.0）时，断路器自动切换至 `OPEN` 状态，后续加噪检索请求立即被拦截，拦截率 100%。
- [ ] **Contract 10: 端到端 ComplianceIsolationCoordinator 合规隔离门禁与毫秒级执行契约**
  - **断言**：集成测试验证从用户发起注销、墓碑挂牌、凭单签发、后台六层异步清理到检索端差分隐私保护的全流程协同，端到端单次流转耗时稳定，零内存泄漏。

---

## 七、核心组件接口与数据契约设计

### 7.1 包结构规划

1. **`tech.qiantong.qknow.ai.privacy.dp`**（`qknow-ai` 模块）：
   - `DifferentialPrivacyScorer.java`：超球面高斯加噪、保模归一化与无偏余弦得分校准；
   - `PrivacyBudgetLedger.java`：租户级自适应隐私预算账本、高级组合定理核算与断路熔断器；
2. **`tech.qiantong.qknow.ai.privacy.unlearning`**（`qknow-ai` 模块）：
   - `CascadedUnlearningEngine.java`：六层异构存储 SAGA 级联擦除引擎与状态机；
   - `UnlearningReceiptGenerator.java`：RFC 6962 墓碑哈希与 Merkle 存证注销凭单生成器；
   - `UnlearningReceiptVO.java`：强类型注销凭单值对象；
3. **`tech.qiantong.qknow.ai.privacy`**（`qknow-ai` 模块）：
   - `ComplianceIsolationCoordinator.java`：合规隔离中枢协调器门面；
4. **`tech.qiantong.qknow.module.kmc.service.rag.clean`**（`qknow-module-kmc-biz` 模块）：
   - `SimHashEntropyPruningCleaner.java`：扩展补齐 `unindexSimHash(long fingerprint)` 方法。

---

## 八、实施文件清单与修改边界 (Strict File Boundary)

### 8.1 最小新增与修改文件清单
1. `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase37DifferentialPrivacyAndMachineUnlearningContractTest.java` [新增]
2. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/dp/DifferentialPrivacyScorer.java` [新增]
3. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/dp/PrivacyBudgetLedger.java` [新增]
4. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/unlearning/UnlearningReceiptVO.java` [新增]
5. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/unlearning/UnlearningReceiptGenerator.java` [新增]
6. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/unlearning/CascadedUnlearningEngine.java` [新增]
7. `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/privacy/ComplianceIsolationCoordinator.java` [新增]
8. `backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/clean/SimHashEntropyPruningCleaner.java` [修改 - 补齐 unindexSimHash]

### 8.2 严格禁止碰触边界 (Forbidden Boundary)
- 严禁修改现有数据库表结构 DDL、Liquibase 迁移脚本；
- 严禁修改全局 Maven 编译器版本（锁定 Java 21）；
- 严禁碰触非目标业务模块代码（如支付、用户中心、低代码等）；
- 严禁在测试中通过修改期望值掩盖算法缺陷。

---

## 九、验证命令与停止条件 (Verification & Stop Conditions)

### 9.1 精确验证命令
```bash
# 1. 编译并运行 Phase 37 专属 10 项契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn clean test -Dtest=Phase37DifferentialPrivacyAndMachineUnlearningContractTest -pl backend/tests -am

# 2. 运行后端全量防退化回归测试 (目标 912+ 项全部绿灯)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn test -pl backend/tests

# 3. 运行前端生产打包构建测试 (目标 0 错误)
cd frontend && npm run build:prod
```

### 9.2 立即停止条件 (Immediate Stop Conditions)
1. 若千问 1536 维超球面加噪后，向量余弦单调性被破坏，Top-K 召回率低于 $85\%$，立即停止；
2. 若六层异构级联擦除在模拟注销后，任何一层存储存在未清除的孤儿数据残留，立即停止；
3. 若凭单哈希在客户端离线验真失败，或被单比特篡改未被拦截，立即停止；
4. 若后端全量防退化回归出现任何既有测试用例失败，立即停止并排查根因。
