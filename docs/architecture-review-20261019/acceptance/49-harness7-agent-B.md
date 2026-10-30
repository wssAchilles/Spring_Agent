# Hermes Harness² 阶段七（Stage 7）：`AgentOrchestrator` 与 `HarnessKernelBridge` 生产桥接法医级代码审计与工程规范报告（Agent B）

> **审计角色**：Agent B —— Java 生产桥接审计员 (Java Production Bridge Auditor)  
> **物理 ConversationId**：`e1765a6e-1d3e-4dac-8a06-eeb257c055e8`  
> **运行 Transcript**：[transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/e1765a6e-1d3e-4dac-8a06-eeb257c055e8/.system_generated/logs/transcript.jsonl)  
> **审计范围**：`AgentOrchestrator`、`HermesHarnessKernel`、`CapabilityContextAssembler`、`HoareTripleToolGuard`、`LocallyDenseCycleAutomaton`、`ReversibleRingColdBuffer` 及 `49-harness7-bridge-design.md`  
> **核心结论**：`49-harness7-bridge-design.md` 确立的三态桥接（`OFF / SHADOW / ENFORCE`）与请求级隔离方向完全正确；但经源码级法医核查，发现 **3 处必须修正的编译/运行期契约细节**（包含 Protobuf `ChatRequest.getInputParams()` 实为 JSON `String` 而非 `Map`、短期记忆 `MemoryManager` 历史消息与 `ChatRequest.getHistoryList()` 的双源对齐、以及 `AgentOrchestrator` 在 `planAndSolve` 之前的拦截切入点与四出口证据闭环挂钩）。本报告给出可直接落入 `src/main/java` 的决策完备级类契约、方法签名与调用时序规范。

---

## 一、`AgentOrchestrator` 公共签名兼容性与零回归边界审计

