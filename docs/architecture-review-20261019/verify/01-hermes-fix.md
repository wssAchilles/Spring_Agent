# Hermes 修复复核报告（提交 f4ff4dda + 当前工作区真实代码）

> 复核员：Hermes 修复复核员（task-7）　|　复核时间：2026-10-20
> 复核基线：`git rev-parse HEAD` = `cb6e185c`（`build(dep): 生产依赖瘦身与测试依赖隔离治理`）**+ 工作区 8 个未提交改动**
> 被复核提交：`f4ff4dda fix(hermes): DeepSeek工具调用协议对齐与取消链路级联治理`（6 文件）
> 纪律声明：本轮**只读**，未修改/删除任何源码、配置、SQL、fixture；未执行 `mvn`/`gradle`/任何测试/任何服务启动（Lead 正在统一编译）。唯一写入文件为本报告。所有"运行时会怎样"的结论均标注【静态推导】或【无法静态判定】，不做运行行为臆测。
> 重要口径：按任务要求以**当前工作区真实代码**裁决，而不是提交信息。经 `git log f4ff4dda..HEAD -- <6 文件>` 与 `git status --porcelain` 核验：**f4ff4dda 的 6 个文件在后续提交中未被再次修改、工作区也没有改动它们**，因此这 6 个文件的工作区内容 = f4ff4dda 落地结果。但工作区另外 8 个文件确实回退了其他提交的修复（见 §2.8、§3），一并裁决。

---

## 0 复核方法

### 0.1 基线核验（先确认"提交"与"工作区"是否一致）

| 核验项 | 命令 | 结果 |
|---|---|---|
| 当前 HEAD | `git log --oneline -8` | `cb6e185c` 为 HEAD；f4ff4dda 位于其后第 3 个历史位（`5d26b814`、`3898471d`、`2f0861a0` 在 f4ff4dda 之后） |
| f4ff4dda 6 文件是否被后续提交再改 | `git log --oneline f4ff4dda..HEAD -- <6 文件>` | 6 个文件输出均为空 → **无后续提交触碰** |
| f4ff4dda 6 文件是否被工作区再改 | `git status --porcelain` | 8 个 `M` 文件均不在 6 文件内 → **提交结果原样保留** |
| 工作区未提交改动清单 | `git status --porcelain` / `git diff --stat` | `.serena/project.yml`、`McpEcosystemContractTest`、4 个 MCP Provider、`CrossKbScoreCalibrator`、`ToolNodeBO`，共 8 个 |

### 0.2 阅读与取证范围

1. 评审侧输入：`docs/architecture-review-20261019/00-MASTER-REVIEW.md` §3.3（P0-07/P0-08）、§4.2（P1-Mem/P1-RAG1）、`01-hermes-cognitive-core.md`（H-P0-1/H-P0-2、H-P1-2、H-P1-7、取消与超时传播章节）、`ARCHITECTURE_DEFECTS_FORENSIC_VERIFICATION_REPORT.md`（对方自证矩阵）。
2. 提交侧：`git show f4ff4dda`（全 284 行 diff）、`git show` 各后续提交对相关文件的改动、`git diff` 全部工作区改动。
3. 生产代码：`DeepSeekCompatibleChatModel`(411 行全读)、`AgentOrchestrator`(关键段 120-600、1024-1130)、`AgentRegistry`(全读)、`MemoryConfiguration`(全读)、`MemoryManager`(全读)、`SleepTimeMemoryAgent`、`ShortTermMemory`(summarize/getContext 段)、`KbConversationController`(send 段 82-190)、`HermesGrpcClient`(全读)、`GrpcReactorBridge`(全读)、`HermesGrpcService`(全读)、`HermesConfiguration`(全读)、`HermesApplication`(全读)、`QKnowApplication`(全读)、`DagCheckpointManager`(关键段)、`ChatModelFactory`(全读)、`ToolNodeBO`/`CrossKbScoreCalibrator`/MCP Provider（工作区现状）。
4. 框架契约（决定 P0-07/P0-08 真伪的关键）：静态阅读依赖源码 jar，不启动任何进程：
   - `~/.m2/repository/org/springframework/ai/spring-ai-model/1.1.0/...-sources.jar`（`AssistantMessage`、`ToolResponseMessage`、`ToolCallingChatOptions`、`MessageAggregator`）
   - `~/.m2/repository/org/springframework/ai/spring-ai-deepseek/1.1.0/...-sources.jar`（`DeepSeekStreamFunctionCallingHelper`，官方流式工具调用合并参考实现）
   - `~/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-agent-framework/1.1.2.2/...-sources.jar`（`AgentLlmNode`、`DefaultBuilder`、`ReactAgent`、`Agent`、`AgentToolNode`）
   - `~/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.1.2.2/...-sources.jar`（`NodeExecutor`、`AsyncNodeActionWithConfig`、`GraphRunner`、`CompiledGraph`）
   - 项目版本锁定：`backend/pom.xml:35-36`（spring-ai 1.1.0 / spring-ai-alibaba 1.1.2.2）。
5. 测试：`DeepSeekCompatibleChatModelContractTest`(205 行)、`GrpcCancelPropagationTest`、`ToolNodeBOTest`、`McpEcosystemContractTest`；只读，不执行。

### 0.3 判定口径

| 判定 | 含义 |
|---|---|
| 已修复 | 原缺陷机制在**生产可达路径**中被消除，且有代码级证据 |
| 部分修复 | 缺陷的一部分被消除，但完整闭环仍不成立，或仅覆盖非生产路径 |
| 未修复 | 原缺陷机制仍在，或修复被后续改动/守卫抵消 |
| 新增回归 | 修复引入新的错误行为，或工作区回退了已提交的修复 |
| 无法静态判定 | 结论取决于运行时/容器/外部服务行为，纯静态阅读不能裁决 |

