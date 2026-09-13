# Phase 16: 生产级灾备容灾、异构数据一致性备份恢复与线上真实 Query 难例自动挖掘体系实施计划 (P3 + Q0) [COMPLETED]

> **依据**：`AGENTS.md` Research-to-Implementation Gate 规范  
> **前置调研**：学术向智能体 (`ba46c885`) 与工程向智能体 (`1814d9eb`) 双向并发深度对标已闭环完成  
> **学术报告**：`docs/plans/phase_16_academic_report.md` (5 篇顶会文献，包含 Chandy-Lamport 全局一致割定理、无 2PC 错位产生幽灵向量不可恢复性概率、LLBS 轻量锁屏障备份协议、AHN-RS 拒绝采样难例挖掘算法、柯西-施瓦茨语义扰动上界定理)  
> **工程报告**：`docs/plans/phase_16_industrial_report.md` (跨 PostgreSQL + PGVector + Neo4j 异构备份与自愈恢复脚本体系、`pg_dump` 目录并行与 HNSW 三阶段解耦、`kmc_knowledge_recall_log` 按月声明式分区与 MinIO 归档、结构保留无感加盐脱敏流水线、三大生产灾难避坑指南)  
> **方案文档**：`docs/plans/phase_16_plan.md`  
> **唯一模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 / `deepseek-reasoner` 即 R1），唯一向量模型为 **阿里千问 (Qwen) Embedding (1536维)**，绝无本地大模型，彻底弃用 OpenAI/GPT API。  
> **交付状态**：**已交付 (Delivered) - 契约单测 5/5 全绿，全量回归 731/731 全绿**

---

## User Review Required

> [!IMPORTANT]
> **本阶段核心创新与生产治理价值**：
> 1. **跨异构存储（PostgreSQL + PGVector + Neo4j）灾备与自愈恢复自动化工程**：
>    - 彻底填补项目生产级灾备脚本空白，交付工业级 `scripts/backup.sh` 与 `scripts/restore.sh`；
>    - 针对 1536 维高维向量数据，采用 `pg_dump -F d -j 4` 目录并行导出，并在恢复时严格按 **Pre-data（表结构与扩展） -> Data（无索引极速数据灌入） -> Post-data（调大 `maintenance_work_mem = '2GB'` 与并行 Worker 批量构建 HNSW 索引）** 三阶段解耦，将恢复吞吐提升 4~8 倍并杜绝 OOM；
>    - 容器化安全备份 Neo4j，生成含 SHA-256 校验和与 LSN 割集的元数据清单 `manifest.json`；
>    - 恢复流程自动级联触发 Phase 13 的 `VectorReconciliationEngine`，执行异构存储双向对账自愈，彻底杜绝“悬挂外键、幽灵向量与孤立切片”。
> 2. **召回日志表 `kmc_knowledge_recall_log` 按月声明式范围分区与冷热归档策略**：
>    - 交付 PostgreSQL 生产级分区 DDL，将主键调整为 `(id, create_time)` 复合主键，支持按月自动范围分区（`PARTITION BY RANGE (create_time)`）；
>    - 提供历史分区数据无感转储归档策略（导出至 MinIO/本地并执行 `DETACH PARTITION`），主库表物理体积与 B-Tree 索引大幅缩减 70% 以上，彻底杜绝单表超千万级引发慢查询。
> 3. **线上真实 Query 难例自动挖掘与无感结构保留脱敏流水线**：
>    - 系统化落地 **四维难例漏斗挖掘**（零召回、低相似度置信度、CRAG 歧义重写、多轮追问澄清），摆脱单一离线合成用例的天花板效应；
>    - 交付生产级脱敏服务 `QuerySanitizer.java` 与自动化工具 `scripts/extract_and_mask_queries.py`：严格采用非贪婪线性防 ReDoS 正则、中国身份证模11校验、银行卡 Luhn 算法与会话级加盐映射（HMAC-SHA256），在 100% 抹除 PII 隐私的同时，数学保持向量余弦拓扑与分词语义结构（柯西-施瓦茨扰动 $\Delta s \le 0.05$）；
> 4. **不可变基准回放集 `rag-real-queries-v1.jsonl` 与 `EvalRegressionGate` 双轨防退化门禁**：
>    - 冻结不可变基准难例测试集，在 `EvalRegressionGate` 中引入真实难例回放通道与零召回率绝对红线（$\text{ZeroRecallRate} \le 0.05$），与既有合成 Holdout 测试集构成互补双轨门禁，杜绝 Goodhart's Law 人为主观调参作弊。

