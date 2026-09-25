# Phase 139 实施方案与契约设计：企业级异构数据库与中间件动态 MCP 运行时沙箱安全隔离与双向事务对齐中枢

## 一、方案核心目标与唯一算法假设

### 1.1 唯一待验证假设 (Hypothesis H-139)
在企业级多智能体调用数据库与中间件动态 MCP 工具场景下，通过构建“基于 SQL 抽象语法树 (AST) 与规则词法的只读安全沙箱与高危指令硬拦截器 + 基于 Undo Log 的两阶段双向事务对齐与逆向幂等补偿引擎 + 纯 Java 21 Record 格式不可变事务审计存证凭单 (`DatabaseMcpTransactionReceipt`)”，能够实现：
1. 对全部 DDL (`DROP`, `ALTER`, `TRUNCATE`)、无约束危险 DML 以及跨库系统敏感表扫描实现微秒级语法树分析与 100.0% 硬拦截（分析耗时 $\le 1.0\text{ms}$）；
2. 只读查询强制追加 `LIMIT` 上限重写，且自动注入 `tenant_id` 多租户逻辑隔离约束，越权渗透拦截率 100.0%；
3. 在业务操作失败或主动中断时，基于预置 Undo Log 逆向补偿生成精确反向 SQL，将多源写入双向事务回滚成功率提升至 100.0%，回滚耗时 $\le 15\text{ms}$，且签发不可变凭单并保证 SHA-256 常量时间自验真率 100.0%。

---

## 二、架构设计与核心组件规划

### 2.1 后端核心组件设计 (`qknow-hermes-core`)

#### 1. `DatabaseMcpSqlSandboxGovernor.java`
- **定位**：轻量级 SQL AST 抽象语法树解析与安全沙箱执行中枢；
- **核心机制**：
  * **AST 语法树只读沙箱 (Default-Deny)**：仅放行根操作为 `SELECT` 的安全只读语句；任何包含 `DROP`, `ALTER`, `TRUNCATE`, `CREATE` 等 DDL 语句 100% 阻断；
  * **危险 DML 拦截**：对于未携带 `WHERE` 谓词的 `DELETE` 和 `UPDATE` 操作判定为高危误操作，强制抛出 `SECURITY_SANDBOX_REJECTED`；
  * **系统字典表防渗透**：严格拦截针对 `information_schema`, `mysql.*`, `pg_catalog` 等底层敏感库的探测攻击；
  * **多租户与资源防护动态重写**：
    - 若查询未包含 `LIMIT`，自动重写追加 `LIMIT 1000`；若 `LIMIT` 超过 1000，硬性钳位至 1000；
    - 动态解析 WHERE 谓词，强制追加 `tenant_id = ?` 约束，确保多租户绝对隔离。

#### 2. `BiDirectionalTransactionAligner.java`
- **定位**：基于 Undo Log 的两阶段双向事务对齐与逆向补偿引擎；
- **核心机制**：
  * **前置镜像捕获与 Undo Log 生成**：在写操作执行前原子捕获行快照（Pre-Image），自动生成精确反向逆向 SQL（如针对 INSERT 生成 DELETE，针对 UPDATE 生成还原旧值的 UPDATE）；
  * **LIFO 逆向事务补偿**：在下游环节或网络超时异常触发时，按后进先出逆序执行 Undo Log，保证多数据源最终一致性，零脏数据残留；
  * **补偿耗时有界**：单次事务回滚耗时严格 $\le 15\text{ms}$。

#### 3. `DatabaseMcpTransactionReceipt.java`
- **定位**：纯 Java 21 Record 格式不可变密码学数据库事务审计存证凭单；
- **核心字段**：
  * `receiptId` (String): 全局唯一存证编号 (`RCP-DB-TX-...`)；
  * `tenantId` (String): 租户唯一标识；
  * `originalSql` (String): Agent 原始传入的 SQL；
  * `rewrittenSql` (String): 经沙箱安全重写后的安全 SQL；
  * `operationType` (String): 操作类型 (`SELECT_READONLY`, `COMPENSATED_ROLLBACK`, `REJECTED_BLOCKED`)；
  * `undoLogCount` (int): 生成的逆向补偿日志条数；
  * `isRolledBack` (boolean): 是否触发了双向事务回滚；
  * `latencyMs` (double): 校验与执行总耗时；
  * `timestamp` (long): 毫秒时间戳；
  * `sha256Signature` (String): 全字段规范化 SHA-256 防篡改签名；
- **特性**：提供常量时间自验真方法 `verifySignature()`。

---

### 2.2 前端可视化扩展 (`frontend`)

#### `DatabaseMcpInspectWidget.vue`
- **路径**：`frontend/src/views/kb/bot/build/components/mcp/DatabaseMcpInspectWidget.vue`；
- **视觉风格与规范**：
  * 严格遵循 Rule 2 UI/UX Pro Max 规范与单色现代暗黑钛金毛玻璃设计 Token；
  * 材质底色 `#0a0a0c`、毛玻璃 `backdrop-filter: blur(20px)`、发丝边框 `rgba(255,255,255,0.08)`；