---

## 1 逐项裁决表

| 编号 | 复核子项 | 判定 | 关键证据（工作区当前行号） | 残留风险 |
|---|---|---|---|---|
| P0-07-a | 标准 `ToolCallingChatOptions.toolCallbacks` → `tools` 请求体 | **已修复**（静态） | `DeepSeekCompatibleChatModel.java:190-216`；框架侧 `AgentLlmNode.java:333-373,489-496`（copy options、`setInternalToolExecutionEnabled(false)`、`promptSpec.options(...)`） | 无单测覆盖；`getToolNames()` 未映射；`tool_choice` 未发送 |
| P0-07-b | 非流式响应构造 `AssistantMessage.ToolCall` | **已修复**（静态） | `DeepSeekCompatibleChatModel.java:254-265`、`:398-410`；`AssistantMessage.Builder.toolCalls` 存在（spring-ai-model 1.1.0 `AssistantMessage.java:127-129`）；框架路由 `ReactAgent.java:787-790,806-812` 依赖 `hasToolCalls()` | 该路径在生产 `agent.stream()` 中不执行（生产走流式） |
| P0-07-c | 流式 `tool_calls` delta 跨 chunk 累积 | **未修复** | ① 空片段守卫先于提取：`DeepSeekCompatibleChatModel.java:323-326` 在 `:332` 提取 tool_calls **之前** `return null`；② client 不做按 `index` 的参数拼接（`:398-410` 仅逐 chunk 构造）；③ 框架 `NodeExecutor.java:270` 的 `mergeToolCalls` 按 id **替换**而非拼接（`NodeExecutor.java:390-407`）；④ 官方参考实现明确必须拼接参数（spring-ai-deepseek `DeepSeekStreamFunctionCallingHelper.java:119-141`） | **生产流式 ReAct 工具调用仍不可用**；请求侧已发送 tools，存在"模型要调工具→消息被丢弃→空回答"回归风险（§3 R1） |
| P0-07-d | tool 消息回传 `tool_call_id` / 工具结果内容 | **未修复**（非本次改动，但 P0-07 闭环必需） | `DeepSeekCompatibleChatModel.java:149-154` 只读 `message.getMetadata().get("tool_call_id")`；框架 `ToolResponseMessage` 构造为 `super(TOOL, "", metadata)`（spring-ai-model `ToolResponseMessage.java:36`），结果在 `responses`（`:44,73`），metadata 只有 `messageType`（`AbstractMessage.java:74-88`）→ 生产请求实际得到 `{"role":"tool","content":""}`（无 id、无结果）；且未把多响应拆成多条 tool 消息 | 多轮工具消息在标准 OpenAI/DeepSeek 协议下会被 400 拒绝或结果丢失 |
| P0-07-e | thinking 模式与 tools 是否冲突 | **无法静态判定**（且生产不可达） | thinking 只在 `options instanceof DeepSeekChatOptions` 时注入（`:173-189`）；`DeepSeekChatOptions` 的生产构造点仅 `mor/AdaptiveThinkingGovernor`/`ReasoningDecision`，全仓 main 无接线；生产图传的是 `ToolCallingChatOptions`；thinking 开启时温度被抑制（`:222-231`） | 运行期官方是否允许"thinking+tools"组合未验证；top_p 完全未发送 |
| **P0-07 整体** | DeepSeek 工具调用协议对齐 | **部分修复** | 请求侧已修复；非流式已修复；**流式（生产默认路径）与 tool 结果回传未修复** | 见 R1、§4 |
| P0-08-a | 控制面保存上游 `Disposable` 并在 `doOnCancel` dispose | **已修复**（静态，触发前提存疑） | `KbConversationController.java:125`（保存 `upstreamSub`）、`:181-186`（`doOnCancel → dispose`）；下游 `HermesGrpcClient.java:93-99`（`emitter.onDispose → call.cancel`） | Spring MVC(servlet) 下 SSE 断连是否触发 `doOnCancel` **无法静态判定**；取消后无落库（§3 R2） |
| P0-08-b | gRPC 服务端取消桥 `setOnCancelHandler → subscription.dispose()` | **部分修复/结构性风险** | `GrpcReactorBridge.java:50` 先阻塞 `subscribe`，`:72-77` 才注册 `setOnCancelHandler`；框架图执行是同步阻塞的（`AsyncNodeActionWithConfig.java:36-47` 同步 `complete`，`NodeExecutor.java:135-160` `Mono.fromFuture`，`GraphRunner.java:48-59` 无 `subscribeOn`；`AgentLlmNode.java:213` 的 `ChatModel.stream` 内部由 `Flux.create` 阻塞读 HTTP） | 流期间取消处理器可能尚未注册 → 客户端 RST_STREAM 不会 dispose 上游 |
| P0-08-c | Orchestrator `Flux.create` 注册 `onDispose`、保存内层 `Disposable`、释放并发闸门 | **部分修复（代码在、时序失效）** | `AgentOrchestrator.java:152` 外层 `Flux.create`；`:464-466` `stream.subscribe(...)` 取 Disposable；`:550-555` 才 `emitter.onDispose(...)`；`:452-460` 闸门；`:523-549` error/complete 释放 | 同步阻塞模型下 `subscribe` 直到生成结束才返回 → `onDispose` 在流期间无法注册 → 内层订阅不能在断连时 dispose，`ACTIVE_REACT_RUNS` 只能延迟到流结束释放【静态推导】 |
| P0-08-d | DeepSeek 流循环检查 `sink.isCancelled()` 并关闭 HTTP body | **部分修复** | `DeepSeekCompatibleChatModel.java:102-110`：`while (iterator.hasNext() && !sink.isCancelled())`；`:108-110` 取消时跳过 `complete` | ① 判断顺序先 `hasNext()`（阻塞）后 `isCancelled()`，即便触发也要等下一行 SSE；② 未注册 `sink.onDispose` 主动关闭 body/中断读取；③ 由于 (b)(c) 上游无法 dispose，`isCancelled()` 在实际断连场景**大概率永远为 false** |
| **P0-08 整体** | 三层取消级联 | **部分修复（未闭环）** | 控制面/客户端代码补齐，Hermes 内部在同步阻塞执行模型下取消不可达 | Token 空烧"跑到自然结束/120s"的核心症状**未被静态证明消除** |
| P1-Mem-a | `MemoryManager.onConversationEnd` 摘要逻辑 | **未修复** | `MemoryManager.java:51` `summarize(sessionId,5)`；`:53-54` `context.get(0).getText()`；`ShortTermMemory.java:111-114` 在 `size<=5` 时**直接 return 不生成摘要** → 仍把最旧一条原始消息当摘要 | 生产可达：`SleepTimeMemoryAgent.java:36-37`（默认 enabled=true）+ `:88` 调 `onConversationEnd`，且 `HermesApplication.java:47-53` 用 `CommandLineRunner` 每次启动强制执行一次 |
| P1-Mem-b | embedding 硬编码 `text-embedding-v2` / `TONGYI_API_KEY` 无默认 | **部分修复** | `MemoryConfiguration.java:28` 默认值 `TONGYI_API_KEY:${ai.tongyi.api-key:default_dashscope_key}`；`:32-35` `text-embedding-v3` + `dimensions(1536)` | 默认值是假占位 key → 配置错误从"启动失败"变为"运行期 401"；"控制面 DB 模型 vs Hermes 硬编码"双轨仍在；v3 是否接受 1536 维需运行验证 |
| P1-RAG1 | 双 CRAG（Hermes 每轮额外阻塞 LLM 复评） | **部分修复** | `AgentOrchestrator.java:270-281`：改为 `Boolean.getBoolean("hermes.crag.secondary.enabled")` 开关、默认 false；全仓仅此一处引用该 key | 用 **JVM 系统属性**而非 Spring 配置（`application.yml` 无效）；未实现评审建议的"控制面已评估标签/前置条件"；`RetrievalEvaluator` 仍被注入（可被 `-D` 重新打开） |
| REG | `AgentRegistry` 加 `@Component`/无参构造；`HermesConfiguration` 加 `AgentRegistry`、`DagCheckpointManager` Bean | **装配性改动（非能力修复）** | `AgentRegistry.java:18,27-29`；`HermesConfiguration.java:35-46`；`AgentRegistry.java:50-53,83-85`（默认 3 个 WorkerAgent + `createSupervisor`） | 全仓 main 代码**除 HermesConfiguration 外无任何 AgentRegistry 消费者**（grep 仅命中定义与配置类）→ 多智能体仍未接入生产聊天链；控制面 `ChatModel` 无 `@Bean`，`getIfAvailable()` 静态推导为 null；`DagCheckpointManager` Bean 补齐后恢复功能仍受 P0-11 语义错误影响（非本任务范围） |