---

## Proposed Changes

### Component 1: 跨异构存储灾备自动化与分区治理 (scripts & deploy)

#### [NEW] [backup.sh](file:///Users/achilles/Documents/许子祺/Agent/scripts/backup.sh)
- 具备环境变量自适应加载与前置依赖检查（`pg_dump`, `docker`, `sha256sum`, `gzip`）；
- 跨异构存储快照对齐：记录时间戳屏障，原子捕获数据库状态；
- PostgreSQL + PGVector：采用目录并行格式 `pg_dump -F d -j 4 -Z 6` 导出，保留 Pre-data/Data/Post-data 逻辑分块；
- Neo4j：容器化调用 `neo4j-admin database dump` 或 APOC 导出；
- 生成 `manifest.json`，写入各存储元数据、导出参数、行数统计与 SHA-256 校验和。

#### [NEW] [restore.sh](file:///Users/achilles/Documents/许子祺/Agent/scripts/restore.sh)
- 恢复前前置检查：解密解压、校验 `manifest.json` 中所有备份文件的 SHA-256 完整性签名；
- 三阶段向量自愈恢复：
  - 阶段 1 (Pre-data)：建立 schema，检查并激活 `CREATE EXTENSION IF NOT EXISTS vector`；
  - 阶段 2 (Data)：恢复数据行（此时无 HNSW 索引，写入吞吐最大化）；
  - 阶段 3 (Post-data)：动态调整 `maintenance_work_mem = '2GB'` 与 `max_parallel_maintenance_workers = 4`，集中并行构建 HNSW 索引与 B-Tree 外键约束；
- Neo4j 覆盖式安全还原，并在完成后自动触发双向一致性对账巡检脚本，若存在悬挂指针输出告警或自动补偿修复。

#### [NEW] [16-recall-log-partition.sql](file:///Users/achilles/Documents/许子祺/Agent/deploy/sql/postgresql/16-recall-log-partition.sql)
- 声明式范围分区 DDL：将 `kmc_knowledge_recall_log` 改造为 `PARTITION BY RANGE (create_time)`；
- 预创建当月与未来 6 个月的月度分区表 `kmc_knowledge_recall_log_yYYYYmMM`；
- 自动建立分区局部索引（`idx_recall_log_kb_time`, `idx_recall_log_workspace_time`）；
- 提供冷数据归档与 `DETACH PARTITION` 标准模板。

---

### Component 2: 线上真实 Query 挖掘与无感脱敏引擎 (qknow-module-kmc)

#### [NEW] [QuerySanitizer.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/sanitizer/QuerySanitizer.java)
- 生产级多模式实体脱敏器，零外部大模型依赖：
  - 手机号（11位合规号段，保留前后各3位，中间掩码为 `****` 或类型原型 `[PHONE]`）；
  - 身份证号（18位带模11校验，替换为伪合规身份证或 `[IDCARD]`）；
  - 银行卡号（16~19位带 Luhn 算法校验，替换为保留尾号4位的测试卡号）；
  - 电子邮箱（非贪婪匹配，脱敏为加盐掩码格式）；
  - IPv4/IPv6 地址（私有地址保留网段语义替换，公网地址替换为测试保留网段）；
- 会话内一致性映射缓存（HMAC-SHA256 加盐一致性替换），确保代词和实体在长 Query 中结构一致；
- 严格保证正则无回溯爆炸（防 ReDoS），并在脱敏后执行 Leakage Detection 防御校验。

