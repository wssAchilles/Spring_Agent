# Hermes / 模型侧第二轮验收报告（提交 54fea59b + 1ff190d0）

> 复核员：Hermes 修复复核员（第一轮 task-7、本轮 task-13）　|　复核时间：2026-10-20
> 复核基线：`git rev-parse HEAD` = `1ff190d0`（`fix(ci): 修复CI自动装配依赖缺失与测试套件断言对齐`），工作区 **clean**（仅 `acceptance/` 下同伴报告为未跟踪文件）。
> 复核对象：`54fea59b fix(core): 深度闭环修复API密钥掩码覆盖、流式工具链与多租户权限等核心缺陷`（41 文件）与 `1ff190d0 fix(ci): 修复CI自动装配依赖缺失与测试套件断言对齐`（4 文件）中 **Hermes / 模型侧**改动，以及对方自证报告 `docs/architecture-review-20261019/verify/06-deep-fix-resolution.md`（下称 06 报告）的相关声明。
> 纪律声明：本轮**只读**，未修改/删除任何源码、配置、SQL、fixture；未执行 `mvn`/测试/服务（Lead 正在统一编译与跑测试）；唯一写入为本报告。所有"运行时会怎样"的结论均标注【静态推导】或【无法静态判定】。
> 重要前提：1ff190d0 未改动 Hermes/模型侧生产代码（仅 `qknow-mcp-server` pom、`ToolNodeBO`、`KbFlowServiceImpl`、`tests` pom），因此 Hermes 侧的功能性改动全部来自 54fea59b。另外 54fea59b 还提交了 `docs/architecture-review-20261019/verify/` 下的三类文档（00-VERIFICATION-MASTER、06 报告、第一轮 01 报告更新）；经 `git diff 844e8a69 54fea59b -- verify/01-hermes-fix.md` 逐行核对，**该 01 报告变更 = 第一轮复核员自己提交的基线漂移更新原文，未被篡改**。

---

## 0 复核方法与基线

### 0.1 基线核验

| 核验项 | 命令 | 结果 |
|---|---|---|
| 当前 HEAD | `git log --oneline -3` | `1ff190d0` ← `54fea59b` ← `844e8a69`；`git status --porcelain` 仅 `?? acceptance/`（同伴报告） |
| 54fea59b 覆盖 | `git show --stat 54fea59b` | 41 文件、+1255/-170；Hermes/模型侧 5 文件：`DeepSeekCompatibleChatModel(+170)`、`AgentOrchestrator(+39)`、`AgentRegistry(+13)`、`GrpcReactorBridge(+26)`、`HermesGrpcClient(+9)`、`HermesGrpcAuthInterceptor(+63, 新增)` |
| 1ff190d0 覆盖 | `git show --stat 1ff190d0` | 4 文件：`qknow-mcp-server/pom.xml`、`ToolNodeBO`、`KbFlowServiceImpl`、`tests/pom.xml`；**无 Hermes/模型侧改动**（ToolNodeBO 属流程域，但构成本轮提交级回归，见 §2.7） |
| 06 报告声明 | `sed -n '1,160p' docs/.../verify/06-deep-fix-resolution.md` | 声称 P0-07/P0-08/P0-09/P0-11/P0-12/P0-04/P0-06 及 R1~R7 "全部闭环"，并以 `DeepSeekCompatibleChatModelContractTest (9/9)`、`GrpcCancelPropagationTest (3/3)` 等运行证据背书 |
| 运行验证纪律 | — | 本报告**未执行任何 Maven/测试**；06 报告中的测试计数属对方自述，本报告只核对代码与测试源码的真实覆盖范围 |

### 0.2 取证范围

1. 生产代码（工作区当前、全读或按关键段全读）：`DeepSeekCompatibleChatModel.java(559)`、`AgentOrchestrator.java`（重点 78-90、280-410、450-580、680-760）、`GrpcReactorBridge.java(87)`、`HermesGrpcClient.java(182)`、`HermesGrpcService.java`、`HermesGrpcAuthInterceptor.java(63)`、`AgentRegistry.java`、`WorkerAgent.java`、`PlanSolveConfig`、`HermesApplication`、`hermes.proto`、`docker-compose-qknow.yml`。
2. 测试与 CI：`backend/tests/src/test/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModelContractTest.java(309)`、`GrpcCancelPropagationTest.java`、`backend/tests/pom.xml`（surefire includes/excludes）、`.github/workflows/ci.yml`。
3. 框架契约（决定 P0-07/P0-08 真伪，全部来自 `~/.m2` 只读 sources jar 与 JDK 21 自带 `src.zip`）：
   - `spring-ai-model 1.1.0`：`AssistantMessage`/`ToolResponseMessage`/`ToolCallingChatOptions`/`MessageAggregator`；
   - `spring-ai-client-chat 1.1.0`：`DefaultChatClient`、`ChatClientMessageAggregator`；
   - `spring-ai-deepseek 1.1.0`：`DeepSeekChatModel`（官方 tool 消息映射对照）、`DeepSeekStreamFunctionCallingHelper`；
   - `spring-ai-alibaba-agent-framework 1.1.2.2`：`AgentLlmNode`、`DefaultBuilder`、`ReactAgent`、`Agent`；
   - `spring-ai-alibaba-graph-core 1.1.2.2`：`NodeExecutor`、`AsyncNodeActionWithConfig`、`GraphRunner`、`CompiledGraph`；
   - JDK 21：`java.net.http/jdk/internal/net/http/ResponseSubscribers`（验证 `lines.close()` 能否唤醒阻塞读）。
4. 版本锁定核对：`backend/pom.xml:35-36`（spring-ai 1.1.0 / spring-ai-alibaba 1.1.2.2）在本轮未变。

---

## 1 逐项裁决表