**汇总（顶层 5 项）**：已修复 0；部分修复 4（P0-07、P0-08、P1-Mem、P1-RAG1）；未修复 1（P1-Mem 摘要逻辑；其中 P0-07 的流式与 tool 回传同为未修复子项）；装配性改动 1（AgentRegistry/HermesConfiguration，安全但无生产效果）。

---

## 2 关键证据与代码走查

### 2.1 P0-07 请求侧：本次修复的真实链路（成立）

生产模型选择：控制面 gRPC → `AgentOrchestrator.executeAgent` → `ChatModelFactory.getChatModel(...)`；`platform=DeepSeek` 时返回 `DeepSeekCompatibleChatModel`（`ChatModelFactory.java:69-76,120-126`）。图侧：`ReactAgent.builder()...tools(guardedTools).toolNames(toolNames).resolver(...)`（`AgentOrchestrator.java:441-449`），`AgentLlmNode.buildChatOptions` 生成**标准** `ToolCallingChatOptions` 并 `setInternalToolExecutionEnabled(false)`（`AgentLlmNode.java:333-373`），`buildChatClientRequestSpec` 再把 copy 后的 options 挂到 `promptSpec.options(...)`（`AgentLlmNode.java:489-519`）。因此 `Prompt.getOptions()` 携带非空 `toolCallbacks`，命中 `DeepSeekCompatibleChatModel.java:190-216` 分支，`tools` 被写入请求体（`{type:function, function:{name, description, parameters}}`）。**这是原缺陷（全仓 `.toolCalls(` 0 命中、tools 不发送）被真实消除的部分，属生产路径**，但无对应单测（见 R7）。

### 2.2 P0-07 流式：修复被"空片段守卫"整包丢弃（关键失效点）

`parseStreamChunk` 的执行顺序（`DeepSeekCompatibleChatModel.java`）：

1. `:317-319` 取 `content`/`reasoning_content`/`finish_reason`；`:320-321` 取 usage；
2. **`:323-326`：四者皆空则 `return null`**；
3. `:328-335` 才把 `tool_calls` 放进 metadata；
4. `:332`/`:340-342` 才 `extractToolCalls(delta)` 并 `assistantMessageBuilder.toolCalls(...)`。