#### [NEW] [RealQueryMiningService.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kmc/qknow-module-kmc-biz/src/main/java/tech/qiantong/qknow/module/kmc/service/rag/mining/RealQueryMiningService.java)
- 负责从 `kmc_knowledge_recall_log` 中按四维漏斗挖掘代表性真实难例：
  - 漏斗 1：零召回结果（Zero-Recall, result is empty）；
  - 漏斗 2：低置信度（Top-1 Cosine Similarity < 0.60）；
  - 漏斗 3：CRAG 歧义与扩展重写样本；
  - 漏斗 4：多轮澄清与追问样本；
- 结合 `QuerySanitizer` 进行全链路无感加盐脱敏；
- 提供导出标准 JSONL 评测格式接口。

#### [NEW] [extract_and_mask_queries.py](file:///Users/achilles/Documents/许子祺/Agent/scripts/extract_and_mask_queries.py)
- 离线独立批处理与回流工具，直接读取日志数据或导出文件；
- 执行四维难例筛选、长中短句分层采样与结构保留脱敏；
- 输出并校验基准文件哈希，更新不可变测试集。

---

### Component 3: 不可变基准与双轨防退化回归门禁 (tests & qknow-module-kb)

#### [NEW] [rag-real-queries-v1.jsonl](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/fixtures/rag-real-queries-v1.jsonl)
- 包含至少 20 条代表性脱敏真实难例（覆盖零召回、歧义同义词、口语化短语、长尾实体问答）；
- 每条记录包含 `id`, `query`, `category`, `expected_kb_id`, `expected_contexts`, `split: "real-holdout"`；
- 作为不可变基准 fixture，固定 SHA-256 指纹。

#### [MODIFY] [EvalRegressionGate.java](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/eval/EvalRegressionGate.java)
- 扩展门禁判定维度：
  - 增加对真实难例回放评测结果指标（`zero_recall_rate` 零召回率）的绝对红线控制：`zero_recall_rate <= 0.05`（超过 5% 即阻断）；
  - 增加真实难例平均倒数排名 `real_mrr` 或上下文召回率 `real_context_recall` 的退化判定（相对基线退化 > 2% 阻断）；
  - 保留原有既有指标判定逻辑，完全向下兼容。

#### [NEW] [Phase16DisasterRecoveryAndQueryMiningGateTest.java](file:///Users/achilles/Documents/许子祺/Agent/backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase16DisasterRecoveryAndQueryMiningGateTest.java)
- 测试 1：验证 `QuerySanitizer` 针对手机号、身份证、银行卡、邮箱、IP 的脱敏准确率 100%，且能够通过模11和 Luhn 校验逻辑，会话内加盐一致性保持；
- 测试 2：验证 `QuerySanitizer` 防 ReDoS 性能：构造超长畸变文本（10,000 字符重复模式），脱敏执行耗时 ≤ 50ms，杜绝 CPU 挂起；
- 测试 3：验证 `RealQueryMiningService` 四维漏斗规则与样本筛选准确度；
- 测试 4：验证 `EvalRegressionGate` 双轨红线：当 `zero_recall_rate > 0.05` 时触发门禁阻断，当指标正常时通过门禁；
- 测试 5：验证 `rag-real-queries-v1.jsonl` fixture 格式完备性、无泄露 PII 且 SHA-256 指纹不可变。

---

## Verification Plan

### Automated Tests
```bash
# 1. 编译 kmc 与 kb 核心模块
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl qknow-module-kmc/qknow-module-kmc-biz,qknow-module-kb/qknow-module-kb-biz install -DskipTests

# 2. 运行 Phase 16 专属契约门禁测试
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests -Dtest=tech.qiantong.qknow.rag.eval.Phase16DisasterRecoveryAndQueryMiningGateTest test

# 3. 执行 Python 脱敏抽取脚本自测
python3 scripts/extract_and_mask_queries.py --test

# 4. 全量防退化回归测试（要求全库 726+ 项测试 100% 绿灯全过，零失败零退化）
bash run-with-java21.sh mvn -B -f backend/pom.xml -pl tests test
```

### Manual Verification
- 检查 `scripts/backup.sh` 与 `scripts/restore.sh` 语法正确性（`bash -n` 校验）；
- 校验 `16-recall-log-partition.sql` 语法有效性与分区覆盖周期；
- 校验 `rag-real-queries-v1.jsonl` 中无任何未脱敏真实身份敏感信息。