| 编号 | 复核子项 | 判定 | 关键证据（当前行号） | 残留风险 |
|---|---|---|---|---|
| P0-07-a | 空片段守卫移到 tool_calls 提取之后 | **已修复**（静态） | `DeepSeekCompatibleChatModel.java:381-397`（先 `accumulate` 再 `:393` 守卫，条件含 `hasToolCalls`） | 无对应端到端测试；`parseStreamChunk(String)` 旧重载仍为无累加语义（仅测试用） |
| P0-07-b | `StreamToolCallAccumulator` 按 index 累积 id/name/arguments | **已修复**（静态） | `:486-559`（`TreeMap<Integer>`、id/name 缺省继承、`StringBuilder` 追加参数；`:496-523` 累积、`:526-538` 取当前全量、`:544-557` 原始列表） | 空参数时 `getCurrentToolCalls()` 用 `"{}"`（`:534`）而 `toRawToolCallsList()` 用 `""`（`:552`）不一致；无框架集成测试 |
| P0-07-c | 与框架 `NodeExecutor.mergeToolCalls` 兼容 | **兼容（静态推导）** | graph-core `NodeExecutor.java:262-277`（`mergeToolCalls` 合并内容/工具调用）、`:390-407`（同 id 用当前 chunk **覆盖**、否则追加）；因累加器每 chunk 输出**全量累计列表**且 id 稳定，覆盖语义收敛为最终完整参数 | 依赖框架当前"同 id 覆盖"实现；若框架改为拼接或消费者换用 `MessageAggregator`（`addAll`，见 `spring-ai-model MessageAggregator.java:63,107`）会出现重复/错乱 |
| P0-07-d | `ToolResponseMessage` 展开为多条 tool 消息（带 `tool_call_id`/内容） | **已修复**（静态） | `DeepSeekCompatibleChatModel.java:142-159`（逐 `ToolResponse` 生成 `role/content/tool_call_id/name` 并 `continue`）；与官方对照 `spring-ai-deepseek DeepSeekChatModel.java:459-466`（同样携带 `name+id+responseData`）一致 | 空 `responses` 时回落到通用路径会产生无 id 的 tool 消息（边缘）；未做多轮真实回环测试 |
| P0-07-e | assistant 历史 `tool_calls` 回传 | **已修复**（静态） | `:172-194`（metadata 优先 + `getToolCalls()` 兜底生成 OpenAI 结构） | 流式聚合消息 `content=""` 时 `arguments` 依赖 metadata 原始列表；空参数用空串（见 P0-07-b） |
| P0-07-f | thinking 与 tools 组合是否违反官方参数约束 | **无法静态判定**（生产不可达） | thinking 仅由 `DeepSeekChatOptions` 注入（`:224`，`dsOptions.getTools()` 分支 `:230`）；生产图传 `ToolCallingChatOptions`（`AgentLlmNode.java:333-373,489-519`）；`DeepSeekChatOptions` 生产构造点仅 `mor/AdaptiveThinkingGovernor`、`ReasoningDecision`，全仓无接线；thinking 开启时温度被抑制（`:278-280`） | 官方是否允许 thinking+tools 组合未验证；同时说明**thinking 能力仍未接入生产**（既有缺陷，非本轮回归） |
| **P0-07 整体** | DeepSeek 流式 ReAct 工具链 | **已修复（静态链路闭合，未运行验证）** | 上述 a~e 构成"tools 发送→流式解析/累积→框架按 id 聚合→tool 执行→tool 消息回传"完整静态链路 | 无 HTTP/SSE 级与 graph 端到端测试；`content=""`、`name` 字段、空参 `{}`/`""` 等 API 层接受度未验证；DB 工具 code 无法解析时框架 `DefaultBuilder.gatherLocalTools` 仍抛 `IllegalStateException`（既有问题，未修） |
| P0-08-a | `AgentOrchestrator` 提前注册 `emitter.onDispose` | **部分修复（仍不生效）** | `:469-477` 注册提前；但 `streamSubscriptionRef` 直到 `:565`（`stream.subscribe(...)` 返回后）才 `set` | 同步阻塞执行模型下（见 §2.3），断连时 `:473` 读到 null → 内层订阅不 dispose；`if (emitter.isCancelled()...) dispose()`（`:566-569`）在流结束后才执行，为死路径 |
| P0-08-b | `GrpcReactorBridge` 提前注册 `setOnCancelHandler` | **部分修复（仍不生效）** | `GrpcReactorBridge.java:50-61` 注册提前、`:53-61` 回调读 `subscriptionRef`；`subscriptionRef.set` 在 `:82`（subscribe 返回后） | 同上：`monitoredFlux.subscribe(...)` 在同步阻塞源下不返回，取消时 ref=null → 不 dispose |
| P0-08-c | DeepSeek `sink.onDispose(() -> lines.close())` | **机制成立、调用路径未闭合** | `:106-110` 注册；`:114` 循环改为先 `isCancelled()` 后 `hasNext()`；JDK 21 `ResponseSubscribers.java:640-660`（close 置 closed、cancel、`buffers.offer(LAST_LIST)`）与 `:444-497`（`buffers.take()` 阻塞、close 后 `:463` 抛 IOException）证明**若被调用**可唤醒阻塞读 | 该 onDispose 只在 DeepSeek 订阅被取消/释放时触发；上游无法 dispose ⇒ 实际永不触发（静态推导） |
| **P0-08 整体** | 三层取消级联 | **部分修复（未闭环）** | 组件与机制均已就位（a~c + 控制面 `KbConversationController.java:181-186`），但两层 `AtomicReference` 赋值时序决定断连瞬间 ref 必为 null | 原始缺陷"断连后 Token 空烧至自然结束/120s"仍在；且取消时提前释放 `ACTIVE_REACT_RUNS`（`:477`）会让并发闸门不再反映真实在跑流（计数漂移） |
| TST | 新增 `DeepSeekCompatibleChatModelContractTest` 真实覆盖度 / CI 归属 | **部分修复（测试证据不足）** | 实际位于 `backend/tests/src/test/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModelContractTest.java`（新增 3 个：`:205-241`、`:243-278`、`:280-308`，全类 9 个）；`qknow-framework/qknow-ai` **无 `src/test` 目录**（`git ls-files` 为空） | 仅覆盖解析层（buildRequestBody/parseStreamChunk/accumulator），不覆盖 `streamResponse` HTTP 层、`sink.onDispose`、取消、graph 端到端、多轮工具回环、thinking；**不在 hermes-core 快速门禁**（`ci.yml:75`），仅在 backend 集成门禁跑（`ci.yml:109`） |
| REG | `AgentRegistry` 注入 / 多智能体接线 | **未修复（P0-15 不变）+ 新增回归风险** | 注入点 `AgentOrchestrator.java:85-86`；复用点 `:733-747`；planner prompt 不产出 worker 字段、默认 `"rag_worker"`（`:684-708`）；`WorkerAgent.java:29-32,60-89` 使用构造期 systemPrompt/tools/chatModel；`AgentRegistry.java:27-29,51-52,61-84` 默认注册 3 个 `chatModel=null` 的 Worker；`ensureInitialized`（`:137-145`）全仓无调用 | plan-solve 默认关闭（`application.yml:93-94`、`PlanSolveConfig.java:14`），开启后：跨请求复用旧 systemPrompt/tools（含旧会话 RAG 预检索内容）/chatModel（含旧用户模型凭据）→ 跨请求/跨 Bot 数据与凭据污染；并发 get→create→register 非原子（`:86-100`） |
| SEC | P0-07/08 相关安全残留（仅指出） | **未修复（安全另有人负责）** | `HermesGrpcClient.java:47` 仍 `usePlaintext()`；token 明文 metadata（`:93-96`）；默认 token 硬编码四处一致（`HermesGrpcAuthInterceptor.java:26`、`HermesGrpcClient.java:38-39`、compose `:44,:87`）；loopback 白名单默认放行（interceptor `:29-30,50-55`）；`isHealthy()` 不带 token（`:110-120`，无 main 调用者，潜伏）；proto 仍明文 `api_key`（`hermes.proto:50,56`） | compose 已改 `expose` 内网（`:89-91`，正向）；无 TLS 下 token 可嗅探/重放；healthCheck 若从非 loopback 调用会被 401 |
| RUNAWAY | `1ff190d0` 回退 `ToolNodeBO` 假成功 | **新增回归（提交级）** | `54fea59b` 把 `adapter==null` 改为 `return NodeRunResultBO.failure(...,"拒绝伪造成功执行")`；`1ff190d0` 又改回 `callResult="{\"status\":\"SUCCESS\",...,\"Executed in standalone mock mode\"}"`（当前 `ToolNodeBO.java:132-135`） | 与 06 报告 R4"已彻底根除"直接矛盾；与 `02-flow-round2.md §2.6` 结论一致（交叉验证）；生产主链通常走非空 adapter，但任何降级/独立部署即"未执行却成功" |