在 DeepSeek（OpenAI 兼容）流式协议中，工具调用 delta 的典型形态是：首帧 `delta.tool_calls=[{index,id,type,function:{name,arguments:""}}]`、后续帧仅 `{index,function:{arguments:"片段"}}`，`content`/`reasoning_content`/`finish_reason` 均为空。这类帧会在步骤 2 被**整体丢弃**，步骤 3/4 的"构造 ToolCall"代码对它们不可达。即使某厂商把参数与 `finish_reason` 合并到同帧，客户端也没有按 `index` 跨帧拼接参数（`:398-410` 无累积状态），而框架 graph-core 的聚合 `mergeToolCalls` 语义是"同 id 用当前帧**替换**、不同 id 追加"（`NodeExecutor.java:390-407`），**不会拼接 arguments**；对照 spring-ai-deepseek 官方实现 `DeepSeekStreamFunctionCallingHelper.java:119-141`，其 `merge` 明确做 `arguments.append(previous).append(current)` 并保留 `id/name` 缺省继承——本项目客户端缺的正是这一层。生产 `AgentOrchestrator` 用的正是 `agent.stream(messages)`（`:464`），图默认流式（`AgentLlmNode.java:204` `_stream_` 缺省 true），所以**生产链路仍不通**。

连带风险：请求侧现在已真正携带 `tools`（§2.1），模型很可能选择调用知识库伪工具；其 tool_calls 帧被丢弃后，聚合消息 `hasToolCalls()==false`，`ReactAgent.makeModelToTools`（`ReactAgent.java:787-790`）走向结束节点，最终 `content` 为空 → 控制面兜底文案"暂未生成文本回答"（`KbConversationController.java:160-179`）。这构成"修复反而让回答变差"的回归风险（R1）。

### 2.3 P0-07 tool 消息回传：修复未覆盖，且旧测试是自证

`buildRequestBody` 对 TOOL 消息仅做两件事（`:148-154`）：`role=tool`、`content=message.getText()`（恒 `""`）、`tool_call_id` 取 `metadata`。但框架 `ToolResponseMessage` 的真实结构是：`super(MessageType.TOOL, "", metadata)`（`ToolResponseMessage.java:36`），工具结果在 `getResponses()` 的 `ToolResponse(id,name,responseData)` 记录里（`:44,73`），metadata 不含 `tool_call_id`（`AbstractMessage.java:74-88` 只注入 `messageType`）。`AgentToolNode` 构建该消息时也未写入该 metadata（`AgentToolNode.java:238-243`）。因此若进入工具第二轮，请求体会出现 `{"role":"tool","content":""}`：缺 `tool_call_id`、缺工具返回内容、且多工具结果未拆成多条消息——标准协议下会 HTTP 400，或模型看不到结果。

更严重的是既有测试**掩盖**了这一点：`DeepSeekCompatibleChatModelContractTest.java:61-77` 用匿名 `Message` 手写 `getText()="工具结果"`、`getMetadata()={tool_call_id:call_123}`，`:90-94` 断言通过；而真实框架消息不具备这些字段。`契约4` 只断言 `metadata.get("tool_calls")`（`:127-172`），从不检查 `AssistantMessage.getToolCalls()/hasToolCalls()`；`契约5`（`:175-198`）只覆盖"仅 reasoning_content 的思考帧"，没有"仅 tool_calls 帧"用例。f4ff4dda 未新增任何测试，因此上述失效点无回归门禁（R7）。

### 2.4 P0-08 取消级联：代码补齐，但同步阻塞执行模型让级联失效【静态推导】

逐层走查：

- **第 1 层（控制面控制器）**：`KbConversationController.java:125` 保存 `agentConfigService.chatMessage(...)` 的订阅；`:181-186` 的 `doOnCancel` 置位并 `dispose`。下游 `HermesGrpcClient.java:63-103` 是 `Flux.create`，`:93-99` `onDispose → call.cancel("client disposed")`。该层代码正确；但触发条件是 Spring MVC（servlet）SSE 客户端断开时是否取消返回的 Flux——**无法静态判定**。
- **第 2 层（gRPC 服务端桥）**：`HermesGrpcService.java:37-49` 把 `agentOrchestrator.chat(...)` 交给 `GrpcReactorBridge.bindStream`；后者 `:50` 同步 `monitoredFlux.subscribe(...)`，`:72-77` 才 `setOnCancelHandler`。框架执行链是**同步阻塞**的：`GraphRunner.java:48-59` 只 `Flux.defer`，`NodeExecutor.java:135-160` 的节点动作返回已完成的 `CompletableFuture`（`AsyncNodeActionWithConfig.java:36-47` 同步 `complete`），`NodeExecutor.java` 内嵌 Flux 处理无 `subscribeOn`（仅并行分支 `:728-745` 使用 scheduler）；而 `AgentLlmNode.java:213` 的 `chatModel.stream(...)` 最终是 `DeepSeekCompatibleChatModel.streamResponse` 在 `Flux.create` 消费者里**阻塞读 HTTP**（`:97-110`）。因此 `:50` 的 subscribe 会在整轮生成期间阻塞，`:72` 的取消处理器在流进行中根本无法注册 → 客户端 RST_STREAM 不会触发 `subscription.dispose()`。
- **第 3 层（Orchestrator）**：`AgentOrchestrator.java:464-466` 的 `stream.subscribe(...)` 同样在上面这套同步模型里阻塞；`:550-555` 的 `emitter.onDispose(...)` 在 subscribe 返回（≈生成结束）后才注册 → 不能在断连时 dispose 内层订阅；`:452-460` 的 `ACTIVE_REACT_RUNS` 闸门也只能在流自然结束（或 error/complete）后释放，取消路径的实际释放时机被推迟。可见部分虽然"有代码"，但**未闭环**。
- **第 4 层（DeepSeek 流循环）**：`:102-110` 增加了 `sink.isCancelled()` 与取消时跳过 `complete`，方向正确；但由于第 2/3 层无法在流期间 dispose，该 sink 在实际断连场景中**不会被置为 cancelled**，检查大概率不生效；即便生效，`while (iterator.hasNext() && !sink.isCancelled())` 先阻塞取下一行再判断取消，且没有 `sink.onDispose(...)` 主动关闭 `HttpResponse` body 的钩子，`try-with-resources` 只在循环退出时生效 → "立即关闭连接"的验收口径（00 报告 §7 Wave0 第 6 项：断连后 1s 内取消、无后续 token）**静态上无法成立**。

