# Phase 21 实施方案：动态自适应 Agentic 记忆图谱与时序衰减遗忘机制 (Temporal Decay Memory Graph & Cross-Session Episodic Consolidation)

> **文档位置**：`docs/plans/phase_21_plan.md`  
> **前置理论与工业支撑**：`docs/plans/phase_21_academic_report.md` & `docs/plans/phase_21_industrial_report.md`  
> **门禁状态**：**GATE_PASSED_WAITING_APPROVAL**（第一回合调研与方案编写完成，待用户批准实施契约后启动 TDD 编码）  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` / `deepseek-reasoner`）；唯一向量模型为 **阿里千问 (Qwen) Embedding（1536维）**；全链路无本地大模型，彻底弃用 OpenAI/GPT API。

---

## 一、方案背景与改造目标

### 1.1 背景与现状诊断
当前 Agent 记忆体系（`tech.qiantong.qknow.hermes.memory`）存在三大结构性缺陷：
1. **静态一维指数衰减与复习强化缺失**：当前长期记忆仅按固定 30 天半衰期衰减，完全忽略了交互中的检索唤醒，导致高频核心偏好被灾难性遗忘；
2. **纯扁平向量存储与图谱联想断裂**：长期记忆仅保存在 PgVector，缺少 Neo4j 实体偏好因果关系图支撑，跨会话多跳推理无法召回；
3. **后台单线程同步阻塞与消息丢失风险**：`SleepTimeMemoryAgent` 采用单线程轮询与同步调用，闲置会话多时调度长时间卡死，且前后台并发写无锁导致前台新消息被后台抹除。

### 1.2 改造目标与量化指标
1. **时序维度**：实现动态艾宾浩斯强化衰减递推模型 $S_{k+1} = S_k \cdot (1 + lpha \ln(1 + k))$，在数学上保证半衰期随唤醒次数单调递增，60 天模拟时序下长期偏好留存率保持 $\ge 90.0\%$（基线跌落至 $\le 25.0\%$）；
2. **图谱维度**：构建 Neo4j 用户实体偏好图，实现带度数截断（一跳 $\le 10$，二跳 $\le 5$）的 2-Hop 激活扩散检索与 3s 超时熔断，单次图查询耗时 $P_{95} \le 25	ext{ms}$，杜绝拓扑爆炸；
3. **多目标维度**：落地 Sigmoid 温度标定与百分位 Min-Max 校准的多目标 Pareto 重排，跨会话记忆 Top-5 召回率提升 $\ge 22.0\%$；
4. **工程高可靠**：重构后台 Worker 为有界线程池 + Redis 会话互斥锁（`memory:lock:session:{id}`，租约 60s）+ 双阈值内存保护（80% 限流降速，85% 熔断跳过），杜绝消息丢失与 OOM。

---

## 二、架构设计与核心组件

```
+----------------------------------------------------------------------------------------------------+
|                                    Client / Chat Application                                       |
+-------------------------------------------------+--------------------------------------------------+
                                                  | gRPC ChatRequest
                                                  v
+-------------------------------------------------+--------------------------------------------------+
|                            qknow-hermes: AgentOrchestrator                                         |
|                                                                                                    |
|  1. 在线快速写入 (Write)                      2. 在线混合召回 (Hybrid Memory Recall)               |
|     |                                            |                                                 |
|     +--> User/Assistant Msg                      +--> 1. L1 Redis 优先读取最近 20 条上下文           |
|          Redis 无锁快速追加                          +--> 2. 若触发深层个性化检索:                     |
|          延迟 <= 1.5ms                                   +------------------------------------+    |
|                                                          | 并行召回与图扩散:                  |    |
|                                                          | a. L2 PGVector: 1536维千问向量     |    |
|                                                          | b. L3 Neo4j: 2-Hop 实体偏好图扩散  |    |
|                                                          +-----------------+------------------+    |
|                                                                            |                       |
|                                                                            v                       |
|                                                          3. 多目标 Pareto 排序器:                  |
|                                                             Score = 0.4*Sim + 0.25*R + 0.15*I      |
|                                                                   + 0.2*C_graph                    |
+----------------------------------------------------------------------------+-----------------------+
                                                                             ^
