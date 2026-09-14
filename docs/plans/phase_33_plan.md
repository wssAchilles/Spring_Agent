# Phase 33 决策完备实施方案：全链路 AI 编排管道与全局零侵入切面引擎 (Zero-Invasive End-to-End Orchestration & Context Pipeline)

> **遵循规范**：`AGENTS.md` Research-to-Implementation Gate 准入规范  
> **学术依据**：`docs/plans/phase_33_academic_report.md` (64KB，定理 1.1 强偏序不变量、定理 1.2 无干扰性定理、定理 2.1 WCET 关键路径界限、定理 2.2 延迟收敛定理、定理 3.1/3.2 安全性与有限步终止性定理)  
> **工程依据**：`docs/plans/phase_33_industrial_report.md` (76KB，Spring AI Advisor 架构对标、LangChain4j 监听模型、Dify VariablePool、Envoy AI Gateway 过滤器链、Alibaba TTL 线程池上下文复制与 OpenTelemetry AutoCloseable Scope)  
> **核心假设**：唯一核心待验证假设 H-Phase33（构建企业级零侵入责任链 AI 编排管道、TransmittableThreadLocal 防泄漏上下文持有者、声明式 @AiOrchestrated AOP 切面，实现业务代码零硬编码侵入、100% 跨租户防串标、简单问答动态跳过耗时削减 $\ge 60\%$、慢节点超时硬配额截断 100% 生效与防重入死锁 0 溢出）  
> **架构模型基线**：唯一生成模型为 DeepSeek API；唯一向量模型为阿里千问 1536 维 Embedding；后端全量 Java 21 隔离环境 (`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)。

---

## 一、方案核心设计与架构拓扑

```text
========================================================================================================================
                                      @AiOrchestrated 声明式切面拦截架构与上下文流转图
========================================================================================================================

    HTTP / RPC 客户端请求 (携带 TenantId, SLA Header, User Query)
                │
                ▼
   ┌───────────────────────────┐
   │ Spring AOP 动态代理接入层  │  <-- 拦截标注了 @AiOrchestrated 的业务方法 (Chat / KMC / Agent)
   └────────────┬──────────────┘
                │
                ▼
   ┌────────────────────────────────────────────────────────────────────────────────────────┐
   │ GlobalAiOrchestrationAspect (零侵入全局切面)                                           │
   │  1. 解析方法参数与 SpEL 表达式，提取 User Query / TenantId / Profile                   │
   │  2. 防重入检测: 若 reentrancyDepth > 0，直接 proceed() 放行，杜绝 StackOverflowError  │
   │  3. 初始化 AiPipelineContext，绑定全局 Deadline (基于当前系统时间 + Timeout 配置)       │
   │  4. try (var scope = AiPipelineContextHolder.open(context)) { 驱动 PipelineEngine }   │
   └────────────┬───────────────────────────────────────────────────────────────────────────┘
                │
                ▼
   ┌────────────────────────────────────────────────────────────────────────────────────────┐
   │ AiPipelineEngine (阶段化责任链引擎)                                                    │
   │                                                                                        │
   │   [Stage 1: INPUT_GUARDRAIL]  ──> 纳秒 PII 脱敏 + 越狱注入防卫 (Fail-Close)            │
   │                │                                                                       │
   │   [Stage 2: SLA_ROUTING]      ──> 基于延迟与成本感知动态选路 (LatencyAwareSlaRouter)   │
   │                │                                                                       │
   │   [Stage 3: CONSENSUS_ACTIVE] ──> 条件自适应: 简单问答跳过 / 复杂分析激活 PBFT 多智能体 │
   │                │                                                                       │
   │   [Stage 4: MODEL_EXECUTION]  ──> 承接底层业务逻辑 proceed() 或 ModelGateway 代理调用  │
   │                │                                                                       │
   │   [Stage 5: OUTPUT_GUARDRAIL] ──> 敏感词 Fail-Close + 幽灵引用清洗 + 事实忠实度核验     │
   │                │                                                                       │
   │   [Stage 6: MERKLE_AUDIT]     ──> 密码学不可篡改 Merkle 树证据链存证 (Fail-Open 降级)   │
   │                │                                                                       │
   │   [Stage 7: CAUSAL_GRAPH]     ──> 全链路神经符号因果可解释性拓扑溯源图记录 (Fail-Open) │
   └────────────┬───────────────────────────────────────────────────────────────────────────┘
                │
                ▼
   ┌────────────────────────────────────────────────────────────────────────────────────────┐
   │ finally 作用域自动销毁 (AutoCloseable Scope)                                           │
   │  - 强制执行 AiPipelineContextHolder.remove()，彻底清空当前线程 TTL 变量                 │
   │  - 安全归还 Tomcat / ForkJoinPool 工作线程，杜绝多租户上下文串标与内存泄漏             │
   └────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、核心组件接口与契约定义

### 2.1 编排阶段与处理器规范
- `PipelineStage` 枚举：
  - `INPUT_GUARDRAIL` (order=100, failClose=true)
  - `SLA_ROUTING` (order=200, failClose=false)
  - `CONSENSUS_ACTIVATION` (order=300, failClose=false)
  - `MODEL_EXECUTION` (order=400, failClose=true)
  - `OUTPUT_GUARDRAIL` (order=500, failClose=true)
  - `MERKLE_AUDIT` (order=600, failClose=false)
  - `CAUSAL_GRAPH` (order=700, failClose=false)
- `PipelineStageHandler` 接口：
  - `PipelineStage getStage()`
  - `boolean shouldActivate(AiPipelineContext context)`
  - `long getStageTimeoutMs(AiPipelineContext context)`
  - `void handle(AiPipelineContext context) throws Exception`

### 2.2 管道上下文与线程持有者
- `AiPipelineContext`：
  - 核心只读入参：`traceId`, `tenantId`, `userId`, `profile`, `createdAt`, `globalDeadlineMs`
  - 防重入控制：`AtomicInteger reentrancyDepth`
  - 动态状态：`rawPrompt`, `sanitizedPrompt`, `piiRedactionMap`, `inputGuardrailDecision`, `selectedChannelId`, `selectedModelName`, `consensusActivated`, `consensusWinnerProposal`, `consensusVoters`, `executionResult`, `rawOutput`, `sanitizedOutput`, `outputGuardrailDecision`, `merkleRootHash`, `merkleInclusionProof`, `causalNodes`, `attributes`
  - 超时计算：`boolean isExpired()`, `long getRemainingTimeMs()`
- `AiPipelineContextHolder`：
  - 基于 `TransmittableThreadLocal<AiPipelineContext>`
  - `static AutoCloseableScope open(AiPipelineContext context)`
  - `static AiPipelineContext get()`
  - `static void remove()`
  - 内部静态类 `AutoCloseableScope implements AutoCloseable`：`close()` 时严格恢复上层上下文或彻底清空，确保线程池复用 0 泄漏。

### 2.3 管道调度引擎 `AiPipelineEngine`
- 职责：
  1. 收集 Spring 容器注入的所有 `PipelineStageHandler` 实例，按 `stage.getOrder()` 严格升序排序；
  2. 提供 `executePipeline(AiPipelineContext context, ModelExecutionCallback callback)` 入口；
  3. 遍历各阶段：
     - 若 `context.isExpired()`，抛出超时熔断异常；
     - 判定 `handler.shouldActivate(context)`：若为 `false`，记录已跳过标记并推进；
     - 若为 `MODEL_EXECUTION` 阶段，执行传入的 `callback.call()` 并将结果注入上下文；
     - 对各阶段执行计时与阶段超时保护（阶段超时不可超过 `handler.getStageTimeoutMs(context)` 与全局剩余时间）；
     - 异常处理：若发生异常，判断 `stage.isFailClose()`，为 `true` 则包装抛出阻断业务，为 `false` 则记录警告日志并 Fail-Open 降级放行。

### 2.4 声明式注解与 AOP 切面
- `@AiOrchestrated`：
  - `String profile() default "DEFAULT"`
  - `long timeoutMs() default 30000L`
  - `boolean enableConsensus() default false`
  - `boolean enableMerkleAudit() default true`
  - `boolean enableCausalGraph() default true`
- `GlobalAiOrchestrationAspect`：
  - `@Around("@annotation(aiOrchestrated)")`
  - 提取 `rawPrompt` 与 `tenantId`
  - 检查 `reentrancyDepth > 0`：若重入则直接 `joinPoint.proceed()` 放行，杜绝死循环
  - `try (var scope = AiPipelineContextHolder.open(context)) { pipelineEngine.executePipeline(...); return context.getExecutionResult(); }`

---

## 三、自动化契约测试清单 (10 项专项核心契约)

在 `backend/tests/src/test/java/tech/qiantong/qknow/rag/eval/Phase33OrchestrationPipelineContractTest.java` 中构建 10 项严密契约测试：

1. **契约 01: `contract01_pipelineEngine_executesInStrictTopologicalOrder`**  
   - **验证目标**：验证 7 大阶段严格按照拓扑序（100 -> 200 -> 300 -> 400 -> 500 -> 600 -> 700）推进，前置脱敏先于模型调用，后置审计后于模型调用。
2. **契约 02: `contract02_inputGuardrailStage_masksPiiAndBlocksInjectionFailClose`**  
   - **验证目标**：验证输入侧 PII 敏感信息在进入模型前 100% 完成脱敏；恶意越狱攻击触发 Fail-Close 阻断并立即短路抛出合规异常，跳过后续所有阶段。
3. **契约 03: `contract03_slaRoutingStage_selectsOptimalChannelByScenario`**  
   - **验证目标**：验证在线交互画像选路优先分配低延迟通道，离线批处理画像选路优先分配经济通道，并将决策结果写入上下文。
4. **契约 04: `contract04_consensusStage_conditionallySkipsSimpleFactsAndActivatesComplex`**  
   - **验证目标**：验证简单事实问答（单轮事实）`shouldActivate` 返回 `false` 动态跳过，复杂仲裁分析场景成功激活多智能体共识，端到端耗时削减 $\ge 60\%$。
5. **契约 05: `contract05_outputGuardrailStage_filtersRedlinesAndHealsCitations`**  
   - **验证目标**：验证输出侧敏感词触发 Fail-Close 阻断；幽灵引用与低忠实度内容成功清洗并注入合规预警。
6. **契约 06: `contract06_auditStages_executeMerkleAndCausalGraphFailOpenOnException`**  
   - **验证目标**：验证 Merkle 树存证与因果图构建在遭遇模拟故障异常时，触发 Fail-Open 降级放行，业务正常输出且记录降级日志，绝不拖垮核心业务。
7. **契约 07: `contract07_pipelineContextHolder_transmitsAcrossThreadPoolAndCleansSafely`**  
   - **验证目标**：验证在 `ForkJoinPool` / 异步线程池提交任务时，TTL 确保上下文无损跨线程透传；在 `AutoCloseableScope` 退出后，线程池线程上下文 100% 清空，跨租户串标为 0。
8. **契约 08: `contract08_deadlineBudget_abortsWhenGlobalTimeoutExpired`**  
   - **验证目标**：验证当全局超时已过（`isExpired() == true`）或单阶段慢节点阻塞时，管道引擎毫秒级识别并阻断后续执行，释放资源。
9. **契约 09: `contract09_globalAiAspect_orchestratesTransparentlyWithoutModifyingBusinessCode`**  
   - **验证目标**：验证被 `@AiOrchestrated` 标注的模拟业务服务类在零侵入状态下，自动触发脱敏、路由、模型执行与存证，业务方法只专注于自身逻辑。
10. **契约 10: `contract10_aspectReentrancyDefense_preventsStackOverflowErrorOnInternalCalls`**  
    - **验证目标**：验证业务方法内部再次调用标注了 `@AiOrchestrated` 的下游方法时，防重入防卫计数器生效，直接放行 proceed，递归调用深度恒定，零 `StackOverflowError`。

---

## 四、最小实现文件集合与变更边界

### 4.1 新增文件清单 (`backend/qknow-framework/qknow-ai`)
- `tech.qiantong.qknow.ai.pipeline.stage.PipelineStage.java`
- `tech.qiantong.qknow.ai.pipeline.stage.PipelineStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.ModelExecutionCallback.java`
- `tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext.java`
- `tech.qiantong.qknow.ai.pipeline.context.AiPipelineContextHolder.java`
- `tech.qiantong.qknow.ai.pipeline.engine.AiPipelineEngine.java`
- `tech.qiantong.qknow.ai.pipeline.annotation.AiOrchestrated.java`
- `tech.qiantong.qknow.ai.pipeline.aspect.GlobalAiOrchestrationAspect.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.InputGuardrailStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.SlaRoutingStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.ConsensusActivationStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.OutputGuardrailStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.MerkleAuditStageHandler.java`
- `tech.qiantong.qknow.ai.pipeline.stage.impl.CausalGraphStageHandler.java`

### 4.2 测试新增清单 (`backend/tests`)
- `tech.qiantong.qknow.rag.eval.Phase33OrchestrationPipelineContractTest.java`

### 4.3 禁止修改边界
- 严禁修改已冻结且测试通过的 Phase 01 ~ Phase 32 各专属契约测试用例及历史核心算子内部逻辑；
- 严禁引入外部本地模型依赖与 OpenAI 依赖；
- 严禁修改全局 Java 21 SDKMAN 隔离环境。