- **核心交互特性**：
  1. **HikariCP 连接池实时状态仪表盘**：投射活跃连接数、空闲连接数与连接池健康度；
  2. **SQL AST 语法树安全审计视图**：对比显示原始 SQL 与注入 `LIMIT` + `tenant_id` 重写后的安全 SQL；
  3. **双向事务回滚时间轴**：可视化展示 Undo Log 生成与 LIFO 补偿流水线；
  4. **流畅度保证**：渲染响应时延 $\le 16.6\text{ms}$，满足 60 FPS 锁步标准。

---

## 三、8 项严苛契约测试定义 (Contract Tests)

| 测试编号 | 契约方法名 | 核心验证指标与断言标准 |
| :--- | :--- | :--- |
| **TC-139-1** | `testSqlSandbox_highRiskDdlInterception()` | 对 `DROP TABLE`, `ALTER TABLE`, `TRUNCATE` 等高危 DDL 100% 拦截，抛出 `SECURITY_SANDBOX_REJECTED` |
| **TC-139-2** | `testSqlSandbox_dangerousDmlWithoutWhereRejection()` | 对无 WHERE 条件的危险 `DELETE` 和 `UPDATE` 语法实现 100% 拦截，防止全表误删 |
| **TC-139-3** | `testSqlSandbox_multiTenantInjectionAndLimitRewrite()` | 对合法 `SELECT` 查询自动重写追加 `LIMIT` 上限与 `tenant_id` 过滤条件，改写耗时 $\le 1.0\text{ms}$ |
| **TC-139-4** | `testSystemTableAccess_hardRejection()` | 拦截针对 `information_schema`, `mysql.*`, `pg_catalog` 等系统级底层字典表的非法探测 |
| **TC-139-5** | `testBiDirectionalTransaction_undoLogGeneration()` | 正向写操作（INSERT/UPDATE）原子生成对应的反向 Undo Log（DELETE/UPDATE 反向镜像），生成耗时 $\le 0.5\text{ms}$ |
| **TC-139-6** | `testBiDirectionalTransaction_rollbackExecution()` | 模拟跨数据源操作异常时触发双向事务回滚，LIFO 执行 Undo Log，回滚成功率 100.0%，耗时 $\le 15\text{ms}$ |
| **TC-139-7** | `testDatabaseMcpTransactionReceipt_immutableVerification()` | 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间自验真率 100.0% |
| **TC-139-8** | `testEndToEndDatabaseMcp_fullPipelineIntegration()` | 端到端闭环：SQL 接收 $\to$ AST 沙箱安全预检 $\to$ 租约与租户重写 $\to$ Undo Log 捕获 $\to$ 模拟故障回滚 $\to$ 凭单防篡改签发 |

---

## 四、最小实现文件集合与禁止修改边界

### 4.1 最小修改文件集合
1. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/db/DatabaseMcpSqlSandboxGovernor.java` (新建)
2. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/db/BiDirectionalTransactionAligner.java` (新建)
3. `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/db/DatabaseMcpTransactionReceipt.java` (新建)
4. `frontend/src/views/kb/bot/build/components/mcp/DatabaseMcpInspectWidget.vue` (新建)
5. `backend/tests/src/test/java/tech/qiantong/qknow/hermes/benchmark/Phase139DatabaseMcpSandboxContractTest.java` (新建)

### 4.2 严格禁止修改的边界
- 严禁修改已归档封存的力学物理沙箱目录：`tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改全局系统默认 JDK 17，所有编译与测试必须严格使用 Java 21 隔离环境变量；
- 严禁引入任何未获批准的外部重型分布式事务中间件依赖（如 Seata 客户端等）；
- 严禁在测试中修改断言期望值以掩盖失败；
- 严禁使用 OpenAI API 或本地小模型，唯一生成模型为 DeepSeek API。

---

## 五、完整复现与全量回归验证命令

```bash
# 1. 编译安装 qknow-hermes-core 模块
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn clean install -pl qknow-hermes/qknow-hermes-core -DskipTests

# 2. 运行 Phase 139 专项严苛契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase139DatabaseMcpSandboxContractTest

# 3. 运行 Phase 125 ~ Phase 139 跨阶段全量基准回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH mvn test -pl tests -Dtest=Phase125SwarmConsensusContractTest,Phase126DistributedSwarmContractTest,Phase127E2ESwarmContractTest,Phase128AutonomousSwarmContractTest,Phase129UnifiedE2ESwarmContractTest,Phase130SwarmGovernanceContractTest,Phase131EnterpriseMcpProductionBenchmarkContractTest,Phase132HierarchicalGraphRagContractTest,Phase133E2EChaosBenchmarkContractTest,Phase134FrontendDagHitlIntegrationContractTest,Phase135SwarmDynamicTopologyContractTest,Phase136McpGatewayContractTest,Phase137GraphRagCognitiveContractTest,Phase138WorkflowSelfHealingContractTest,Phase139DatabaseMcpSandboxContractTest

# 4. 验证前端 Vite 生产环境全量编译构建
cd frontend && npm run build:prod
```