### 1.1 现有构造器与注入契约法医核查
经逐行审查 [`AgentOrchestrator.java`](file:///Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/AgentOrchestrator.java#L102-L268)，当前类存在 **3 个公共构造器**与 **2 类可选 Setter 注入**：
1. **9 参数非 Spring 测试构造器**（行 200–221）：供大量既有离线单元测试直接 `new AgentOrchestrator(...)` 使用，内部将 `multiAgentOrchestrationService` 回退为默认关闭的兜底实例；
2. **10 参数 `@Autowired` 主构造器**（行 223–262）：Spring 生产环境唯一自动装配入口，且内部包含 `allReflectiveNullProbe`（行 245–255）全 `null` 反射探针兼容分支；
3. **3 参数极简测试构造器**（行 264–267）：委托给 9 参数构造器；
4. **可选 Setter 注入先例**（行 112–123）：`private HermesHarnessAssembler harnessAssembler = new HermesHarnessAssembler();` 配合 `@Autowired(required = false) public void setHarnessAssembler(...)`。

### 1.2 零回归兼容边界铁律
1. **严禁修改现有 3 个构造器签名或新增第 4 个 `@Autowired` 构造器**：
   - 若修改 10 参数构造器，将直接破坏 `qknow-tests` 与 `qknow-hermes-core` 中直接调用该构造器的既有测试；
   - 若新增带 `@Autowired` 的 11 参数构造器，Spring 容器将抛出 `BeanCreationException: Invalid autowire-marked constructor` 歧义异常。
2. **字段默认初始化 + `@Autowired(required = false)` Setter 标准接入模式**：
   在 `AgentOrchestrator` 中采用与 `harnessAssembler` 完全同构的注入方式，确保无论通过哪个构造器（含全 `null` 反射探针）初始化，`harnessKernelBridge` 字段恒为非空且默认处于 `OFF` 短路状态：

```java
// 默认初始化为全局无状态禁用实例，保证非 Spring 构造器与反射探针 100% 零 NPE、零行为变更
private tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge harnessKernelBridge =
        tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge.disabled();

@org.springframework.beans.factory.annotation.Autowired(required = false)
public void setHarnessKernelBridge(
        tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge harnessKernelBridge) {
    // 传入 null 时自动回退为 disabled() 实例，严禁暴露 null 引用
    this.harnessKernelBridge = harnessKernelBridge != null
            ? harnessKernelBridge
            : tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge.disabled();
}

public tech.qiantong.qknow.hermes.agent.harness.kernel.HarnessKernelBridge getHarnessKernelBridge() {
    return this.harnessKernelBridge;
}
```

3. **公共方法签名 100% 零改动承诺**：
   - `public Flux<ChatEvent> chat(ChatRequest request)`
   - `public HarnessEvidenceRecord getLatestEvidence(String requestId)`
   - `public void recordEvidence(HarnessEvidenceRecord evidence)`
   - `public void registerRequestVoucher(String requestId, HermesHarnessVoucher voucher)`
   - `public void unregisterRequestVoucher(String requestId)`
   上述所有既有公共方法签名、返回类型与异常契约保持 100% 原样。

---

## 二、`OFF / SHADOW / ENFORCE` 三态模式与配置契约设计

### 2.1 模式枚举与配置项组合解析真值表

在 `tech.qiantong.qknow.hermes.agent.harness.kernel` 包下定义 `KernelBridgeMode`：

```java
package tech.qiantong.qknow.hermes.agent.harness.kernel;

import java.util.Locale;

/**
 * Hermes Harness Kernel 生产桥接运行模式枚举。
 */
public enum KernelBridgeMode {
    /** 完全旁路关闭（默认）：零对象分配、零校验、100% 走原有路径 */
    OFF,
    /** 影子核验模式：旁路执行 PreparedExecution 与凭单记录，绝不修改实际提示词与工具链，异常时 Fail-Open */
    SHADOW,
    /** 强制接管模式：强制校验 CapabilityTag、替换受控 SystemPrompt、挂载请求级 HoareGuard + CycleAutomaton，违约时 Fail-Close */
    ENFORCE;

    /**
     * 大小写不敏感解析模式字符串；空值返回 OFF，未知非法值抛出 IllegalArgumentException 由调用方按策略处理。
     */
    public static KernelBridgeMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return OFF;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "OFF", "DISABLED", "NONE" -> OFF;
            case "SHADOW" -> SHADOW;
            case "ENFORCE" -> ENFORCE;
            default -> throw new IllegalArgumentException("未知的 KernelBridgeMode: " + raw);
        };
    }
}
```

#### 配置项组合解析规则（`resolveEffectiveMode`）
Spring 配置属性：
- `hermes.harness.kernel.enabled`（布尔值，默认 `false`）
- `hermes.harness.kernel.mode`（字符串，默认 `"OFF"`）

**解析优先级与一票否决律**：
1. 只要 `enabled == false`，无论 `mode` 配置为 `"OFF"`、`"SHADOW"`、`"ENFORCE"` 还是非法字符串，`resolveEffectiveMode()` **恒返回 `KernelBridgeMode.OFF`**（总开关一票否决）；
2. 当 `enabled == true` 时：
   - `mode == "OFF"` 或空白 $\implies$ `KernelBridgeMode.OFF`；
   - `mode == "SHADOW"` $\implies$ `KernelBridgeMode.SHADOW`；
   - `mode == "ENFORCE"` $\implies$ `KernelBridgeMode.ENFORCE`；
   - `mode` 为未知非法字符串 $\implies$ 记录 `log.warn` 并安全回退为 `KernelBridgeMode.OFF`。

| `hermes.harness.kernel.enabled` | `hermes.harness.kernel.mode` | 解析后 `effectiveMode` | 是否分配 `ExecutionSession` | 是否写入 `KernelBridgeReceipt` | 异常处置策略 |
|---|---|---|---|---|---|
| `false`（默认） | 任意值（`OFF`/`SHADOW`/`ENFORCE`） | **`OFF`** | 否（零分配） | 否 | 100% 短路跳过 |
| `true` | `OFF` | **`OFF`** | 否（零分配） | 否 | 100% 短路跳过 |
| `true` | `SHADOW` | **`SHADOW`** | 是（请求级隔离） | 是（记录影子结果） | **Fail-Open**（捕获异常写入凭单，原路径继续） |
| `true` | `ENFORCE` | **`ENFORCE`** | 是（请求级隔离） | 是（记录强制结果） | **Fail-Close**（能力缺失或护栏拒绝立即阻断） |

### 2.2 三态模式在 `AgentOrchestrator.executeAgent` 中的精确代码执行流

#### 法医级切入点定位
在 `AgentOrchestrator.java` 中：
- 行 473–481：已完成 `effectiveVoucher` 与 `evidenceCollector` 创建；
- 行 488–543：已完成短期记忆（`MemoryManager`）、关系库历史（`request.getHistoryList()`）、长期记忆召回的 `List<Message> messages` 组装；
- 行 547–556：已完成原生 `systemPrompt` 与 `plannerSupervision` 组装；
- 行 607–616：已完成原生 `ReActCycleGuard` 的 `guardedTools` 与 `guardedResolver` 包装；
- 行 571 为 `planAndSolve(...)` 调用，行 619 为 `multiAgentOrchestrationService.executeIfEnabled(...)`，行 645 为 `ReactAgent.builder()`。

**关键法医发现（切入时序修正）**：
在 `AgentOrchestrator.java` 原代码中，行 571 的 `planAndSolve` 位于行 607 的 `guardedTools` 构建**之前**！如果 `ENFORCE` 模式在行 617 才执行 `CapabilityContextAssembler` 校验，那么当 `planAndSolve` 启用时，能力违约请求会先泄漏进 `planAndSolve` 调用大模型！
因此，必须在进入 `planAndSolve`（行 571）之前完成 `ReActCycleGuard` 包装与 `harnessKernelBridge.bridgeExecution(...)` 介入：
1. **`OFF` 模式**：`harnessKernelBridge.isOff()` 为 `true`，完全不调用桥接逻辑，零对象分配。
2. **`SHADOW` 模式**：
   - 解析 `CapabilityTag`；
   - 构建当前请求已挂载的 `Map<String, ToolCallback> mountedTools` 与有效历史列表 `List<ChatMessage> effectiveHistory`；
   - 在 `try-catch (Throwable t)` 保护块中创建请求级 `HermesHarnessKernel.ExecutionSession`，调用 `session.prepare(...)` 生成 `PreparedExecution`，并记录 `KernelBridgeReceipt(preparedSuccess=true, fallbackTriggered=false, ...)`；
   - 若 `session.prepare(...)` 抛出 `IllegalArgumentException`（如能力不匹配），捕获异常，记录 `log.warn` 并写入 `KernelBridgeReceipt(preparedSuccess=false, fallbackTriggered=true, errorCode="SHADOW_PREPARE_FAILED", errorMessage=t.getMessage())`；
   - **绝对铁律**：`SHADOW` 模式的 `BridgeDirective` 恒返回原有的 `systemPrompt`、原有的 `guardedTools`、原有的 `guardedResolver`，`failClosed = false`。
3. **`ENFORCE` 模式**：
   - 解析 `CapabilityTag`（若显式传入非法标签名，直接判定违约）；
   - 创建请求级 `HermesHarnessKernel.ExecutionSession`，调用 `session.prepare(effectiveVoucher, capabilityTag, effectiveRagContexts, mountedTools, effectiveHistory)`；
   - **若校验失败抛出 `IllegalArgumentException` 或 `!session.coldBuffer().canArchive()`**：
     - 记录 `KernelBridgeReceipt(effectiveMode=ENFORCE, preparedSuccess=false, enforced=true, failClosed=true, errorCode="CAPABILITY_CONTRACT_VIOLATION", ...)`；
     - 返回 `BridgeDirective.failClose(errorCode, errorMessage, ...)`；
     - `AgentOrchestrator` 检测到 `directive.failClosed()` 为 `true` 时，立即在 `planAndSolve` 与 `ReactAgent` 之前执行 Fail-Close 闭环（产出 `ExecutionMode.FAIL_SAFE` 证据并向 `emitter` 发送 `ErrorEvent` 完成流，0 次大模型调用）；
   - **若校验通过**：
     - 使用 `prepared.systemPrompt()`（若有 `plannerSupervision` 则同样拼接 `plannerSupervision`）替换 `systemPrompt`；
     - 使用 `session.wrapToolCallbacks(guardedTools, evidenceCollector)` 与 `session.wrapToolResolver(guardedResolver, evidenceCollector)` 替换 `guardedTools` 与 `guardedResolver`；
     - 向 `evidenceCollector.addCompletionListener(...)` 注册单次完成回调：当本次请求在任意出口调用 `evidenceCollector.finalizeEvidence()` 时，自动触发 `session.completeAndRecordTrajectory(evidenceRecord)` 并刷新 `KernelBridgeReceipt`！

---

## 三、跨请求状态隔离（Request-Scoped Isolation）审计与架构设计

### 3.1 共享可变组件导致跨请求状态污染的法医级推演
在 Stage 6 的 `HermesHarnessKernel.java`（行 39、41、50–51）中，`coldBuffer` 与 `cycleAutomaton` 是 `HermesHarnessKernel` 的成员变量。若在生产环境中将单个 `HermesHarnessKernel` 实例多线程共享于多个并发 `ChatRequest`：

1. **故障一：`LocallyDenseCycleAutomaton` 跨请求滑动窗口混叠（Cross-Request Window Aliasing）**
   - **假阳性误杀（False Positive）**：设窗口 $W=6$、阈值 $K=2$。并发请求 `req-1`（用户甲查询 `"北京天气"`）与并发请求 `req-2`（用户乙查询 `"北京天气"`）同时在第 1 步调用 `weatherQuery({"city":"北京"})`。由于 `sha256(toolName + "\u001f" + canonicalArgs)` 仅取决于工具名与参数，两个独立请求的首次调用在共享 `LocallyDenseCycleAutomaton` 中产生完全相同的指纹，导致 `req-2` 的第 1 次合法工具调用使 `frequency` 达到 $2 \ge K$，当场被误判为死循环拦截！
   - **假阴性漏检（False Negative）**：若 `req-1` 陷入恶意死循环，但并发请求 `req-2..req-8` 的正常工具调用交错涌入共享 `Deque<String> window`，会将 `req-1` 的重复指纹不断从队首 `removeFirst()` 挤出窗口，导致 `req-1` 的局部致密循环永远达不到阈值 $K$，守卫彻底失效！
2. **故障二：`ReversibleRingColdBuffer` 跨会话/跨租户记忆污染（Cross-Session Memory Contamination）**
   - `ReversibleRingColdBuffer` 的 `hot`、`warm`、`cold`、`archive` 为可变集合。多请求共享时，请求 A 的 Episode 会将请求 B 的热记忆挤入 `cold/archive`；且调用 `warmCentroid()` 计算单位超球面 $\mathbb{S}^{1023}$ 外在弦质心或调用 `restoreNearest()` 恢复记忆时，会混入其他用户/其他 Bot 的上下文向量，造成跨租户隐私泄漏与流形质心偏移。
3. **故障三：Bot 级个性化 `HermesHarnessVoucher` 阈值失配**
   - 每个请求通过 `effectiveVoucher.guardrails()` 拥有独立的 `maxCycleSteps` 与 `loopThreshold`，通过 `effectiveVoucher.memoryContext()` 拥有独立的 `shortTermWindowSize` 与 `longTermRecallTopK`。全局单例无法按请求凭单动态调整窗口容量。

### 3.2 组件状态分类矩阵（无状态共享 vs 请求级隔离）

| 组件类名 | 状态属性 | 生命周期作用域 | 线程安全与隔离策略 |
|---|---|---|---|
| `HypersphericalManifoldProjector` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例，所有请求安全共享 |
| `HoareTripleToolGuard` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例，状态由方法参数 `ExecutionState` 传入 |
| `CapabilityContextAssembler` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例 |
| `HermesHarnessAssembler` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例 |
| `TrajectoryAttributionAnalyzer` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例 |
| `AlgebraicHarnessMutator` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例 |
| `ParetoFrontierComposer` | 纯函数无状态 | **全局共享 (Singleton)** | `HermesHarnessKernel` 持有一个不可变实例 |
| `LocallyDenseCycleAutomaton` | **有状态可变** (`window`, `counts`) | **请求级隔离 (Request-Scoped)** | 每次 `kernel.openSession(requestId, voucher)` 独立创建新实例 |
| `ReversibleRingColdBuffer<String>` | **有状态可变** (`hot`, `warm`, `cold`, `archive`) | **请求级隔离 (Request-Scoped)** | 每次 `kernel.openSession(requestId, voucher)` 独立创建新实例 |
| 步数 / Token / 成本 / 违约列表 | **有状态可变** (`AtomicInteger`, `AtomicLong`) | **请求级隔离 (Request-Scoped)** | 封装在 `HermesHarnessKernel.ExecutionSession` 内部 |
| `KernelBridgeReceipt` 缓存 | **有界可变观测表** | **Bridge 级有界 LRU Map** | `Collections.synchronizedMap` + `LinkedHashMap` (LRU 上限 256)，按 `requestId` 键值隔离 |

---

## 四、`ChatRequest` 能力注入与 `ToolCallbackResolver` 包装细节规范

### 4.1 关键法医发现：`ChatRequest.getInputParams()` 与历史消息双源解析

#### 发现 1：`ChatRequest.getInputParams()` 是 JSON `String` 而非 `Map`
经核查 `AgentOrchestrator.java`（行 473）与 `NodeUtils.java`（行 20–35），Protobuf 生成的 `ChatRequest.getInputParams()` 和 `ChatRequest.getParameters()` 返回类型均为 **`String`（JSON 格式文本）**！
因此，`HarnessKernelBridge.resolveCapabilityTag(...)` **严禁写成 `request.getInputParams().get(...)`**，必须按如下确定性优先级解析：

1. **显式声明提取优先级**：
   - 先解析 `request.getInputParams()`（若非空且为合法 JSON 对象）：
     - 优先读取键 `"harness.capabilityTag"`；
     - 次优读取键 `"capabilityTag"`；
   - 若未找到，再解析 `request.getParameters()`（若非空且为合法 JSON 对象）：
     - 优先读取键 `"harness.capabilityTag"`；
     - 次优读取键 `"capabilityTag"`；
   - 若读取到非空字符串 `rawTag`：调用 `CapabilityTag.valueOf(rawTag.trim().toUpperCase(Locale.ROOT))`（若不匹配任何枚举常量，抛出 `IllegalArgumentException("Unsupported capabilityTag: " + rawTag)`，由 `SHADOW` Fail-Open 或 `ENFORCE` Fail-Close 处理）。

2. **未显式声明时的自动推断规则（Default Inference Rule）**：
   当请求未显式携带 `"harness.capabilityTag"` 或 `"capabilityTag"` 时，根据当前请求已装载的真实上下文自动推断：
   - 设 `hasRag = ragContexts != null && !ragContexts.isEmpty()`；
   - 设 `hasTools = mountedTools != null && !mountedTools.isEmpty()`；
   - 设 `hasHistory = effectiveHistory != null && !effectiveHistory.isEmpty()`；
   - 推断决策树：
     ```java
     if (hasRag && hasTools) {
         return CapabilityTag.COMPOSITE;
     }
     if (hasRag) {
         return CapabilityTag.RAG;
     }
     if (hasTools) {
         return CapabilityTag.TOOL;
     }
     if (hasHistory) {
         return CapabilityTag.HISTORY;
     }
     return CapabilityTag.PROMPT_ONLY;
     ```

#### 发现 2：历史消息双源对齐（`MemoryManager` 短期记忆 vs `request.getHistoryList()`）
在 `AgentOrchestrator.java`（行 488–543）中，会话历史优先从 `memoryManager.getShortTerm().getContext(sessionId, shortTermWindow)` 加载到 `List<Message> messages` 中；仅当短期记忆为空时才回退读取 `request.getHistoryList()`。
- **规范解法**：构建 `List<ChatMessage> effectiveHistory` 时：
  1. 若 `!request.getHistoryList().isEmpty()`，直接使用 `request.getHistoryList()`；
  2. 否则，若 `messages`（在拼接当前轮 `finalUserPrompt` 之前加载的历史 `List<Message>`）非空，将其映射为 `List<ChatMessage>`（`UserMessage` $\to$ `role="user"`, `AssistantMessage` $\to$ `role="assistant"`）传入 `CapabilityContextAssembler`。

### 4.2 `ENFORCE` 模式下工具 JSON 参数解析与四层守卫协同顺序

在 `ENFORCE` 模式下，对 `guardedTools` 与 `guardedResolver` 返回的 `ToolCallback` 进行包装。单次 `call(String toolInput)` 严格按 5 步执行：
1. `parseToolInput(toolInput)` $\to$ `(argsMap, canonicalJson)`；
2. `sessionAutomaton.inspect(toolName, canonicalJson)` $\to$ `Inspection(fingerprint, frequency, rejected)`；
3. `toolGuard.verifyBefore(toolName, argsMap, guardrails, contract, new ExecutionState(step, inspection.frequency(), estimatedTokens, estimatedCostCny))` $\to$ 若 `!beforeDecision.allowed()` 或 `inspection.rejected()`，向 `evidenceCollector` 记录 `success=false` 并返回结构化反例提示，**绝不调用底层工具**；
4. 委托内层 `ReActCycleGuard` 包装的 `original.call(toolInput)` 获取 `rawResult`；
5. `toolGuard.verifyAfter(rawResult, contract)` $\to$ 若 `!afterDecision.allowed()`，向 `evidenceCollector` 记录后置拦截 `success=false` 并返回脱敏反例提示，屏蔽敏感或损坏输出。

---

## 五、审计结论汇总

1. **零回归保证**：`AgentOrchestrator` 的全部 3 个构造器与所有既有公共方法签名保持 **0 修改**；通过 `private HarnessKernelBridge harnessKernelBridge = HarnessKernelBridge.disabled();` 与 `@Autowired(required = false) setHarnessKernelBridge(...)`，保证默认状态（`enabled=false, mode=OFF`）下 100% 旁路短路。
2. **三态契约严密**：`OFF` 零对象分配短路；`SHADOW` 旁路生成 `PreparedExecution` 与 `KernelBridgeReceipt` 且异常时 Fail-Open；`ENFORCE` 在进入 `planAndSolve` / `MultiAgent` / `ReactAgent` 前执行强能力校验，缺失能力立即 Fail-Close（发送 `ErrorEvent` 并产出 `FAIL_SAFE` 证据），通过时替换受控 `systemPrompt` 并用请求级神经符号守卫包装 `guardedTools` 与 `guardedResolver`。
3. **跨请求隔离完备**：通过 `HermesHarnessKernel.ExecutionSession` 将 `LocallyDenseCycleAutomaton`、`ReversibleRingColdBuffer` 与步数/预算计数器严格隔离在单次请求作用域内，彻底消除跨请求窗口混叠与跨租户记忆串扰；同时通过有界 LRU Map（默认容量 256）管理 `KernelBridgeReceipt`。
4. **Protobuf 与工具链细节对齐**：修复了 `ChatRequest.getInputParams()` 作为 JSON `String` 的解析方式、短期记忆 `List<Message>` 与 `request.getHistoryList()` 的双源历史对齐、以及四层守卫与 `HarnessEvidenceCollector` 协同顺序。