+----------------------------------------------------------------------------+-----------------------+
|                                  底层三层协同存储底座                                               |
|  L1: Redis (短期会话与元数据，24h TTL) | L2: PGVector (1536维情景向量) | L3: Neo4j (用户实体偏好图)   |
+----------------------------------------------------------------------------------------------------+
                                      | 闲置扫描 (Idle > 30min)
                                      v
+----------------------------------------------------------------------------------------------------+
|                AsyncMemoryConsolidationWorker (异步高可靠固化引擎)                                 |
|  - 有界隔离线程池 (Core=4, Max=8, Queue=500, CallerRunsPolicy)                                     |
|  - 双阈值 JVM 内存水位保护 (80% 降级并发，85% 熔断跳过)                                            |
|  - Redis 会话分布式写锁 (前台优先，后台遇前台写入立即避让)                                         |
|  - SHA-256 内容签名幂等防重 (无增量跳过外部 API)                                                   |
|  - DeepSeek API 批量抽取四元组 (Subject-Predicate-Object-Validity)                                 |
|  - 双写驱动: PGVector 向量存储 + Neo4j 实体关系 MERGE (权重与计次自增)                             |
|  - Redis 消息安全安全裁剪 (对比处理前后消息数，有新消息仅裁已处理部分)                             |
+----------------------------------------------------------------------------------------------------+
```

---

## 三、数据库设计与 Schema 规范

### 3.1 PGVector 元数据扩展
在 `vector_store` 文档的 `metadata` JSONB 字段中增补字段（零 DDL 破坏）：
- `content_hash`: 摘要 SHA-256 指纹；
- `recall_count`: 被检索成功唤醒次数 $k$；
- `memory_strength`: 当前动态记忆强度 $S_k$（天）；
- `last_retrieved_at`: 最后一次被检索唤醒的时间戳（毫秒）；
- `entities`: 该记忆片段绑定的实体名称列表（`List<String>`）。

### 3.2 Neo4j 实体偏好图 Schema
1. **节点**：
   `(:UserMemoryEntity {id, userId, scope, name, type, importance, lastAccessedAt, createdAt})`
2. **关系**：
   - `[:PREFERS {weight, count, sentiment, updatedAt}]`
   - `[:RELATED_TO {relation, weight, updatedAt}]`
   - `[:ACTED_IN {role, timestamp}]`
3. **约束与索引**：
   ```cypher
   CREATE CONSTRAINT user_memory_entity_uniq IF NOT EXISTS
   FOR (e:UserMemoryEntity)
   REQUIRE (e.userId, e.scope, e.name) IS UNIQUE;

   CREATE INDEX user_memory_userId_idx IF NOT EXISTS
   FOR (e:UserMemoryEntity) ON (e.userId);

   CREATE INDEX user_memory_scope_idx IF NOT EXISTS
   FOR (e:UserMemoryEntity) ON (e.scope);
   ```

---

## 四、核心代码实现清单 (Proposed Changes)

### 4.1 新增/重构核心类
1. **`DynamicEbbinghausDecay.java`**（新建于 `tech.qiantong.qknow.hermes.memory`）：
   - 实现自适应强化递推计算：`computeNextStrength(double currentStrength, int recallCount, double alpha)`；
   - 实现留存率计算：`computeRetention(long lastRetrievedAt, double strength, long now)`；
   - 实现重要性初始强度调制：`computeInitialStrength(double importance)`。
2. **`UserMemoryGraphService.java`**（新建于 `tech.qiantong.qknow.hermes.memory` 或 `tech.qiantong.qknow.neo4j`）：
   - 封装 Neo4j 实体偏好图 Upsert（`upsertUserPreference`）；
   - 封装度数截断 2-Hop 扩散检索 Cypher（`spreadActivation`，超时 3s，捕获异常软降级返回空 Map）。
3. **`LongTermMemory.java`**（改造于 `tech.qiantong.qknow.hermes.memory`）：
   - 引入 `DynamicEbbinghausDecay` 与 `UserMemoryGraphService`；
   - 改造 `recall` 方法：执行 Over-fetching（$3 	imes 	ext{topK}$）、提取实体并触发 2-Hop 激活扩散、计算温度 Sigmoid 标定相关分与多目标 Pareto 排序；
   - 异步触发检索强化反馈（更新 `recall_count` 与 `memory_strength`）；
   - 增加 SHA-256 内容签名校验。
4. **`SleepTimeMemoryAgent.java`**（改造并增强于 `tech.qiantong.qknow.hermes.memory`）：
   - 重构为异步多线程执行架构（`ThreadPoolExecutor`）；
   - 引入 Redis 会话互斥锁（`SET NX EX 60`）；
   - 引入双阈值 JVM 内存水位监控；
   - 引入处理前后的消息计数比对，杜绝前台新消息被误删；
   - 接入结构化四元组抽取与双写。
5. **`MemoryManager.java`**（改造于 `tech.qiantong.qknow.hermes.memory`）：
   - 协调短期、长期与图谱的统一门面。

---

## 五、测试驱动开发 (TDD) 契约与实施计划

### 5.1 自动化测试矩阵
严格先写测试，验证红灯，再编写业务实现直至绿灯：
1. **`DynamicEbbinghausDecayTest.java`**：
   - 验证 $S_k$ 随 $k$ 严格单调递增，离散增量严格 $> 0$；
   - 验证高重要性记忆在全生命周期内的留存率下界不低于 $1 - \delta$；
   - 验证数值稳定性，防止浮点溢出与 NaN。
2. **`LongTermMemoryEnhancedTest.java`**：
   - 验证温度 Sigmoid 标定有效展开窄带余弦分布，方差显著提升；
   - 验证多目标排序单调性（提升任意正向特征必推高排序）；
   - 验证图谱激活扩散与向量检索融合打分；
   - 验证 SHA-256 签名幂等性。
3. **`AsyncMemoryConsolidationWorkerTest.java`**：
   - 验证多会话并发沉淀时，线程池有界排队与调度无阻塞；
   - 验证并发读写冲突：模拟前台追加新消息，后台固化仅安全裁剪已处理消息，前台新消息 100% 完整保留；
   - 验证 JVM 水位 $\ge 85\%$ 时触发熔断跳过。
4. **`UserMemoryGraphServiceTest.java`**：
   - 验证实体偏好原子 Upsert 与权重递增；
   - 验证度数截断防超级节点爆炸（返回结果严格限制在 Top-N 邻域内）；
   - 验证 3s 超时熔断与无感降级。

### 5.2 实施步骤次序
1. **步骤 1**：编写 `DynamicEbbinghausDecay` 数学工具类及单元测试 `DynamicEbbinghausDecayTest`；
2. **步骤 2**：编写 `UserMemoryGraphService` 及 Cypher 扩散测试；
3. **步骤 3**：重构 `LongTermMemory` 实现多目标 Pareto 排序与唤醒强化，编写 `LongTermMemoryEnhancedTest`；
4. **步骤 4**：重构 `SleepTimeMemoryAgent` 为高可靠异步 Worker，编写并发防丢锁测试 `AsyncMemoryConsolidationWorkerTest`；
5. **步骤 5**：运行 Maven 模块级全量测试与防退化全量回归，确保 741+ 测试持续 100% 绿灯；
6. **步骤 6**：更新 `00_master_index.md` 进度。

---

## 六、风险、停止条件与授权边界

### 6.1 立即停止条件 (Stop Conditions)
1. 编译失败或既有 741+ 项测试发生任何退化（Regression）；
2. 检索端到端延迟 $P_{95}$ 增量超出 $35	ext{ms}$ 预算红线；
3. 发现前后台并发写测试中存在任何消息丢失或内存溢出风险。

### 6.2 授权边界
本方案严格遵循 `@AGENTS.md`：当前处于只读调研与方案设计阶段，等待用户批准后方可执行编码与测试。