**汇总（本报告顶层 7 项）**：已修复 1（P0-07，静态闭环、未运行验证）；部分修复 2（P0-08、新增测试覆盖度）；未修复 2（AgentRegistry 多智能体接线、P0-04/03 安全残留）；新增回归 2（1ff190d0 ToolNodeBO 伪成功回潮、plan-solve worker 跨请求状态污染）。

---

## 2 关键证据与代码走查

### 2.1 P0-07：三处代码修复成立，静态链路闭合

1. **空片段守卫顺序**（`DeepSeekCompatibleChatModel.java:381-397`）：先算 `hasDeltaToolCalls`/`accumulator.accumulate(deltaNode)`（`:381-390`），再执行"content/reasoning/finishReason/usage/toolCalls 全空才丢弃"（`:393-395`）。tool-call-only chunk 不再被 `return null`（对照第一轮 `:323-326` 缺陷），且 `:401-407` 每 chunk 携带累加器全量 `tool_calls` 元数据、`:408-412` 构造 `AssistantMessage.ToolCall`。
2. **参数跨 chunk 累积**（`:486-559`）：`TreeMap<Integer, ToolCallState>` 以 `index` 为键；`:500-521` 用 `id`/`type`/`name` 非空才覆盖（保留首帧属性），`:516-519` 对 `function.arguments` 逐段 `append`；`:526-538` 每帧返回当前全量列表（空参数以 `"{}"` 兜底），`:544-557` 输出原始列表。
3. **tool 结果回传**（`:142-159`）：`ToolResponseMessage.getResponses()` 逐条展开为 `{role:"tool", tool_call_id, content, name}` 的独立消息并 `continue`，不再落回"content 为空、无 tool_call_id"的旧路径。官方 `spring-ai-deepseek DeepSeekChatModel.java:459-466` 同样以 `tr.responseData()/Role.TOOL/tr.name()/tr.id()` 构造，说明该形状与官方 DeepSeek 映射一致（含 `name` 字段并不违规）。
4. **assistant 历史回传**（`:176-194`）：metadata 有 `tool_calls` 直接用；否则由 `assistantMessage.getToolCalls()` 生成标准结构，覆盖第一轮"tool_calls 只塞 metadata、不构造对象"的半修复。
5. **请求侧 tools 发送**（`:239-266`）沿用第一轮已验证的 `ToolCallingChatOptions` 映射；图侧 `AgentLlmNode.java:333-373,489-519` 生成含 callbacks 的 options 并 `setInternalToolExecutionEnabled(false)`，故该分支在生产可达。

因此，按**代码静态链路**，P0-07 的原缺陷机制（tool-only 帧被丢弃、无跨 chunk 累积、ToolResponseMessage 未序列化、assistant tool_calls 缺失）均已被消除；06 报告对 P0-07 的四点描述与代码一致。

### 2.2 P0-07 残余（未运行验证，不能等同于"生产已通"）