需强调：以上时序结论来自框架源码静态推演（属高置信静态推导，未做运行时验证）；若图执行实际被调度到其他线程（当前代码未见依据），结论需运行验证推翻。验证方法见 §4。

### 2.5 P1-Mem：摘要缺陷原样保留，embedding 局部修复

- **摘要**：`MemoryManager.java:51` 调 `shortTerm.summarize(sessionId,5)`；`ShortTermMemory.java:111-114` 在 `context.size() <= 5` 时**直接 return**，不生成摘要；随后 `MemoryManager.java:53-54` 取 `getContext(sessionId, Integer.MAX_VALUE).get(0).getText()`。`getContext` 返回的是按插入顺序的列表（`ShortTermMemory.java:57-68`），`get(0)` 即**最旧消息** → "把最旧一条原始消息当摘要写长期记忆"的缺陷**未修复**。生产可达性反而很强：`SleepTimeMemoryAgent` 默认 `enabled=true`（`:36-37`），`:88` 调 `onConversationEnd`；`HermesApplication.java:47-53` 还注册了 `CommandLineRunner` 在每次启动时强制执行一次。控制面 dev 配置里的 `hermes.memory.sleep-agent.enabled: false`（`qknow-server/src/main/resources/application-dev.yml:158-160`）只影响控制面进程，Hermes 服务自己的 `application.yml` 未覆盖该开关。
- **embedding**：`MemoryConfiguration.java:32-35` 已从 `text-embedding-v2` 改为 `text-embedding-v3` + `dimensions(1536)`，与仓库自身基线 `EmbeddingDimensionGuard.REQUIRED_DIMENSION=1536/DEFAULT_MODEL_NAME=text-embedding-v3`（`backend/qknow-module-kmc/.../EmbeddingDimensionGuard.java:29-30`）一致；`PgVectorStore.builder(jdbcTemplate, embeddingModel).build()`（`:40-42`）会从 embedding model 推导维度（Spring AI pgvector 1.1.0 `PgVectorStore.java:487-510`）。这部分可判**部分修复**。残留：`TONGYI_API_KEY` 默认值是假占位 `default_dashscope_key`（`:28`），仅避免启动失败，配置缺失会推迟为运行期 401；"控制面 DB 模型配置 vs Hermes 硬编码模型"双轨未消除（原缺陷第二条）。

### 2.6 P1-RAG1：默认绕过成立，但开关实现不合契约

`AgentOrchestrator.java:270-281`：`RetrievalEvaluation` 默认 null，`hermes.crag.secondary.enabled` 为 false 时不再调用 `retrievalEvaluator.evaluate(...)`；`buildRagContextBlock`/`appendRetrievalEvaluation` 全部 null 安全（`:1024-1030,1056-1060`），不会 NPE。所以"双 CRAG 每轮额外阻塞 LLM"在默认配置下被消除（功能上等价于默认关闭）。但：
1. 用 `Boolean.getBoolean(...)`（JVM `-D` 系统属性）而非 `@Value`/`@ConfigurationProperties`，`application.yml`/环境变量配置无效，全仓仅有 `AgentOrchestrator.java:273` 一处引用，运维无法按环境开关；
2. 评审建议的"仅当 `hermes.crag.enabled=true` 且控制面未标记已评估时执行"（01 报告 §6 建议 5 与 H-P1-2）未实现——现在是**全局开关**，而不是"前置标签/条件判断"；
3. `RetrievalEvaluator` 仍作为构造依赖注入（`:71,102-113`），代码路径与配置语义分裂。

### 2.7 AgentRegistry / HermesConfiguration：装配性改动，不是能力接线

- `AgentRegistry` 新增 `@Component`（`AgentRegistry.java:18`）与无参构造（`:27-29`，`this(null,(ChatModel)null,null)`）；构造链 `:50-53` → `initDefaultAgents`（`:58-85`）创建 RAG/Search/Code 三个 `WorkerAgent`，但 `createSupervisor`/`getAgent`/`getWorkers` 在**全仓 main 代码中无任何消费者**（`grep -rn "AgentRegistry"` 仅命中定义文件与 `HermesConfiguration.java:43-46`）。控制面 `QKnowApplication.java:22-27` 的 `@ComponentScan` 仍以正则排除 `tech.qiantong.qknow.hermes.*`，因此 `@Component` 只在 Hermes 服务生效；控制面靠 `HermesConfiguration.java:41-46` 显式 `@Bean`（传 `null` config、`ObjectProvider<ChatModel>.getIfAvailable()`、null toolDiscovery）。静态检索控制面模块**没有 `@Bean ChatModel` 定义**，故该 Bean 内 3 个 WorkerAgent 的 `chatModel` 静态推导为 null，且无人调用 → **安全但无实际功能**，多智能体仍未进入生产聊天链（P0-15 不因本提交改变）。
- `HermesConfiguration.java:35-39` 新增 `DagCheckpointManager` Bean（P0-10 的方向性修复），`new` 出来的实例仍会经过容器后处理（`@Autowired(required=false) JdbcTemplate` 可注入、`@PostConstruct init()` 会执行，`DagCheckpointManager.java:29-38`）。方法体内 `ensureTableExists()` 自捕获异常（`:495-528`），不会直接炸启动；主库为 PostgreSQL（`application-dev.yml:5-6,100-106`），`ON CONFLICT`/`ADD COLUMN IF NOT EXISTS` 语法兼容。但该 Bean 的功能正确性（P0-10/P0-11）不在本复核裁决范围；且启动期执行 DDL 属新增副作用，建议在部署冒烟中确认（§4）。

