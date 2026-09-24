# Phase 126 实施方案与技术契约
## 分布式 MCP 工具调用断点租约超时自愈 (Lease/Heartbeat TTL)、级联依赖事务补偿 (Sagas) 与所有权仲裁中枢
### (Phase 126 Implementation Plan & Technical Contract)

> **归档路径**：`docs/plans/phase_126_plan.md`  
> **所属阶段**：第六演进阶段 (Phase 125 ~ Phase 128) 第二步骤  
> **所属核心支柱**：支柱一：复杂业务 Agent 认知与编排 (Cognitive Orchestration) & 支柱二：生产级企业 MCP 工具生态 (Enterprise MCP Ecosystem)  
> **顶刊学术对齐**：ESWA 手稿 Lemma 3.1（断点自愈活性定理）与 Section 3.3 分布式长事务；抹平节点假死长 GC 脑裂与长任务永久悬挂风险  
> **模型基线**：唯一生成模型为 DeepSeek API（主干模型参数化思考 `thinking: {"type": "enabled"}`）；唯一向量模型阿里千问 1536 维超球面；Java 21 隔离环境。

---

## 一、 核心目标与交付清单

1. **改造 `DagCheckpointManager.java`**：
   - 增加 DDL 字段：`lease_owner_id VARCHAR(128)`, `lease_expire_at TIMESTAMP`, `fencing_token BIGINT NOT NULL DEFAULT 1`；
   - 扩充 `DagCheckpoint` 类模型；
   - 实现双轨原子 CAS 抢占方法 `wakeSuspendedOrRecoverLease(...)`；
   - 实现持有节点心跳续租方法 `refreshLease(...)`；
   - 实现安全写屏障方法 `saveCheckpointWithFencingToken(...)`；
2. **实现不可变自愈存证凭单 `LeaseLivenessRecoveryReceipt.java`**：
   - 纯 Java 21 Record 格式，包含自签名验真功能；
3. **实现分布式租约协调中枢 `LeaseLivenessRecoveryCoordinator.java`**：
   - 管理后台看门狗探活与超时抢占自愈调度；
4. **实现级联事务逆序补偿中枢 `McpSagasCompensationGovernor.java`**：
   - 解析检查点中的 `compensation_log`，采用 Java 21 虚拟线程以 LIFO 严格逆序并发调度补偿；
5. **编写专属契约测试并验证 `Phase126McpLeaseLivenessContractTest.java`**：
   - 覆盖 8 项硬核指标，实现 100% 绿灯，0 破坏性变更，保持历史向下兼容。

---

## 二、 验证与构建命令

```bash
# 1. 编译并本地安装 qknow-hermes-core
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean install -DskipTests -f backend/qknow-hermes/qknow-hermes-core/pom.xml

# 2. 运行 Phase 126 专属契约测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase126McpLeaseLivenessContractTest -f backend/tests/pom.xml

# 3. 运行 Phase 121 ~ Phase 126 全量联合回归测试
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=Phase121SwarmDelegationContractTest,Phase122McpSecuritySandboxContractTest,Phase123DeepSeekThinkingBudgetContractTest,Phase124GraphRagTopologyContrastiveContractTest,Phase125ReActStrictWindowContractTest,Phase126McpLeaseLivenessContractTest,ReActCycleGuardTest -f backend/tests/pom.xml
```