- **无端到端证据**：新增 3 个测试全部是 `parseStreamChunk`/`buildRequestBody` 级单元测试（`:207-308`），没有经过 `streamResponse` 的 HTTP/SSE 读取、没有经过 graph-core `NodeExecutor` 的流式聚合、没有走完整 ReAct 工具执行→回传→二次生成回环（§2.4）。
- **API 层接受度未定**：assistant 消息 `content=""`（tool-only 聚合消息的 `AssistantMessage.getText()`）、tool 消息附 `name` 字段、空参数 `arguments` 在 `{}`/`""` 两种表示间不一致（`:534` vs `:552`）——这些只能通过真实 DeepSeek API 或本地 fake SSE server 验证。
- **既有工具名解析问题未修**：`AgentOrchestrator` 仍以 `guardedResolver` 解析 DB 工具 code（`:435-448`）；`DefaultBuilder.gatherLocalTools` 对解析不到的名字抛 `IllegalStateException`（agent-framework `DefaultBuilder.java:236-240`）。工具 genuinely 开始下发后，配错工具 code 的 Bot 会从"工具不执行"变成"整轮报错"（第一轮已指出，本轮未处理）。
- **thinking 模式仍不可用**：见 P0-07-f；因此"thinking 与 tools 互斥"问题本轮既未修复也未触发。

### 2.3 P0-08：Lead 疑点 1 — 提前注册解决不了"ref 仍在使用后才赋值"

**结论：仍未闭环。**

1. `AgentOrchestrator.java:469-477` 在 `stream.subscribe(...)`（`:481`）**之前**注册 `emitter.onDispose`，但回调体在 `:473` 读取 `streamSubscriptionRef.get()`；该 ref 在 `:565`（subscribe 返回后）才 `set`。
2. 图执行是**同步阻塞**的（框架源码）：`Agent.doStream` 无调度器（agent `Agent.java:507-511,523-531`）→ `CompiledGraph.streamFromInitialNode` 用 `Flux.defer` + `Mono.fromFuture`（graph-core `CompiledGraph.java:578-599`）→ `GraphRunner.run` 仅 `Flux.defer`（`GraphRunner.java:48-59`）→ `NodeExecutor` 同步调用节点动作并把已完成 future 展开（`NodeExecutor.java:123-126`；`AsyncNodeActionWithConfig.java:36-47` 同步 `complete`）→ LLM 节点的内嵌 Flux 被订阅后，`DeepSeekCompatibleChatModel.streamResponse` 的 `Flux.create` 消费者在**订阅线程上阻塞读 HTTP**（`:97-118`）。主执行路径无 `subscribeOn`（框架仅并行分支用 scheduler，`NodeExecutor.java:728-745`）。
3. 因而 `stream.subscribe(...)` 在整轮生成（含多步 ReAct 往返）结束前不返回；客户端在流中途断连时，gRPC 传输线程会触发取消回调，但 `streamSubscriptionRef` 仍为 null（`GrpcReactorBridge.subscriptionRef` 同理，`GrpcReactorBridge.java:53-61,82`）→ 内层订阅不会被 dispose → DeepSeek `sink.onDispose` 不触发 → `lines.close()` 不执行 → **Token 继续烧到自然结束**（或 120s 超时）。`:566-569` 的"subscribe 返回后若已取消再 dispose"只在流已结束后成立，属死路径。
4. `sink.onDispose(lines.close())` 的**机制本身是有效的**（这是本轮真实改进）：JDK 21 `BodyHandlers.ofLines()` 的 `Stream` 关闭会走到 `HttpResponseInputStream.close()`，后者置 `closed=false→true`、`subscription.cancel()` 并在 finally `buffers.offer(LAST_LIST)`（`ResponseSubscribers.java:640-660`），从而唤醒阻塞在 `buffers.take()` 的读线程（`:444-497`，`:463` 看到 closed 抛 IOException）。也就是说：**只差把 DeepSeek 订阅的取消信号接通**。
5. 现有验证不能证明闭环：06 报告用 `GrpcCancelPropagationTest (3/3)` 作为 P0-08 证据（`06-deep-fix-resolution.md:24,140-145`），但该测试自 `0cbf26bc` 之后未改动（`git log -3 -- GrpcCancelPropagationTest.java`），三个用例分别使用 `Flux.interval(10ms)`（异步定时源）与 `Flux.just`（同步即时源）——**没有一个是"订阅时阻塞"的源**，恰好避开了生产缺陷时序。`06` 报告同时声称"已闭环"，属**证据与结论不匹配**。
6. 附带新增副作用：取消时 `onDispose` 里 `releaseReactCapacity.run()`（`:477`）会提前释放 `ACTIVE_REACT_RUNS`，而实际生成仍在继续——闸门计数不再反映真实并发（中等风险）。

**正确的修复方向**（供下一轮）：把 `stream.subscribe(...)` 放到独立调度器上（如 `stream.subscribeOn(Schedulers.boundedElastic()).subscribe(...)`）使 `streamSubscriptionRef` 在阻塞开始前可用；或把内层订阅包装为 `Flux.create` 并在其中暴露 sink 的取消句柄；同时把 `AtomicReference` 换成 `Sinks`/`Disposable.Swap` 一类的可取消容器，消除 null 窗口。

### 2.4 Lead 疑点 2：新测试真实覆盖度与模块归属（Lead 描述有误）

- **位置更正**：任务描述中的 `backend/qknow-framework/qknow-ai/src/test/java/.../DeepSeekCompatibleChatModelContractTest.java` **不存在**；`qknow-framework/qknow-ai` 模块当前无 `src/test` 目录（`git ls-files backend/qknow-framework/qknow-ai | grep src/test` 为空）。真实位置是 `backend/tests/src/test/java/tech/qiantong/qknow/ai/deepseek/DeepSeekCompatibleChatModelContractTest.java`（309 行，全类 9 个 `@Test`，本轮新增 3 个，`:205-308`）。
- **测了什么**：
  1. 契约 7（`:207-241`）：`ToolResponseMessage` 展开为 2 条 tool 消息，断言 `role/tool_call_id/content/name`——真实覆盖 `P0-07-d` 的序列化，但仅 `buildRequestBody` 层；
  2. 契约 8（`:243-278`）：手工 JSON tool-call-only chunk 经 `parseStreamChunk` 不返回 null、`hasToolCalls()==true`——真实覆盖 `P0-07-a` 的守卫顺序；
  3. 契约 9（`:280-308`）：三段 arguments 分片经 `parseStreamChunk(payload, accumulator)` 拼接为 `{"sql":"SELECT 1"}`——真实覆盖 `P0-07-b` 的累加器。