### 2.8 工作区回退清单（"提交修了、工作区又改回去"）

以下均为**工作区现状 vs HEAD** 的逐行核对（`git diff` 为证），与 f4ff4dda 无关，但按任务要求必须指出：

| 文件 | 已被哪个提交修复 | HEAD 行为 | 工作区现状（当前行号） | 性质 |
|---|---|---|---|---|
| `backend/qknow-module-kb/.../flow/bo/ToolNodeBO.java` | `2f0861a0`（fix(flow)） | adapter 为 null → 返回 `NodeRunResultBO.failure(...)`，不伪造成功 | `:132-135` adapter 为 null 时构造 `{"status":"SUCCESS",...,"message":"Executed in standalone mock mode"}` → 重新出现"工具节点静默成功"假成功 | **提交修复被回退（P1-Tool2 复活）** |
| `backend/qknow-mcp/.../provider/KnowledgeBaseMcpProvider.java` 等 4 个 Provider | `5d26b814`（fix(mcp)） | 未接入真实委托时返回 `CallToolResult.error(...)`（客观报错） | `KnowledgeBaseMcpProvider.java:96-97`、`KnowledgeGraphMcpProvider.java:79-80`、`DataAgentMcpProvider.java:112-113`、`CodeSandboxMcpProvider.java:77-78` 全部改为返回 `"status=SUCCESS"` 文本（无真实执行） | **提交修复被回退（P0-MCP2 假数据复活，换了一层"契约模式"包装）** |
| `backend/qknow-module-kb/.../retrieval/CrossKbScoreCalibrator.java` | `3898471d`（fix(rag)） | `scoreFloor` 默认 `0.0D`，不再误杀 RRF 分数 | `:33-34` 默认回退 `0.40D`；`:104` 用 `score > 0.1D` 魔法阈值区分 RRF/余弦 | **提交修复被部分回退（P0-14 复活/降级为启发式）** |
| `backend/qknow-mcp/qknow-mcp-client/src/test/.../McpEcosystemContractTest.java` | `5d26b814` | 路由延迟断言 ≤10ms | `:176-191` 增加 3 次预热循环、断言放宽到 ≤15ms | **测试断言放宽**（违反"不得根据首次结果放宽契约阈值"的取证纪律方向） |
| `.serena/project.yml` | 无 | — | 工具配置噪声（语言服务器列表/REPL 字段），与缺陷无关 | 无关改动，建议提交前剔除 |

**结论**：f4ff4dda 自身 6 文件未被回退；但同批"6 个修复提交"中的另外 3 个提交的关键修复被工作区重新改回，工作区现状不可作为"修复已交付"的证据。

---

## 3 新增风险与回归

| ID | 风险 | 类型 | 证据与机理 | 严重度 |
|---|---|---|---|---|
| R1 | 请求侧已发 `tools`，但流式 tool_calls 帧被丢弃 → 模型"打算调工具"时返回空内容，用户得到兜底文案 | **新增回归风险【静态推导】** | §2.2；`:190-216` + `:323-326`；`ReactAgent.java:787-790` | 高（直接影响问答可用性，且可能比修复前更差） |
| R2 | 取消路径不再等待上游完成 → 控制器 `subscribe` 的 `onComplete` 不触发 → assistant 消息永久停留在"思考中"，部分答案丢失 | **新增回归【高置信静态推导】** | `KbConversationController.java:125-186`（取消分支无 `updateAssistantMessage`）；`:160-179` 只在正常完成落库；无超时清理任务 | 中高（数据一致性/UX） |
| R3 | 工作区回退 `ToolNodeBO` 假成功 | **新增回归** | §2.8；`ToolNodeBO.java:132-135`；`ToolNodeBOTest` 未覆盖 adapter=null 分支 | 高（工作流"成功"不可信） |
| R4 | 工作区回退 4 个 MCP Provider 为伪造 SUCCESS | **新增回归** | §2.8；四个 Provider 当前行号见表；`McpToolAdapter.executeTool` 返回的"成功"无真实执行 | 高（对外能力失真，P0-MCP2 复活） |
| R5 | 工作区回退 `scoreFloor=0.40` 并用 `score>0.1` 魔法阈值区分量纲 | **新增回归/部分回退** | `CrossKbScoreCalibrator.java:33-34,104`；RRF 分数（≈0.016~0.065）因 <0.1 不被过滤，但真实余弦分 [0.1,0.4) 会被误杀；量纲判定靠经验阈值 | 中（检索质量波动，需 RAG 复核员交叉裁决） |
| R6 | 契约测试放宽路由延迟断言（10→15ms + 预热） | **新增回归（工程纪律）** | `McpEcosystemContractTest.java:176-191` | 中（性能契约弱化，掩盖 CI 抖动） |
| R7 | f4ff4dda 无任何测试新增；既有 DeepSeek 契约测试用匿名 Message 自证 tool 回传、只断言 metadata、不覆盖 tool-only 流帧 | **测试自证风险** | `DeepSeekCompatibleChatModelContractTest.java:61-94,127-172,175-198`；`git show f4ff4dda --stat` 无测试文件 | 高（修复无门禁，后续极易再次漂移） |
| R8 | `HermesConfiguration` 新 Bean：控制面 `AgentRegistry` 空壳（chatModel 静态推导为 null）+ `DagCheckpointManager` 启动期执行 DDL | 轻微副作用 | `HermesConfiguration.java:35-46`；`DagCheckpointManager.java:29-38,495-528`；`QKnowApplication.java:22-27` | 低（安全，但增加启动副作用与误导性装配） |

---

## 4 未能静态判定项与建议验证

