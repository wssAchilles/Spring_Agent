# Phase 108 实施方案与工程契约 (Decision-Complete Plan)
## 自适应思考调控中枢 (Adaptive Thinking) 与千问 1536 维 CoT 思考链认知缓存器 (Adaptive Thinking Governor & Hyperspherical CoT Cognitive Cache)

> **归档路径**：`docs/plans/phase_108_plan.md`  
> **制定时间**：2026-09-19  
> **状态**：已就绪待执行 (Approved & Ready for Execution)  
> **前置依赖完成**：学术理论报告 ([`phase_108_academic_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_108_academic_report.md))，工业落地报告 ([`phase_108_industrial_report.md`](file:///Users/achilles/Documents/许子祺/Agent/docs/plans/phase_108_industrial_report.md))  
> **战略定位**：严格遵守《业务定位与领域边界铁律（铁律九）》，100% 聚焦于**支柱一：复杂业务 Agent 认知与编排 (Cognitive Orchestration)** 与 **支柱三：高保真 RAG 知识引擎与多模态图谱 (Advanced RAG & Multimodal Knowledge)**。彻底封存具身力学资产，全力攻坚以 `deepseek-flash` 为唯一主干的自适应思考控制与 1536 维超球面认知缓存底座。  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（主干 `deepseek-flash`，通过请求体 `thinking: {"type": "enabled" | "disabled"}` 与 `reasoning_effort` 动态控制）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（1536 维超球面流形，$\|\mathbf{v}\|_2 = 1.0 \pm 10^{-4}$）；全系统绝无本地大模型，彻底弃用 OpenAI/GPT API；后端编译运行唯一使用隔离 **Java 21** 虚拟环境（`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。

---

### 一、 当前代码与失败机制剖析 (Current Code & Failure Mechanisms)

#### 1. 既有实现与断层梳理
1. **模型契约层**：系统已在 Phase 107 前置补丁中实现了 `DeepSeekCompatibleChatModel`，支持 `DeepSeekChatOptions` 中的 `thinkingEnabled` 与 `reasoningEffort` 参数化传递，但上层编排层缺乏根据请求难度、CRAG 检索置信度、因果冲突度自适应决策的调控中枢，运行时只能静态全开或全关；
2. **历史代码残留**：`tech.qiantong.qknow.ai.mor` 包中存在早期的 `MixtureOfReasoningGovernor.java` 与 `ReasoningDecision.java`，但残留了 `FAST_V3` / `DEEP_R1` 的双模型物理路由旧假设，严重违背当前唯一主干 `deepseek-flash` 的真实基线；
3. **认知资产浪费**：长思考链（`reasoning_content`）在推演一次后被直接丢弃，缺少结构化脚手架（200~400 字）提炼器与千问 1536 维超球面缓存，高频同质复杂任务反复从头昂贵推演；
4. **流式体验瓶颈**：流式推送缺乏双轨信封隔离，缺少平滑的思考折叠与打字机脉冲分轨支持。

#### 2. 本阶段唯一核心待验证假设 (H-PHASE108-001)
> **核心假设声明 (H-PHASE108-001)**：  
> 在唯一生成模型 DeepSeek API（主干 `deepseek-flash`）与唯一向量模型阿里千问（1536 维超球面单位向量流形 $\mathbb{S}^{1535}$）约束下：  
> 构建**基于三维感知模型（语义复杂度、CRAG 证据熵、工具冲突度）的最优思考必要性判定调控中枢 (`AdaptiveThinkingGovernor`)**，能够实现思考触发决策耗时 $\le 1.0\text{ms}$，且使简单任务 100% 保持 Flash 极速模式（TTFT $\le 450\text{ms}$）；  
> 构建**基于信息瓶颈剪枝的思考链决策脚手架提炼器 (`CoTScaffoldDistiller`)**，能够将 2000~5000 字符长思维链浓缩为 200~400 字符的因果决策逻辑树，因果决策信息保真度 $\ge 97.0\%$；  
> 构建**基于千问 1536 维超球面单位内积的认知缓存器 (`CoTCognitiveCacheService`)**，在设定余弦相似度阈值 $\tau^* \in [0.88, 0.92]$ 与反向语义极性过滤下，对高频同构复杂任务实现认知脚手架极速召回并注入 Flash 默认模式，系统综合 P99 首字延迟降低 $\ge 60.0\%$，推理 Token 成本削减 $\ge 65.0\%$，且输出质量严格逼近全量思考模式（决策一致性 $\ge 98.5\%$）。

---

### 二、 最小代码实现集合与文件边界 (Minimal Implementation Files)

#### 1. 拟重构与强化的核心组件 (`tech.qiantong.qknow.ai.mor.*`)
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/model/ReasoningDecision.java`** [MODIFY]：
  - 更新枚举 `RoutingBranch` 为 `FAST_FLASH`、`FLASH_WITH_SCAFFOLD`、`DEEP_THINKING`（保留 `FAST_V3`、`V3_WITH_SCAFFOLD`、`DEEP_R1` 为兼容别名）；
  - 增加 `reasoningEffort` 字段（`low` / `medium` / `high`），支持将决策结果一键转换为 `DeepSeekChatOptions`。
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/MixtureOfReasoningGovernor.java`** [MODIFY]：
  - 彻底拔除 V3/R1 物理模型路由残留，升级为基于三维指标（语义复杂度、检索置信度缺口、因果冲突度）的自适应思考调控中枢；
  - 动态计算思考必要性指数 $\Psi_{\text{think}}$，映射至 `FAST_FLASH` / `FLASH_WITH_SCAFFOLD` / `DEEP_THINKING` 及 `reasoning_effort`。
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/AdaptiveThinkingGovernor.java`** [NEW]：
  - 作为 `MixtureOfReasoningGovernor` 的现代门面接口，统一提供 `evaluate(String query, CragContext, tools)` 与 `toChatOptions(ReasoningDecision)` 契约。
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/ScaffoldDistiller.java`** [MODIFY]：
  - 强化因果充分性断言与 200~400 字因果逻辑树（假设边界、关键推演步进、判定约束）蒸馏，压缩比 $\ge 80\%$。
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/DualTrackThinkingDispatcher.java`** [NEW]：
  - 双轨流式思考分发器：基于 FSM 解析器将输入流解耦为 `event: thinking` 与 `event: message`，输出不可变 `DualTrackStreamEnvelope`。
- **`backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai/mor/cache/CoTCognitiveCacheService.java`** [MODIFY]：
  - 强化多租户与安全上下文哈希绑定，严格执行千问 1536 维超球面单位归一化、切片 SHA-256 不可变签名验证与测地线内积门限 $\ge 0.92$。

#### 2. 拟新增的专属契约测试套件
- **`backend/tests/src/test/java/tech/qiantong/qknow/ai/mor/AdaptiveThinkingCognitiveCacheContractTest.java`** [NEW]：
  - 覆盖三维动态思考仲裁、`reasoning_effort` 梯级映射、超球面 1536 维内积命中、切片变更旧缓存失效、因果脚手架蒸馏与注入、双轨流式分发等 12 项高精度集成契约测试。

---

### 三、 专属契约测试规划与验证命令 (TDD Plan & Exact Commands)

#### 1. 前置先红阶段（Red Stage）
- 编写 `AdaptiveThinkingCognitiveCacheContractTest.java`，定义 12 项严密断言，验证编译失败或断言红灯。

#### 2. 实现先绿阶段（Green Stage）
- 逐一实现并强化 `ReasoningDecision`、`MixtureOfReasoningGovernor`、`AdaptiveThinkingGovernor`、`ScaffoldDistiller`、`DualTrackThinkingDispatcher` 与 `CoTCognitiveCacheService`；
- 执行 Phase 108 专属验证命令：
  ```bash
  JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,tests -Dtest="tech.qiantong.qknow.ai.mor.*Test" -Dsurefire.failIfNoSpecifiedTests=false
  ```

#### 3. 跨模块全量联合回归基线保护（Phase 101 ~ Phase 108，100% 绿灯）
- 执行全量回归验证：
  ```bash
  JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl qknow-framework/qknow-ai,qknow-hermes/qknow-hermes-core,qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client,qknow-mcp/qknow-mcp-server,qknow-module-kmc/qknow-module-kmc-biz,tests -Dtest="tech.qiantong.qknow.ai.deepseek.*ContractTest,tech.qiantong.qknow.ai.mor.*Test,tech.qiantong.qknow.mcp.server.*Test,tech.qiantong.qknow.hermes.agent.dag.*Test,tech.qiantong.qknow.hermes.agent.debate.*Test,tech.qiantong.qknow.rag.EnhancedSemanticCacheContractTest" -Dsurefire.failIfNoSpecifiedTests=false
  ```

---

### 四、 残余风险、停止条件与授权边界 (Risks & Governance)

1. **残余风险与防御**：
   - 风险：若知识库切片内容变更未及时同步至签名，可能导致旧缓存短暂生效；
   - 对策：每次检索召回必须基于实时切片现算 SHA-256 签名，签名不符即刻视作未命中；
2. **立即停止条件**：
   - 仲裁决策耗时超过 $2.0\text{ms}$；
   - 任何思考过程（`reasoning_content`）泄漏至正文轨；
   - 否定词或语义反转样本发生误命中（穿透率 $> 0.0\%$）；
3. **独立授权要求**：
   - 本阶段完成后，代码与单元测试绿灯归档；后续在生产网关全量启用自适应思考调控与认知缓存，需独立发起生产灰度流量授权。
