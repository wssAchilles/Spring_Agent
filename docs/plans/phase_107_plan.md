# Phase 107 实施计划与工程契约 (Decision-Complete Plan)
## 企业级原生 MCP Server 导出中枢与四道安全防线体系 (Enterprise Native MCP Server Export & Quad-Defense Security Engine)

> **归档路径**：`docs/plans/phase_107_plan.md`  
> **制定时间**：2026-09-19  
> **状态**：已就绪待执行 (Approved & Ready for Execution)  
> **前置依赖完成**：学术论证报告 ([`phase_107_academic_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_107_academic_report.md))，工业落地报告 ([`phase_107_industrial_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_107_industrial_report.md))  
> **战略定位**：严格遵守《业务定位与领域边界铁律（铁律九）》，100% 聚焦于**支柱二：生产级企业 MCP 工具生态 (Enterprise MCP Ecosystem)**，赋能支柱一与支柱三。彻底叫停并封存具身力学沙箱，构筑原生 MCP 导出与高防安全底座。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（V3/R1）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面流形，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地大模型；后端编译运行唯一使用隔离 **Java 21** 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

### 一、 唯一核心待验证假设与契约断言

- **唯一核心假设 (H-PHASE107-001)**：
  在唯一生成模型（DeepSeek API）、唯一向量模型（阿里千问 1536 维超球面单位向量）与 Java 21 隔离环境约束下，通过构建**基于上下文无关文法投影与李雅普诺夫势函数收敛的模式有界强类型校验器（第一道防线）**、**基于单调时钟与一次性 Nonce 状态转移机的瞬态时效租约引擎（第二道防线）**、**基于敏感词语义图谱投影与对抗特征熵检测的间接提示词注入主动免疫拦截器（第三道防线）**，以及**完全清空环境变量硬隔离沙箱与 SHA-256 Merkle-Damgård 全链路不可变存证凭单中枢（第四道防线）**，能够在保证单步校验耗时 $\le 500\mu\text{s}$、租约验证 $\le 100\mu\text{s}$、防御决策延迟 $\le 1.5\text{ms}$ 的极致性能下，实现畸变入参穿透率 $\mathbb{P}(\text{Malformed Bypass}) \equiv 0.0$、重放攻击穿透率 $\mathbb{P}(\text{Replay}) \equiv 0.0$、租约过期阻断率 $100.0\%$、提示词注入检出率 $\ge 99.5\%$（误报率 $\le 0.5\%$），以及凭单伪造概率满足离散对数抗碰撞性 $\mathcal{O}(2^{-128})$。

---

### 二、 最小代码实现集合与文件边界 (Minimal Implementation Files)

#### 1. 拟新增与强化的后端源码文件（严禁越界修改既有稳定模块）
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/model/McpServerExportReceipt.java`** [NEW]：
  - 纯 Java 21 Record，记录导出状态、工具数量、四道防线掩码与 SHA-256 密码学防篡改自验真。
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/security/McpTransientLeaseManager.java`** [NEW]：
  - 60 秒瞬态时效租约管理器，支持 `issueLease` 与基于 CAS 原子操作的一次性消费（Consume-Once）。
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/security/QuadDefenseSecurityPipeline.java`** [NEW]：
  - 工业级四道纵深防御工程引擎：16KB 物理截断、租约强核验、指令覆盖主动免疫与环境变量清空沙箱。
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/transport/StdioServerEnterpriseTransport.java`** [NEW]：
  - 支持 CLI Stdio 标准输入输出的稳定安全传输通道，内置 16KB 行截断与 JSON-RPC 分发。
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/registry/McpServerRegistry.java`** [MODIFY]：
  - 强化 JSON-RPC 请求分发中枢，内嵌安全流水线审查与存证凭单签发。
- **`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/provider/CodeSandboxMcpProvider.java`** [MODIFY]：
  - 适配四道防线，标记为 `HIGH_RISK` 并绑定 `requiresLease=true`。

#### 2. 拟新增的专属契约测试套件
- **`backend/tests/src/test/java/tech/qiantong/qknow/mcp/server/McpServerExportReceiptTest.java`** [NEW]
- **`backend/tests/src/test/java/tech/qiantong/qknow/mcp/server/McpTransientLeaseManagerTest.java`** [NEW]
- **`backend/tests/src/test/java/tech/qiantong/qknow/mcp/server/QuadDefenseSecurityPipelineTest.java`** [NEW]
- **`backend/tests/src/test/java/tech/qiantong/qknow/mcp/server/Phase107McpServerExportIntegrationTest.java`** [NEW]

---

### 三、 专属契约测试规划与验证命令 (TDD Plan & Exact Commands)

#### 1. 前置先红阶段（Red Stage）：编写专属测试并确认编译失败或断言红灯
- 创建 4 个专属测试类，涵盖：
  - 契约 1：凭单 SHA-256 结构与自验真有效性；
  - 契约 2：瞬态租约 60 秒时效、超时失效与 CAS 一次性消费防重放；
  - 契约 3：四道防线（16KB截断、时效租约、提示词注入拦截与敏感信息脱敏、清空环境变量沙箱）；
  - 契约 4：全链路端到端集成测试（工具注册、标准 JSON-RPC 2.0 调用、高危租约核销与不可变存证凭单签发）。

#### 2. 实现先绿阶段（Green Stage）：填充业务实现并通过测试
- 执行 Phase 107 专属测试验证：
  ```bash
  JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-mcp/qknow-mcp-server,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="tech.qiantong.qknow.mcp.server.*Test" -Dsurefire.failIfNoSpecifiedTests=false
  ```

#### 3. 跨模块全量联合回归基线保护（Phase 101 ~ Phase 107 全绿，$\ge 90$ 项测试）
- 执行全量回归验证：
  ```bash
  JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-mcp/qknow-mcp-server,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="*Phase101*Test,*Phase102*Test,*Phase103*Test,*Phase104*Test,*Phase105*Test,*Phase106*Test,*Phase107*Test,HierarchicalDocumentChunkerTest,GraphRagSubgraphReasonerTest,StreamingTypewriterAlignBufferTest,GraphRagOrchestrationControlBusTest,WorkflowDebugReceiptTest,TimeTravelSnapshotRingBufferTest,CognitiveProjectionFilterTest,EnterpriseMcpClientTransportTest,HighRiskToolSafetyGovernorTest,ToolRagFilterTest,McpExecutionReceiptTest,StateGraph*Test,NodeSelfHealingRouterTest,DebateNetworkCoordinatorTest,SwarmHandoffProtocolTest,MoaMixtureOfAgentsRouterTest,DebateConsensusJudicialReceiptTest,HierarchicalTraceSpanTest,HierarchicalExecutionTraceEngineTest,TraceExecutionReceiptTest,McpServerExportReceiptTest,McpTransientLeaseManagerTest,QuadDefenseSecurityPipelineTest" -Dsurefire.failIfNoSpecifiedTests=false
  ```
- 确保测试用例 100% 绿灯（0 失败、0 错误、0 跳过）。