- **没测什么**：`streamResponse`（私有 HTTP 读取，`:88-135`）、`sink.onDispose(lines.close())` 取消路径、`GraphReactorBridge`/`AgentOrchestrator` 取消时序、graph-core `NodeExecutor` 的合并兼容性、多轮工具执行（tool node 执行 + 第二轮请求）、`AssistantMessage.getToolCalls()` 兜底回传（`:180`）、thinking+tools、以及"模型返回不完整 JSON 参数"的坏形态。即 9/9 是**解析层单测全绿**，不能作为"生产工具链闭环"的证据。
- **CI 归属**：`backend/tests` 模块由 `backend-integration-gate` 执行（`.github/workflows/ci.yml:109` `-pl tests -am test`，且 backend 路径变更时触发）；**不在** `hermes-core-gate`（`:75` `-pl qknow-hermes/qknow-hermes-core -am test`，`qknow-ai` 无测试目录）。结论：新增测试**会被 CI 执行**，但处于"全量集成门禁"而非"Hermes 快速契约门禁"，且模块归属使 `qknow-ai` 自身仍无就近测试；未来修改客户端时门禁反馈更慢。

### 2.5 Lead 疑点 3：`StreamToolCallAccumulator` 与框架 `mergeToolCalls` 的兼容性

**结论：按标准 DeepSeek/OpenAI 协议兼容，且"每 chunk 发累积列表"反而是当前框架语义下的正确选择。**

- 框架 `NodeExecutor` 对每对相邻 chunk 执行 `mergeToolCalls(lastOutput.getToolCalls(), currentMessage.getToolCalls())`（`NodeExecutor.java:262-277`），其实现为：`currentToolCalls.forEach(tc -> toolCallMap.put(tc.id(), tc))` → 复用 last 中"不在 current id 集合"的调用 → `resultCalls.addAll(currentToolCalls)`（`:390-407`）。也就是**同 id 以当前 chunk 覆盖、不同 id 追加**。
- 本项目累加器每帧返回"当前全量（id 稳定、参数已累积）"列表，因此每帧覆盖后 = 该帧全量，最终聚合消息的参数 = 最后一帧参数 = 完整 JSON。若累加器改为"只发增量片段"，框架的覆盖语义会丢失早期片段——所以**当前实现与框架语义是自洽的**。
- 两个需要留意的点：① 中间帧的 `AssistantMessage` 携带全量 toolCalls，`AgentOrchestrator` 的 `AGENT_MODEL_STREAMING` 回调只读 `text/reasoning_content`（`:490-530`），不会重复下发给前端；② 若未来有消费者改用 `MessageAggregator`（`spring-ai-model MessageAggregator.java:63,107` 对 toolCalls 仅 `addAll`），累积列表会产生重复条目——当前 `ChatClientMessageAggregator` 仅用于 observation 侧（`spring-ai-client-chat ChatClientMessageAggregator.java:52-65` 透传原流并只聚合给 observation handler），不影响生产数据面；但这是**隐式契约**，建议补一条"fake ChatModel 多帧→NodeExecutor 聚合"的集成测试锁定。
- 同一处还有两个小不一致：`getCurrentToolCalls()` 空参输出 `"{}"`（`:534`）、`toRawToolCallsList()` 空参输出 `""`（`:552`）；而重放路径优先 metadata（`:172-176`），极端情况下"无参工具"第二轮会发送空字符串 arguments（非法 JSON 风险）。属边缘，但建议统一。

### 2.6 Lead 疑点 4：`AgentRegistry` 注入没有接入多智能体，只是 plan-solve worker 复用，并引入状态污染

- **注入与使用**：`AgentOrchestrator.java:85-86` `@Autowired(required=false) AgentRegistry`；唯一使用点是 `executePlanTasks`（`:733-747`）：先从 registry 按 `task.worker()` 取 `WorkerAgent`，取不到则以**当次请求**的 `systemPrompt/tools/chatModel/resolver` 新建并 `registerAgent`。除此之外全仓 main 代码无 `getWorkers/createSupervisor/getAllAgents` 消费者——swarm/debate/consensus 等仍零生产调用。**P0-15 结论不变：多智能体仍未接入生产聊天主链。**
- **默认 worker 名**：`createPlan` 的 Prompt 只要求 `taskId/objective/dependencies`（`:684-693`），不要求 `worker` 字段；`parsePlan` 用 `defaultString(object.getString("worker"), "rag_worker")`（`:707`）→ 实际几乎全部任务都叫 `rag_worker`。
- **状态污染（新增风险）**：`WorkerAgent` 的 `systemPrompt/tools/chatModel/resolver` 是构造期 final（`WorkerAgent.java:29-32`），`chat()` 直接用它们建 ReactAgent（`:60-89`）。因此第一个开启 plan-solve 的请求注册 `rag_worker` 后，**后续任意 Bot/任意用户的请求**命中 registry 便会复用旧请求的：① `systemPrompt`（上一个 Bot 的 prePrompt）；② `tools`（上一个会话的 `knowledgeBase<id>` 回调，内部绑定**上一个会话的预检索内容** `SearchKnowledgeTool(knowledgeId,...,preRetrievedContent)`）；③ `chatModel`（上一个用户模型配置/凭据实例）。这是跨请求、跨租户的数据与凭据混用——新增回归风险。
- **默认 worker + null 模型**：Hermes 服务的 `AgentRegistry` 由 `@Component`+无参构造创建（`AgentRegistry.java:27-29` → `:51-52`）→ `initDefaultAgents(null)`（`:61-84`）注册 `RAGAgent/SearchAgent/CodeAgent` 三个 **chatModel=null、tools 为空**的 Worker。若 planner 偶尔输出这些名字，`WorkerAgent.chat` 的异常会被吞成 `"执行失败: ..."`（`WorkerAgent.java:86-88`）→ 计划任务静默降级（由 `hasFailedWorkerResult` 触发修复兜底）。
- **并发**：`getAgent` + 新建 + `registerAgent` 非原子（`:86-100` 各自线程安全但组合不安全）；同一请求 4 个并行 plan task 可能重复创建/覆盖；跨请求并发时"谁最后注册"决定后续污染源。
- **可达性**：`qknow.hermes.plan-solve.enabled` 默认 false（starter `application.yml:93-94`；`PlanSolveConfig.java:14` 默认 false），因此默认生产不触发；但该开关支持配置/环境变量开启（配置即触发），且 06 报告没有把该路径纳入验证。`ensureInitialized`（`:137-145`）全仓无调用，属死代码。