| # | 无法静态判定项 | 原因 | 建议验证（交由 Lead/后续授权者执行；本复核未运行） |
|---|---|---|---|
| V1 | Spring MVC(servlet) SSE 客户端断开是否触发 `doOnCancel` | 取决于 Servlet 容器异步错误/写失败传播，静态不可证 | 用 `MockMvc`+异步分发或真实浏览器断开做集成测试，断言 `upstreamSub.isDisposed()` 与 `clientConnected=false` |
| V2 | Hermes 内层取消是否真正生效（V2 是本报告最关键待验证项） | ① 图执行是否确实同步阻塞（本报告由框架源码推导，高置信）；② gRPC 取消处理器注册时序 | 写一个"阻塞型 Fake ChatModel"（`Flux.create` 内 `Thread.sleep` 逐块发 `ChatResponse`，并 `sink.onDispose` 打点）→ 走 `AgentOrchestrator.chat` + `GrpcReactorBridge`，取消后断言 `onDispose` 在 1s 内触发、`ACTIVE_REACT_RUNS` 归零；再在 `DeepSeekCompatibleChatModel` 前挂本地假 HTTP SSE 服务器，断言连接被关闭 |
| V3 | DeepSeek 真实流式 `tool_calls` 分片形态（id 是否只在首帧、arguments 是否分帧） | 外部服务行为，只有官方文档/实测能定 | 用本地 fake SSE server 按 OpenAI/DeepSeek 文档回放 `tool_calls` 分片，跑 `DeepSeekCompatibleChatModelContractTest`（新增用例）断言聚合后 `getToolCalls()` 参数完整 |
| V4 | thinking 模式与 tools 组合的官方允许性 | DeepSeek 官方文档为准，且当前生产不可达 | 先按官方文档核对；再用 `DeepSeekChatOptions`+tools 做真实 API 冒烟（需授权 Key 与预算） |
| V5 | `text-embedding-v3` 是否接受 `dimensions=1536`（DashScope 兼容模式） | 外部模型能力，需实测 | Hermes 服务启动后调用一次 embedding（或运行现有 embedding 冒烟），断言返回 1536 维且范数≈1；不通过则改用 v4 或调整维度 |
| V6 | PgVectorStore 建表维度与 embedding 维度一致性 | 依赖运行期 `embeddingModel.dimensions()` 与既有 `vector_store` 表 | 启动后检查 `vector_store` 表 DDL 与 `EmbeddingDimensionGuard` 启动探测日志 |
| V7 | `SleepTimeMemoryAgent` 在 Hermes 服务的生产可达性（Redis 是否注入） | `IRedisService` 是否可用取决于 Hermes 部署的 Redis 配置 | 启动 Hermes，观察 `CommandLineRunner` 日志与 `memory:short:*` 键；或对 `ShortTermMemory.listSessionIds` 注入 mock 做单测 |
| V8 | AgentRegistry 是否真的实例化多智能体 | 需要 Spring 上下文测试确认 Bean 与 `@Component` 是否双注册/是否被消费 | `@SpringBootTest` 断言控制面只有 1 个 AgentRegistry Bean 且无生产调用方；架构测试断言聊天链不引用 AgentRegistry |
| V9 | `DagCheckpointManager` Bean 补齐后恢复链路是否可用 | 属 P0-10/P0-11 语义修复，需 flow 复核员裁决 | 运行 `KbFlowServiceImpl` 恢复测试（若存在）或构造失败工作流做 resume 冒烟 |

---

### 附录 证据索引

> 全部为工作区当前内容；依赖源码证据标注 sources jar 路径。行号以 2026-10-20 工作区为准。