### 2.7 1ff190d0 的提交级回归与 06 报告失真

- `54fea59b` 对 `ToolNodeBO` 的修正是：adapter 为 null 时 `return NodeRunResultBO.failure(..., "拒绝伪造成功执行")`，并新增 `resObj.containsKey("error")` 拦截（`git show 54fea59b -- ToolNodeBO.java`）。
- `1ff190d0` 以 `fix(ci)` 名义把该分支改回 mock 成功：`callResult = "{\"status\":\"SUCCESS\",...,\"Executed in standalone mock mode\"}"`（`git show 1ff190d0`；当前 `ToolNodeBO.java:132-135`），提交信息自述动机是"保证 ReAct 熔断测试稳定运行"。
- 因此 06 报告 R4"已彻底根除"（`06-deep-fix-resolution.md:21,58`）与本轮**最终 HEAD** 矛盾：Provider 侧仍是客观报错（✓，`CodeSandboxMcpProvider.java:78` 等），但生产类 `ToolNodeBO` 在 adapter 缺失时仍返回伪 SUCCESS（✗）。这也是 master 报告 §4 所述"先修测试契约而非反过来改生产"的**同一模式在同一轮内复发**。与 `02-flow-round2.md §2.6` 结论一致（交叉验证）。该分支在生产主链通常不可达（`HermesConfiguration` 提供非空 `McpToolAdapter`），但 `NodeFactory` 可选注入/独立部署/降级场景可达，违反 fail-close 原则。

### 2.8 安全残留（仅指出，安全裁决另有人负责）

- **传输**：`HermesGrpcClient.java:47` 仍 `.usePlaintext()`；`x-hermes-auth-token` 明文 metadata（`:93-96`）；proto 仍明文携带 `api_key`（`hermes.proto:50,56`）。鉴权 token 在明文通道上可嗅探/重放，P0-03 传输面未闭环。
- **默认 token**：`HermesGrpcAuthInterceptor.java:26`、`HermesGrpcClient.java:38-39`、`docker-compose-qknow.yml:44,87` 四处同一字面量 `qknow-hermes-grpc-internal-token-2026`；不配环境变量即"公开共享密钥"。
- **绕过面**：拦截器 loopback 白名单默认放行（`:29-30,50-55`，`remoteAddr.toString().contains("127.0.0.1")`），同机/同 pod 任意进程可绕过 token。
- **潜伏不一致**：`HermesGrpcClient.isHealthy()`（`:110-120`）不使用 token，而拦截器为 `@GrpcGlobalServerInterceptor` 全局生效（经 `javap -v` 确认该注解含 `@Component` 元注解，`grpc-server-spring-boot-starter-3.1.0`）；当前 main 代码无 `isHealthy()` 调用者，但一旦被控制面用于健康检查且走非 loopback，将恒为 false。
- 正向：compose 已把 9090/8081 从 `ports` 改为 `expose`（`docker-compose-qknow.yml:89-91`），宿主明文端口暴露已消除。

---

## 3 新增风险与回归

| ID | 风险 | 类型 | 证据 | 严重度 |
|---|---|---|---|---|
| R1 | **P0-08 "已闭环"为假**：onDispose/取消回调提前注册但 ref 在阻塞 subscribe 返回后才赋值；断连时 ref=null → 内层不 dispose → `lines.close()` 永不触发 → Token 继续烧；同时 `ACTIVE_REACT_RUNS` 提前释放造成并发计数漂移 | **部分修复/假闭环（非本轮新增功能回归，但结论失真）** | §2.3；`AgentOrchestrator.java:469-477,565-569`；`GrpcReactorBridge.java:53-61,82`；`DeepSeekCompatibleChatModel.java:106-110`；JDK `ResponseSubscribers.java:444-497,640-660` | **P0** |
| R2 | **plan-solve worker 跨请求状态/凭据污染**：默认 `rag_worker` 复用旧请求的 systemPrompt/tools（旧会话 RAG 内容）/chatModel（旧用户模型凭据）；并发放任 get→create→register；默认 3 个 worker chatModel=null | **新增回归风险（本轮新引入）** | §2.6；`AgentOrchestrator.java:684-708,733-747`；`WorkerAgent.java:29-32,60-89`；`AgentRegistry.java:27-29,51-52,61-84` | **P0（配置开启即触发）** |
| R3 | **1ff190d0 回退 ToolNodeBO 伪成功**（提交级），与 06 报告 R4 矛盾 | **新增回归（已提交）** | §2.7；`ToolNodeBO.java:132-135`；与 `02-flow-round2.md §2.6` 交叉一致 | **P1** |
| R4 | **测试证据自证/覆盖不足**：9/9 为解析层单测；P0-08 用未改动的异步源取消测试背书；P0-08/P0-07 端到端零测试 | **验收风险** | §2.3-2.4；`06-deep-fix-resolution.md:21-24,140-145`；`GrpcCancelPropagationTest.java:94-170`（`Flux.interval`/`Flux.just`） | **P1** |
| R5 | **P0-07 残余**：空参 `{}`/`""` 不一致；`content=""`、tool 消息 `name` 字段等 API 接受度未验证；DB 工具名解析失败仍会抛 `IllegalStateException`；thinking 模式依旧不可达 | 部分修复残余 | §2.2；`DeepSeekCompatibleChatModel.java:534,552`；`DefaultBuilder.java:236-240` | P2 |
| R6 | **安全残留**（gRPC 无 TLS、公开默认 token、loopback 绕过、healthCheck 无 token、proto 明文 apiKey） | 未修复（安全面） | §2.8；`HermesGrpcClient.java:38,47,93-96,110-120`；`HermesGrpcAuthInterceptor.java:26,29-30,50-55`；`hermes.proto:50,56` | P0/P1（安全复核员裁决） |

---

## 4 未能静态判定项与建议验证

| # | 项 | 原因 | 建议验证（交由 Lead/下一轮执行；本复核未运行） |
|---|---|---|---|
| V1 | P0-08 生产取消是否真的不生效 | 本报告基于框架源码推导"同步阻塞订阅"，需运行验证 | 用"阻塞型 Fake ChatModel"（`stream()` 返回 `Flux.create(sink -> { sink.next(chunk); Thread.sleep(3000); ...})`）驱动 `AgentOrchestrator.chat(...)`，订阅后从另一线程 dispose/cancel，断言：① Fake 源 `doOnCancel/onDispose` 在 500ms 内触发；② `DeepSeekCompatibleChatModel` 等价 fake 的 `lines.close()` 被调用；③ `ACTIVE_REACT_RUNS` 计数与真实在跑数一致 |
| V2 | P0-07 端到端 ReAct 工具链 | 需真实 HTTP/SSE 与 graph 回路 | 本地 fake SSE server 按 OpenAI/DeepSeek 协议回放"tool_calls 分片→finish_reason=tool_calls→usage"；跑 `ReactAgent` 或 `AgentOrchestrator`，断言 tool 被执行且第二轮请求含 assistant tool_calls + 2 条 tool 消息（id/content）、最终回答非空；同时录下请求体确认 `content=""` 与 `name` 字段被接受 |
| V3 | `StreamToolCallAccumulator` 与 graph 聚合的端到端一致性 | 本报告为源码层论证 | 构造多帧 fake ChatModel 走 graph-core `NodeExecutor`，断言最终状态里 `AssistantMessage.getToolCalls()` 的 arguments 完整、无重复条目、tool 执行一次 |
| V4 | thinking 与 tools 官方兼容性 | 外部 API 行为 | 按 DeepSeek 官方文档核对；如允许，用 `DeepSeekChatOptions(thinking=on)` + tools 做一次真实 API 冒烟（需授权 Key/预算） |
| V5 | plan-solve 开启后的跨请求污染 | 需集成/端到端上下文 | 单测：模拟两次不同 bot 的 `executeAgent`，断言 `AgentRegistry.getAgent("rag_worker")` 第二次不会复用第一次的 systemPrompt/tools/chatModel（当前实现会复用）；或加"每请求命名空间/不注册临时 worker"的修复后回归 |
| V6 | gRPC 鉴权配置互通 | 部署运行期 | compose 起 hermes + 控制面，验证 chat 正常；`isHealthy()` 若启用需补 token 或 loopback 处理；另验证 token 环境变量覆盖一致性 |
| V7 | 9/9 测试在 CI 的真实执行 | 本复核未运行 | `mvn -B -ntp -f backend/pom.xml -pl tests -am -Dtest=DeepSeekCompatibleChatModelContractTest test`（Lead 侧统一执行）；同时确认 backend-integration-gate 触发路径 |

---

### 附录 证据索引

> 行号为 2026-10-20 HEAD=`1ff190d0` 工作区当前内容；框架/JDK 证据标注依赖 sources 路径。