| ID | 证据位置 | 证明要点 |
|---|---|---|
| E01 | `git log --oneline f4ff4dda..HEAD -- <6 文件>` 输出为空 | f4ff4dda 6 文件未被后续提交修改 |
| E02 | `git status --porcelain` 8 个 `M` 文件 | 6 文件不在其中；工作区另有 8 个未提交改动 |
| E03 | `backend/qknow-framework/qknow-ai/.../deepseek/DeepSeekCompatibleChatModel.java:190-216` | `ToolCallingChatOptions.toolCallbacks → tools` 映射已实现 |
| E04 | 同上 `:254-265`、`:398-410` | 非流式构造 `AssistantMessage.ToolCall` |
| E05 | 同上 `:323-326` vs `:332-343` | **空片段守卫先于 tool_calls 提取 → 流式 tool 帧被整包丢弃** |
| E06 | 同上 `:148-154` | tool 消息只从 metadata 取 `tool_call_id`、content 取 `getText()` |
| E07 | 同上 `:102-110` | `isCancelled()` 检查顺序与无 body 关闭钩子 |
| E08 | 同上 `:173-189`、`:222-231` | thinking 注入条件与温度抑制 |
| E09 | `backend/qknow-hermes/.../agent/AgentOrchestrator.java:270-281` | 双 CRAG 改为系统属性开关、默认跳过 |
| E10 | 同上 `:452-460`、`:464-466`、`:550-555` | 并发闸门、内层订阅 Disposable、`onDispose` 注册位置 |
| E11 | `backend/qknow-module-kb/.../controller/admin/conversation/KbConversationController.java:125`、`:181-186` | 控制面保存 Disposable 并 dispose |
| E12 | `backend/qknow-module-kb/.../service/agent/HermesGrpcClient.java:63-103`（`:93-99`） | gRPC 客户端 `onDispose → call.cancel` |
| E13 | `backend/qknow-hermes/.../grpc/GrpcReactorBridge.java:50`、`:72-77` | 先阻塞 subscribe、后注册取消处理器 |
| E14 | `backend/qknow-hermes/.../grpc/HermesGrpcService.java:37-49` | 编排 Flux 交由桥接 |
| E15 | `backend/qknow-hermes/.../memory/MemoryConfiguration.java:24-36,40-42` | embedding v3+1536、默认 key、PgVectorStore |
| E16 | `backend/qknow-hermes/.../memory/MemoryManager.java:50-54` | `summarize` 后取 `context.get(0)` 作摘要 |
| E17 | `backend/qknow-hermes/.../memory/ShortTermMemory.java:106-127` | `size<=5` 直接 return，不生成摘要 |
| E18 | `backend/qknow-hermes/.../memory/SleepTimeMemoryAgent.java:36-37,88`；`.../HermesApplication.java:47-53` | 记忆缺陷生产可达（默认启用 + 启动强制执行） |
| E19 | `backend/qknow-hermes/.../agent/AgentRegistry.java:18,27-29,50-53,83-85` | `@Component`/无参构造/默认三个 WorkerAgent |
| E20 | `backend/qknow-server/.../config/HermesConfiguration.java:35-46` | `DagCheckpointManager` 与 `AgentRegistry` Bean |
| E21 | `backend/qknow-server/.../QKnowApplication.java:22-27` | 控制面排除 `hermes.*` 扫描 |
| E22 | `backend/qknow-server/.../config/.../application-dev.yml:5-6,100-106,158-160` | 主库 PostgreSQL；控制面 sleep-agent 关闭 |
| E23 | `deploy/docker/docker-compose-qknow.yml:69-87` | hermes 服务存在并注入 `TONGYI_API_KEY`（空值风险） |
| E24 | spring-ai-model 1.1.0 `AssistantMessage.java:59-66,127-129` | `hasToolCalls()`/`Builder.toolCalls(List)` 契约 |
| E25 | spring-ai-model 1.1.0 `ToolResponseMessage.java:36,44,73`；`AbstractMessage.java:74-88` | TOOL 消息 textContent=""、结果在 responses、metadata 无 tool_call_id |
| E26 | spring-ai-model 1.1.0 `MessageAggregator.java:107,135-139,160-168` | 框架聚合对 toolCalls 仅 `addAll`，无参数拼接 |
| E27 | spring-ai-deepseek 1.1.0 `DeepSeekStreamFunctionCallingHelper.java:88-116,119-141` | 官方参考实现按 id 继承 + arguments 拼接（本项目缺失） |
| E28 | agent-framework 1.1.2.2 `AgentLlmNode.java:333-373,489-519` | 图侧生成标准 `ToolCallingChatOptions` 并关闭内部执行 → 请求侧分支可达 |
| E29 | agent-framework 1.1.2.2 `AgentLlmNode.java:204,213` | 默认 `_stream_`=true，生产走流式 |
| E30 | agent-framework 1.1.2.2 `ReactAgent.java:787-790,806-812` | 工具路由完全依赖 `hasToolCalls()` |
| E31 | graph-core 1.1.2.2 `NodeExecutor.java:270,347-352,390-407` | 流式聚合把 toolCalls 合并进最终 AssistantMessage；`mergeToolCalls` 为替换语义 |
| E32 | graph-core 1.1.2.2 `AsyncNodeActionWithConfig.java:36-47`、`NodeExecutor.java:135-160`、`GraphRunner.java:48-59`、`CompiledGraph.java:549-557,578-599` | 图执行同步阻塞、无 `subscribeOn` → 取消处理器注册滞后【静态推导】 |
| E33 | `backend/tests/.../ai/deepseek/DeepSeekCompatibleChatModelContractTest.java:61-94,127-172,175-198` | 既有测试用匿名 Message 自证、只断言 metadata、无 tool-only 流帧用例 |
| E34 | `backend/tests/.../hermes/grpc/GrpcCancelPropagationTest.java:94-120` | 取消测试源为 `Flux.interval`（异步），不覆盖生产同步阻塞源 |
| E35 | `git diff`：`ToolNodeBO.java:132-135`（工作区）vs HEAD failure | ToolNodeBO 假成功回退（2f0861a0 被回退） |
| E36 | `git diff`：`KnowledgeBaseMcpProvider.java:96-97`、`KnowledgeGraphMcpProvider.java:79-80`、`DataAgentMcpProvider.java:112-113`、`CodeSandboxMcpProvider.java:77-78`（工作区）vs HEAD `CallToolResult.error` | 4 个 Provider 假 SUCCESS 回退（5d26b814 被回退） |
| E37 | `CrossKbScoreCalibrator.java:33-34,104`（工作区）vs HEAD 默认 0.0 | scoreFloor 0.40 回退（3898471d 被回退） |
| E38 | `McpEcosystemContractTest.java:176-191`（工作区） | 路由延迟断言 10→15ms 放宽 |
| E39 | `backend/pom.xml:35-36` | spring-ai 1.1.0 / spring-ai-alibaba 1.1.2.2 版本锁定 |
| E40 | `git show f4ff4dda --stat`（6 文件、115+/21-，无测试文件） | 本次修复无测试门禁 |

---

### 附录：本报告与任务要求的对应关系

1. P0-07 五项子问题：§1（P0-07-a~e）、§2.1-2.3。
2. P0-08 三处检查点：§1（P0-08-a~d）、§2.4。
3. P1-Mem 摘要与 embedding：§1、§2.5。
4. P1-RAG1 双 CRAG：§1、§2.6。
5. AgentRegistry/HermesConfiguration：§1、§2.7。
6. 工作区回退与"提交-工作区"差异：§0.1、§2.8、§3 R3-R6。
7. 未静态判定项与验证方式：§4 V1-V9。
8. 只读与不运行测试/服务纪律：本报告头声明；本轮仅执行 `git`/`read`/`grep`/`unzip`（解压依赖 sources jar 到 /tmp 只读分析）与目录列举。