| ID | 证据位置 | 证明要点 |
|---|---|---|
| E01 | `git log --oneline -3`、`git status --porcelain` | HEAD=1ff190d0，工作区 clean（仅 acceptance/ 未跟踪） |
| E02 | `git show --stat 54fea59b`（41 文件 +1255/-170）；`git show --stat 1ff190d0`（4 文件） | Hermes 侧改动全部来自 54fea59b；1ff190d0 无 Hermes/模型侧改动 |
| E03 | `DeepSeekCompatibleChatModel.java:381-397` | 空片段守卫移到 tool_calls 提取之后（P0-07-a 修复） |
| E04 | 同上 `:486-559` | `StreamToolCallAccumulator` 按 index 累积、覆盖 id/name、拼接 arguments |
| E05 | 同上 `:106-110`、`:114` | `sink.onDispose(lines.close())` + 先判 `isCancelled()` 再 `hasNext()` |
| E06 | 同上 `:142-159` | `ToolResponseMessage` 逐条展开为带 `tool_call_id/content/name` 的 tool 消息 |
| E07 | 同上 `:176-194` | assistant 历史 `tool_calls`：metadata 优先 + `getToolCalls()` 兜底 |
| E08 | 同上 `:224,230,239-266,277-284` | thinking 只在 `DeepSeekChatOptions` 注入；生产传 `ToolCallingChatOptions`；thinking 时抑制 temperature |
| E09 | 同上 `:534` vs `:552` | 空参 `arguments` 的 `"{}"`/`""` 不一致 |
| E10 | `AgentOrchestrator.java:469-477`、`:481`、`:565-569` | onDispose 提前注册，但 ref 在 `stream.subscribe` 返回后才 set（Lead 疑点 1 确认） |
| E11 | `AgentOrchestrator.java:85-86`、`:684-708`、`:733-747` | `AgentRegistry` 注入 + plan-solve worker 复用 + 默认 `"rag_worker"` |
| E12 | `AgentRegistry.java:27-29,51-52,61-84,86-100,137-145` | 无参构造注册 3 个 null-model worker；`ensureInitialized` 无调用；get/register 非原子 |
| E13 | `WorkerAgent.java:29-32,60-89` | WorkerAgent 使用构造期 systemPrompt/tools/chatModel；异常被吞为"执行失败" |
| E14 | `PlanSolveConfig.java:14`；`qknow-hermes-starter/application.yml:93-94` | plan-solve 默认关闭（配置可开启） |
| E15 | `GrpcReactorBridge.java:50-61,82` | `setOnCancelHandler` 提前注册但 `subscriptionRef` 在 subscribe 返回后 set |
| E16 | `KbConversationController.java:181-186`（第一轮已核，本轮未变） | 控制面 doOnCancel dispose 上游；触发前提仍无法静态判定 |
| E17 | agent-framework 1.1.2.2 `Agent.java:523-531`、graph-core `CompiledGraph.java:578-599`、`GraphRunner.java:48-59`、`NodeExecutor.java:123-126`、`AsyncNodeActionWithConfig.java:36-47` | 图执行同步、无 `subscribeOn`，支持"subscribe 阻塞"推导 |
| E18 | graph-core `NodeExecutor.java:262-277,390-407` | 流式聚合按 id 覆盖；与累加器全量语义自洽 |
| E19 | spring-ai-client-chat 1.1.0 `ChatClientMessageAggregator.java:52-65`；spring-ai-model `MessageAggregator.java:63,107` | ChatClient 观察聚合为透传，不改变下游数据面 |
| E20 | spring-ai-deepseek 1.1.0 `DeepSeekChatModel.java:459-466` | 官方 tool 消息映射同样携带 `name+id+responseData`，佐证项目展开格式 |
| E21 | JDK 21 `java.net.http/jdk/internal/net/http/ResponseSubscribers.java:444-497,640-660`、`java/net/http/HttpResponse.java:656-659,1233-1235` | `BodyHandlers.ofLines()` 的 Stream close 会 cancel 并唤醒阻塞 `buffers.take()` |
| E22 | `backend/tests/.../DeepSeekCompatibleChatModelContractTest.java:205-308`；`grep -c @Test`=9 | 新增 3 个解析层单测（契约7/8/9）；全类 9 个 |
| E23 | `git ls-files backend/qknow-framework/qknow-ai | grep src/test` 为空 | Lead 所述 qknow-ai 测试路径不存在；真实模块为 backend/tests |
| E24 | `.github/workflows/ci.yml:75,109`；`backend/tests/pom.xml:167-184` | tests 模块走 backend-integration-gate，surefire include `**/*Test.java`；不在 hermes-core 快速门禁 |
| E25 | `GrpcCancelPropagationTest.java:94-170`；`git log -3 -- 该文件`（最后修改 0cbf26bc） | 三个用例均为异步/即时源，未覆盖同步阻塞源；本轮未修改 |
| E26 | `06-deep-fix-resolution.md:21-24,140-145` | 自证报告将 P0-07/P0-08 标为"已闭环"，证据分别为 9/9 解析单测与未改动的 3/3 取消测试 |
| E27 | `git show 54fea59b -- ToolNodeBO` vs `git show 1ff190d0 -- ToolNodeBO`；当前 `ToolNodeBO.java:132-135` | 54fea59b fail-close → 1ff190d0 回退 mock SUCCESS（提交级回归） |
| E28 | `git diff 844e8a69 54fea59b -- verify/01-hermes-fix.md` | 54fea59b 对我第一轮报告的修改 = 我自己的基线漂移更新原文，无篡改 |
| E29 | `HermesGrpcClient.java:38,47,93-96,110-120`；`HermesGrpcAuthInterceptor.java:26,29-30,50-55`；`hermes.proto:50,56`；`docker-compose-qknow.yml:44,87,89-91` | 安全残留：无 TLS、公开默认 token、loopback 绕过、healthCheck 无 token、proto 明文 apiKey；端口已改 expose |
| E30 | `javap -v` on `grpc-server-spring-boot-starter-3.1.0.RELEASE.jar#GrpcGlobalServerInterceptor` | 该注解含 `@Component` 元注解 → 拦截器确实成为 Spring Bean 并全局生效 |
| E31 | `backend/pom.xml:35-36` | spring-ai 1.1.0 / spring-ai-alibaba 1.1.2.2 未变，第一轮框架结论仍适用 |
| E32 | `docs/architecture-review-20261019/acceptance/02-flow-round2.md §2.6`；`03-security-round2.md §3` | 与本报告的 ToolNodeBO 回退、gRPC 安全结论交叉一致 |

---

### 附：与 06 报告声称的差异（便于 Lead 快速定位）

| 06 报告声明 | 本报告核对结果 |
|---|---|
| P0-07 "已闭环修复"，证据 9/9 | 代码静态链路闭合成立；但 9/9 为解析层单测，**不含端到端/HTTP/取消/多轮**，不能等同于生产闭环（§2.1-2.2、§2.4） |
| P0-08 "已闭环修复"，证据 `GrpcCancelPropagationTest (3/3)` | **不成立**：测试未改动且源为异步 `Flux.interval`；两层 `AtomicReference` 均在阻塞 subscribe 返回后才赋值，断连时 ref=null，DeepSeek onDispose 永不触发（§2.3） |
| R4 "已彻底根除"，Provider 返回 error + ToolNodeBO 拦截 | Provider 侧成立；**ToolNodeBO 在 1ff190d0 被回退为 mock SUCCESS**，R4 未根除（§2.7） |
| P0-04/06 "已闭环修复" | gRPC 鉴权已接入（拦截器确为 Bean），但无 TLS、默认 token 公开、loopback 绕过、healthCheck 无 token、proto 明文 apiKey —— 属部分修复，交安全复核员（§2.8） |
| （未提 P0-15/AgentRegistry） | `AgentRegistry` 注入只服务 plan-solve worker 复用，多智能体仍未接入生产，且引入跨请求状态污染（§2.6） |
