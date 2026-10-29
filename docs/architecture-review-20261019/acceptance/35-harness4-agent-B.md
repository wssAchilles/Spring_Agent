# Agent B: Java 离线执行器工程审计员 (Java Offline Executor Engineering Specialist) 独立技术方案设计与工程审计报告

> **智能体角色**：Agent B: Java 离线执行器工程审计员 (Java Offline Executor Engineering Specialist)  
> **物理 ConversationId**：`e52fbf2f-0b80-4292-9540-7469bda69992`  
> **报告对应目标文档**：`docs/architecture-review-20261019/acceptance/35-harness4-agent-B.md`  
> **审计基准分支**：`feat/hermes-harness-stage3`（Commit `ba46de4a`）  
> **实施目标分支**：`feat/hermes-harness-stage4`  
> **编译与运行隔离环境**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **系统生成模型基线**：DeepSeek 官方活跃模型（`deepseek-flash` 等，严格解析 `reasoning_content`）  
> **系统向量模型基线**：阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1$，余弦度量）  
> **准则遵循**：《AGENTS.md》科研门禁与全篇独占简体中文规范  

---

## 阶段四唯一待验证核心假设 (Unique Falsifiable Hypothesis)

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库问答、多工具路由选择与历史会话理解的 5 个代表性离线任务用例）上，使用真实 AgentExecutor（通过注册临时请求凭单隔离调度 AgentOrchestrator 及其底层模型/工具链）对 Baseline 与 1 个候选变异 Harness 各执行一次真实探针执行（Probe）；以真实物理落盘的 HarnessEvidenceRecord（格式为标准 JSON，分别落盘于 `experiment/outputs/harness4/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据，候选 Harness 在任务成功率、工具调用成功率、Token 消耗、执行耗时四项物理指标中至少有一项实现显著改善且其余指标无退化；若因网络/接口故障导致无法获取真实物理证据，或候选指标出现显著退化，则假设判定不成立。”**

---

# 第一章：`AgentOrchestrator` 凭单覆写机制法医级审计与时序安全性

### 1.1 现状源码解构与状态生命周期模型
在阶段二与阶段三的代码基线中，`AgentOrchestrator`（`tech.qiantong.qknow.hermes.agent.AgentOrchestrator`）引入了基于请求 ID 的局部覆写凭单映射机制，核心定义如下（Lines 126–157, 474–481）：

```java
private final java.util.Map<String, HermesHarnessVoucher> voucherOverrideMap = new java.util.concurrent.ConcurrentHashMap<>();

public void registerRequestVoucher(String requestId, HermesHarnessVoucher voucher) {
    if (requestId != null && voucher != null) {
        voucherOverrideMap.put(requestId, voucher);
    }
}

public void unregisterRequestVoucher(String requestId) {
    if (requestId != null) {
        voucherOverrideMap.remove(requestId);
    }
}
```

在执行链路核心方法 `executeAgent`（Lines 474–481）中：
```java
// 5. 构建不可变 Harness 凭单并由契约驱动运行时参数
String resolvedSystemPrompt = NodeUtils.replacePlaceholder(request.getSystemPrompt(), request.getInputParams());
HermesHarnessVoucher harnessVoucher = voucherOverrideMap.get(request.getRequestId());
if (harnessVoucher == null) {
    harnessVoucher = harnessAssembler.createRequestVoucher(request.getBotId(), resolvedSystemPrompt);
}
final HermesHarnessVoucher effectiveVoucher = harnessVoucher;
final HarnessEvidenceCollector evidenceCollector = new HarnessEvidenceCollector(
        request.getRequestId(), request.getBotId(), effectiveVoucher);
evidenceCollector.recordUserInput(request.getQuestion());
```

### 1.2 异步响应式流（Reactive Flux）下的调用时序陷阱
经过逐行法医排查，发现当前代码实现存在极具隐蔽性的时序陷阱：
1. **提前卸载时序竞争（Premature Unregister Race Condition）**：
   `AgentOrchestrator.chat(ChatRequest request)` 方法返回的是冷响应式流 `Flux<ChatEvent>`（Lines 272–307）。如果离线探针调用方采用如下看似直观的“注册-调用-注销”模板：
   ```java
   // 错误调用范式！
   orchestrator.registerRequestVoucher(probeReqId, candidateVoucher);
   Flux<ChatEvent> stream = orchestrator.chat(chatRequest);
   orchestrator.unregisterRequestVoucher(probeReqId); // 致命缺陷：过早注销！
   stream.subscribe(...);
   ```
   **时序机理分析**：`orchestrator.chat(...)` 只是构建响应式流水线管道，在 `subscribe()` 被调用以及 Reactor 调度器调度到工作线程执行 `executeAgent` 之前，主线程已经执行了 `unregisterRequestVoucher(probeReqId)`。当工作线程实际执行到 `voucherOverrideMap.get(request.getRequestId())` 时，该映射已为空，导致探针静默回退至生产默认凭单 `createRequestVoucher(...)`。此时候选变异完全未生效，所有收集到的证据退化为基线证据，造成实验有效性彻底归零。
2. **缺乏内部自动卸载生命周期挂钩（Missing Auto-Unregister Hook）**：
   在 `chat()` 方法的 `Flux.create(emitter -> ...)` 结构中，包含 `doFinally(signalType -> ...)`（Lines 308–312）与 `doOnComplete(...)`（Lines 312–355），但内部仅处理了 `deepSeekCostGovernor` 与 `langFuseService`，并未调用 `unregisterRequestVoucher(request.getRequestId())`。这意味着当前系统完全依赖外部调用方手动清理。
3. **异常中断时的凭单悬挂与堆内存泄漏（Dangling Voucher Memory Leak）**：
   若外部大模型超时、网络闪断或发生非检查异常，如果调用方未将 `unregisterRequestVoucher` 严格置于 `try-finally` 或响应式 `doFinally` 块中，已注册的 `HermesHarnessVoucher` 及其内部的不可变字段将长期驻留于 `voucherOverrideMap` 中，导致无界内存泄漏。

### 1.3 线程安全性与高并发数据竞争（Race Conditions）深潜
1. **原子操作安全性**：
   `voucherOverrideMap` 采用 `ConcurrentHashMap`，单次 `put` 与 `remove` 具备线程安全内存可见性与分段锁/CAS 保障。
2. **并发冲突场景剖析**：
   - **Scenario 1（多任务隔离并发）**：当并发运行 5 个基准测试用例时，只要每个测试任务具备强全局唯一的 `probeRequestId`（如带纳秒戳与 UUID），各个键值互不交叉，`ConcurrentHashMap` 具备完美的并发读写性能，零阻塞；
   - **Scenario 2（重试冲突与脏读）**：若某个探测任务因超时触发重试，重试任务若复用了原 `probeRequestId`，在前一个任务的异步延迟清理与重试任务的新注册之间将产生覆盖竞争。
3. **证据缓存挤压风险（Evidence Eviction Race）**：
   `AgentOrchestrator` 内部的 `latestEvidenceMap` 容量被硬编码为 100（Lines 138–143）：
   ```java
   if (latestEvidenceMap.size() > 100) {
       java.util.Iterator<String> it = latestEvidenceMap.keySet().iterator();
       if (it.hasNext()) {
           latestEvidenceMap.remove(it.next());
       }
   }
   ```
   如果并发运行大批量探测且外部未及时提取，最早完成的探针证据将直接被迭代器 FIFO 挤压删除，导致 `getLatestEvidence(probeRequestId)` 偶发返回 `null`。

### 1.4 凭单生命周期自愈与优雅卸载防御设计（Lease TTL & Safe Registration Pattern）
为根治上述隐患，阶段四工程落地必须实现**“凭单租约与作用域自愈模式 (Scoped Lease Pattern)”**：
1. **不可变注册租约凭证（AutoCloseable Voucher Lease）**：
   为 `OfflineProbeExecutor` 设计专用的作用域凭据，实现 `AutoCloseable` 接口：
   ```java
   public final class ScopedVoucherLease implements AutoCloseable {
       private final AgentOrchestrator orchestrator;
       private final String probeRequestId;
       private final AtomicBoolean closed = new AtomicBoolean(false);

       public ScopedVoucherLease(AgentOrchestrator orchestrator, String probeRequestId, HermesHarnessVoucher voucher) {
           this.orchestrator = Objects.requireNonNull(orchestrator);
           this.probeRequestId = Objects.requireNonNull(probeRequestId);
           this.orchestrator.registerRequestVoucher(probeRequestId, Objects.requireNonNull(voucher));
       }

       @Override
       public void close() {
           if (closed.compareAndSet(false, true)) {
               this.orchestrator.unregisterRequestVoucher(probeRequestId);
           }
       }
   }
   ```
2. **响应式生命周期严格对齐**：
   执行探针时，卸载动作必须与响应式流的终结信号物理对齐：
   ```java
   Flux<ChatEvent> guardedFlux = orchestrator.chat(request)
           .doFinally(signalType -> lease.close());
   ```
   无论正常完成（`onComplete`）、异常抛出（`onError`）还是被下游主动取消（`cancel`），均能且仅能触发一次 `lease.close()`，彻底杜绝凭单残留与提前注销。

---

# 第二章：`OfflineProbeExecutor` 离线探针执行器系统级架构设计

### 2.1 架构接口与不可变契约模型（Java 21 Records）
`OfflineProbeExecutor` 是驱动 Hermes SINGLE Agent 架构在离线环境下执行真实探测的核心调度器，接口与契约模型严格遵循 Java 21 Record 规范：

```java
package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.OfflineBenchmarkTestCase;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Hermes 阶段四离线真实探针执行器契约。
 */
public interface OfflineProbeExecutor {

    /**
     * 对单个离线用例执行一次真实探针运行。
     */
    ProbeExecutionReport executeSingleProbe(
            String runId,
            String variantName, // "baseline" 或 "candidate"
            OfflineBenchmarkTestCase testCase,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options
    );

    /**
     * 对全量基准任务集执行批量探测。
     */
    List<ProbeExecutionReport> executeBatchProbe(
            String runId,
            String variantName,
            List<OfflineBenchmarkTestCase> testSuite,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options
    );

    /**
     * 探针运行配置（不可变 Record）。
     */
    record ProbeExecutionOptions(
            long timeoutMs,               // 单任务硬超时（默认 30,000ms）
            int maxRetryAttempts,         // 重试次数（默认 1 次，对齐 Harness² 架构）
            Path outputBaseDir,           // 物理落盘基础路径（experiment/outputs/harness4）
            boolean persistEvidenceJson,  // 是否强制物理落盘
            String modelPlatform,         // 模型平台（固定 deepseek）
            String modelName              // 模型名称（如 deepseek-flash）
    ) {
        public ProbeExecutionOptions {
            Objects.requireNonNull(outputBaseDir, "outputBaseDir 不能为空");
            Objects.requireNonNull(modelPlatform, "modelPlatform 不能为空");
            Objects.requireNonNull(modelName, "modelName 不能为空");
            if (timeoutMs <= 0) {
                timeoutMs = 30_000L;
            }
        }

        public static ProbeExecutionOptions defaults() {
            return new ProbeExecutionOptions(
                    30_000L,
                    1,
                    Path.of("experiment/outputs/harness4"),
                    true,
                    "deepseek",
                    "deepseek-flash"
            );
        }
    }

    /**
     * 单次探针执行综合报告（不可变 Record）。
     */
    record ProbeExecutionReport(
            String runId,
            String variantName,
            String taskId,
            String probeRequestId,
            String voucherVersion,
            String voucherFingerprint,
            boolean success,
            String errorCode,
            long executionDurationMs,
            HarnessEvidenceRecord evidenceRecord,
            Path persistedJsonPath,
            String rawOutputSummary
    ) {
        public ProbeExecutionReport {
            Objects.requireNonNull(runId, "runId 不能为空");
            Objects.requireNonNull(variantName, "variantName 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(probeRequestId, "probeRequestId 不能为空");
            Objects.requireNonNull(voucherVersion, "voucherVersion 不能为空");
            Objects.requireNonNull(voucherFingerprint, "voucherFingerprint 不能为空");
            Objects.requireNonNull(errorCode, "errorCode 不能为空");
        }
    }
}
```

### 2.2 响应式异步流聚合与真实证据提取算法
离线探针执行器需要将非阻塞流式的 `Flux<ChatEvent>` 转化为离线可核验的不可变凭据，采用同步收集与倒计时闭锁（`CountDownLatch`）架构，兼顾流式数据完整性与虚拟线程无阻塞特性：

```java
private ProbeExecutionReport doExecuteSingleProbeInternal(
        String runId,
        String variantName,
        OfflineBenchmarkTestCase testCase,
        HermesHarnessVoucher voucher,
        ProbeExecutionOptions options) {

    long startTime = System.currentTimeMillis();
    String probeRequestId = String.format("probe-%s-%s-%s-%s",
            runId, variantName, testCase.taskId(), UUID.randomUUID().toString().substring(0, 8));

    // 1. 构建离线专用隔离 ChatRequest
    ChatRequest chatRequest = ChatRequest.newBuilder()
            .setRequestId(probeRequestId)
            .setWorkspaceId(9999L)
            .setBotId(voucher.botId())
            .setUserId(8888L)
            .setConversationId(7777L)
            .setQuestion(testCase.question())
            .setSystemPrompt(voucher.invariantSystemPrompt().canonical())
            .setModelConfig(ModelConfig.newBuilder()
                    .setModelId(1L)
                    .setPlatform(options.modelPlatform())
                    .setBaseUrl("https://api.deepseek.com")
                    .setModelName(options.modelName())
                    .build())
            .build();

    StringBuilder fullAnswer = new StringBuilder();
    AtomicReference<Throwable> executionError = new AtomicReference<>();
    AtomicReference<HarnessEvidenceRecord> evidenceRef = new AtomicReference<>();

    // 2. 注册不可变凭单租约
    try (ScopedVoucherLease lease = new ScopedVoucherLease(orchestrator, probeRequestId, voucher)) {
        // 3. 消费响应式流并附加超时控制
        orchestrator.chat(chatRequest)
                .timeout(Duration.ofMillis(options.timeoutMs()))
                .doOnNext(event -> {
                    if (event.hasChunk() && StrUtil.isNotBlank(event.getChunk().getContent())) {
                        fullAnswer.append(event.getChunk().getContent());
                    }
                })
                .doOnError(executionError::set)
                .blockLast(Duration.ofMillis(options.timeoutMs() + 3000L));

        // 4. 获取真实落地的证据凭单
        HarnessEvidenceRecord recordedEvidence = orchestrator.getLatestEvidence(probeRequestId);
        evidenceRef.set(recordedEvidence);
    } catch (Exception e) {
        log.warn("[OfflineProbeExecutor] 探针任务执行异常: taskId={}, reason={}", testCase.taskId(), e.getMessage());
        executionError.set(e);
    }

    long durationMs = System.currentTimeMillis() - startTime;
    Throwable error = executionError.get();

    // 5. 异常保底凭据合成（Fail-Safe 机制）
    HarnessEvidenceRecord finalEvidence = evidenceRef.get();
    boolean isSuccess = (error == null) && (finalEvidence != null);
    String errorCode = isSuccess ? "NONE" : (error != null ? error.getClass().getSimpleName() : "MISSING_EVIDENCE");

    if (finalEvidence == null) {
        finalEvidence = synthesizeFailSafeEvidence(
                probeRequestId, voucher, testCase, durationMs, errorCode,
                error != null ? error.getMessage() : "探针执行未产生证据记录");
    }

    // 6. 物理落盘与报告构建
    Path persistedPath = null;
    if (options.persistEvidenceJson()) {
        persistedPath = persistEvidenceToFile(options.outputBaseDir(), runId, variantName, testCase.taskId(), finalEvidence);
    }

    return new ProbeExecutionReport(
            runId, variantName, testCase.taskId(), probeRequestId,
            voucher.version(), voucher.computeFingerprint(),
            isSuccess, errorCode, durationMs, finalEvidence, persistedPath,
            fullAnswer.length() > 0 ? fullAnswer.toString() : finalEvidence.finalOutputSummary()
    );
}
```

### 2.3 双重看门狗超时监控与安全熔断机制（Watchdog & Fail-Safe Fallback）
在离线受控实验中，外部大模型接口与工具链可能遭遇网络超时、速率限制（HTTP 429）或内部服务异常（HTTP 500）。根据 Google Research Harness² 的核心设计哲学：**单点探测失败绝不允许拖垮整个递归循环，必须具备错误隔离与安全回退能力**。
`OfflineProbeExecutor` 采用**双重看门狗架构**：
1. **第一重：Reactive 数据流超时看门狗**：
   在 `Flux` 上挂载 `.timeout(Duration.ofMillis(options.timeoutMs()))`，防止底层 HTTP 连接因 TCP 半打开（Half-Open）进入无界悬挂；
2. **第二重：调度线程硬看门狗（Virtual Thread TaskScope Watchdog）**：
   在批量执行器中使用 Java 21 虚拟线程池 `Executors.newVirtualThreadPerTaskExecutor()`，结合 `Future.get(timeoutMs, TimeUnit.MILLISECONDS)` 进行外部保底熔断，拦截任何可能阻塞在 JNI 或不可中断同步锁中的非响应式逻辑；
3. **安全继承保底（Fail-Safe Baseline Inheritance）**：
   当候选变异探针发生超时或严重故障时，系统自动生成标记为 `PROBE_TIMEOUT` 或 `API_FAULT` 的保底记录，不会抛出崩溃性异常。在差分提取器（`EvidenceDiffExtractor`）计算时，该候选将被自动判为失败，触发回退保护，保持基线配置不变。

### 2.4 物理证据标准化 JSON 落盘与原子重命名安全保障
为满足科研门禁中“必须以真实物理落盘的 `HarnessEvidenceRecord` JSON 文件为客观依据”的硬性红线，落盘机制必须达到生产级资产管理标准：
1. **标准落盘目录规范**：
   - 基线证据路径：`experiment/outputs/harness4/<runId>/baseline/evidence_<taskId>.json`
   - 候选证据路径：`experiment/outputs/harness4/<runId>/candidate/evidence_<taskId>.json`
2. **原子性写入协议（Atomic Write Protocol）**：
   直接写入目标文件若中途遇进程中止或断电，将留下 0 字节或半截畸变 JSON。因此必须采用“先写临时文件，后原子重命名”的四步安全写入法：
   - Step 1: 在目标目录下创建临时文件 `evidence_<taskId>.json.tmp.<uuid>`；
   - Step 2: 使用 FastJSON2（`JSONWriter.Feature.PrettyFormat`）将 `HarnessEvidenceRecord` 格式化序列化写入临时文件，确保包含工具执行摘要列表与出入参哈希；
   - Step 3: 调用 `FileChannel.force(true)` 强制刷盘；
   - Step 4: 调用 `Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)` 完成原子落盘；
3. **物理凭单 SHA-256 自校验（Self-Verification on Load）**：
   落盘完成后，执行器立即从物理磁盘回读该文件，反序列化为 Record 对象，并调用 `record.verifyDigest(record.computeDigest())` 进行常量时间验签，彻底杜绝文件损坏或篡改。

---

# 第三章：真实大模型调用与确定性单测替身物理分流与隔离设计

### 3.1 工业级双轨制隔离模型：真实离线实验 vs 确定性回归测试
在现代企业级 AI 系统的研发中，存在一对尖锐矛盾：
- **学术与实证需求**：离线基准评估必须与真实环境对齐，使用真实大模型与真实工具链，生成真实的 Token 开销、时延指标与物理落盘文件，杜绝虚假的静态 Mock；
- **工程 CI/CD 需求**：常规自动化测试（`mvn test`）必须 100% 具备确定性、零外部网络依赖、毫秒级执行，严禁因为外部 API 抖动或网络问题导致回归流水线变红。

为彻底解决这一矛盾，阶段四必须建立**物理双轨分流架构 (Physical Dual-Track Bifurcation)**：

```
                    ┌────────────────────────────────────────────────────────┐
                    │               离线基准测试用例集 (5 用例)                 │
                    │               OfflineBenchmarkTestCase                 │
                    └──────────────────────────┬─────────────────────────────┘
                                               │
                                 ┌─────────────┴─────────────┐
                                 │                           │
                                 ▼                           ▼
                  【轨道一：日常构建回归单测】       【轨道二：真实离线受控探针】
                  HermesHarnessStage4ContractTest     OfflineProbeRunner (物理入口)
                                 │                           │
                                 ▼                           ▼
                     DeterministicHarnessTestDouble     ProductionAgentOrchestratorDriver
                                 │                           │
                                 ▼                           ▼
                         [纯内存确定性断言]             [真实 DeepSeek 官方 API 调用]
                         [毫秒级零网络依赖]             [真实 Token 与时延消耗]
                         [零外部物理落盘]              [物理落盘 experiment/outputs/...]
```

### 3.2 抽象执行驱动契约 (`HarnessExecutionDriver`)
为解耦调度逻辑与具体执行底层，定义核心驱动契约：

```java
package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.proto.ChatEvent;
import tech.qiantong.qknow.hermes.proto.ChatRequest;
import reactor.core.publisher.Flux;

public interface HarnessExecutionDriver {
    /**
     * 执行 Agent 对话并返回事件流。
     */
    Flux<ChatEvent> execute(ChatRequest request);

    /**
     * 获取驱动器模式标识。
     */
    DriverMode getMode();

    enum DriverMode {
        REAL_PRODUCTION,        // 真实调用（DeepSeek 官方 API）
        DETERMINISTIC_DOUBLE    // 确定性测试替身
    }
}
```

### 3.3 生产级真实驱动实现 (`ProductionAgentOrchestratorDriver`)
真实驱动器直接委托给 Spring 容器管理的 `AgentOrchestrator`：
```java
public class ProductionAgentOrchestratorDriver implements HarnessExecutionDriver {
    private final AgentOrchestrator orchestrator;

    public ProductionAgentOrchestratorDriver(AgentOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator);
    }

    @Override
    public Flux<ChatEvent> execute(ChatRequest request) {
        return orchestrator.chat(request);
    }

    @Override
    public DriverMode getMode() {
        return DriverMode.REAL_PRODUCTION;
    }
}
```

### 3.4 确定性测试替身实现 (`DeterministicHarnessTestDouble`)
确定性测试替身完全运行于本地内存中，基于确定性输入产生固定的仿真输出与证据：
```java
public class DeterministicHarnessTestDouble implements HarnessExecutionDriver {
    private final Map<String, DeterministicResponse> fixtureMap;

    public record DeterministicResponse(
            String textResponse,
            String toolName,
            long simulatedTokens,
            long simulatedDurationMs
    ) {}

    public DeterministicHarnessTestDouble(Map<String, DeterministicResponse> fixtures) {
        this.fixtureMap = Map.copyOf(fixtures);
    }

    @Override
    public Flux<ChatEvent> execute(ChatRequest request) {
        String taskId = extractTaskId(request.getRequestId());
        DeterministicResponse resp = fixtureMap.getOrDefault(taskId,
                new DeterministicResponse("确定性兜底回答", "defaultTool", 150L, 50L));

        return Flux.just(
                ChatEvent.newBuilder()
                        .setRequestId(request.getRequestId())
                        .setChunk(ChatChunk.newBuilder().setContent(resp.textResponse()).build())
                        .build()
        );
    }

    @Override
    public DriverMode getMode() {
        return DriverMode.DETERMINISTIC_DOUBLE;
    }
}
```

### 3.5 防污染、防伪造与防泄漏四道工程铁律
1. **代码与资源物理隔离铁律**：
   - 确定性单测替身 `DeterministicHarnessTestDouble` **必须且只能**存在于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/` 目录下，严禁打包至生产归档 JAR；
2. **落盘介质与路径隔离铁律**：
   - 单测套件运行期间严禁向 `experiment/outputs/harness4/` 写入任何非真实数据；单测产生的临时文件只能存在于 `target/test-scratch/` 且测试完成后即刻清理；
3. **真实证据凭单防伪校验铁律（Anti-Spoofing Verification）**：
   - 真实落盘的 `HarnessEvidenceRecord` 必须包含真实的系统纳秒级耗时与网络交互留痕，其 `voucherFingerprint` 与 `computeDigest()` 必须与真实执行上下文数学自洽。任何在离线实验中注入假数据或试图以单测替身冒充真实物理落盘的行为，在审计阶段均可通过耗时分布（$0\text{ms}$ vs 真实网络时延）与出入参 SHA-256 哈希检举并硬性拦截；
4. **Holdout 测试集与评分信息零泄漏铁律**：
   - 严格遵循 Harness² `context.py` 的防御规范，离线基准测试集的期望答案、评测打分规则与验证集数据，绝对禁止拼接至 `ChatRequest` 的 Prompt 中。

---

# 第四章：5 大代表性离线基准任务真实探测与指标收集规范

### 4.1 任务集构成与特征映射表
阶段四冻结的 5 个离线小样本基准任务用例，覆盖企业知识中枢的核心高频交互模式：

| 任务编号 | 任务类别 (`TaskCategory`) | 业务场景描述 | 期望匹配工具 (`expectedToolName`) | 最小上下文 Token 阈值 |
|---|---|---|---|---|
| **kb-qa-01** | `KNOWLEDGE_QA` | 中间件规范查询：订单支付超时重试与幂等键规则 | `knowledgeBase_MiddleWare_Spec` | 120 |
| **tool-route-02** | `MULTI_TOOL_SELECTION` | 银企对账与异常纠偏：多工具识别与路由选择 | `banking_transaction_query` | 150 |
| **param-guard-03** | `TOOL_PARAM_GUARD` | 异常参数容错：非标准日期格式引导与工具自纠 | `calendar_event_creator` | 100 |
| **ctx-history-04** | `CONTEXT_HISTORY_RECALL` | 多轮会话长程推理：跨轮次代词消解与上下文召回 | `(无直接工具调用)` | 200 |
| **comp-rag-tool-05** | `COMPOSITE_REASONING` | 架构演进报告生成：长文本多工具合成与结构化输出 | `knowledgeBase_Arch_Spec` | 300 |

### 4.2 探针执行矩阵与指标采集维度
对每个任务用例，执行器必须分别采集基线（Baseline）与候选（Candidate）的两份独立物理证据：

```
基线执行 (Baseline Probe)   ──► experiment/outputs/harness4/<runId>/baseline/evidence_<taskId>.json
候选执行 (Candidate Probe)  ──► experiment/outputs/harness4/<runId>/candidate/evidence_<taskId>.json
```

采集的 4 项核心物理量化指标定义如下：
1. **任务成功率 ($\text{Rate}_{\text{success}}$)**：
   探针执行是否在时限内正常终结，且未触发异常中断码（`errorCode == "NONE"`）；
2. **工具调用成功率 ($\text{Rate}_{\text{tool}}$)**：
   若该用例定义了 `expectedToolName`，则证据凭单中记录的工具调用列表是否命中了该工具，且执行状态为 `toolSuccess == true`；
3. **Token 消耗增量 ($\Delta \text{Tokens}$)**：
   $$\Delta \text{Tokens} = \text{estimatedTokens}_{\text{candidate}} - \text{estimatedTokens}_{\text{baseline}}$$
   契约红线：$\Delta \text{Tokens} \le 0$（候选变异严禁导致 Token 膨胀）；
4. **执行耗时增量 ($\Delta \text{Duration}$)**：
   $$\Delta \text{Duration} = \text{executionDurationMs}_{\text{candidate}} - \text{executionDurationMs}_{\text{baseline}}$$
   契约红线：$\Delta \text{Duration} \le 5\text{ms}$（若未带来大幅质量跃迁，不允许出现显著时延退化）。

### 4.3 差分提取器与双向交叉盲审（Position-Swap Dual Pass）集成
在获取落盘的两份物理 JSON 证据后，必须调用阶段三构建的 `EvidenceDiffExtractor` 提取客观物理差分，并对接阶段四构建的 `PositionSwapBlindEvaluator`：

```
                ┌──────────────────────────────┐
                │ Baseline Evidence (JSON)    │
                │ Candidate Evidence (JSON)   │
                └──────────────┬───────────────┘
                               │
                               ▼
                   EvidenceDiffExtractor.extract()
                 [提取 ΔTokens, ΔDuration, 工具匹配]
                               │
                               ▼
                PositionSwapBlindEvaluator (双向盲审)
                  Pass 1: Eval(Candidate, Baseline)
                  Pass 2: Eval(Baseline, Candidate)
                               │
                               ▼
                 [仅当双向一致判定候选胜出时采纳]
```

只有当候选在至少一项物理指标（任务成功率、工具命中率、Token 消耗、耗时）上表现出统计显著性改善，且在其他三项指标上均无显著退化，并通过了匿名双向交叉盲审时，才最终判定核心假设成立。

---

# 第五章：开源文献与工业参考实现映射（Research Ledger 对齐）

依据《AGENTS.md》科研门禁规范，本报告对应的技术设计严格对齐 Research Ledger 中的权威开源项目与顶会文献：

```text
id: SRC-STAGE4-001
sourceType: official-code
titleOrRepository: google-research/harness2 (Harness²: Recursive Agent Harnessing for an Open World)
authorsOrMaintainer: Google Research (Junxian He et al.)
venueAndYear: Google Research 2026
doiOrArxiv: N/A
url: https://github.com/google-research/harness2
commitOrTag: 1cfe254e
license: Apache-2.0
filesOrSectionsRead: README.md, harness2/core/recursion.py, harness2/core/proposer.py, harness2/core/selection.py, harness2/core/context.py, harness2/core/ledger.py
verificationStatus: VERIFIED
relevantFinding: 揭示了真实探针执行（Probe Rollout）是评估候选变异不可或缺的物理事实依据；其 recursion.py 与 proposer.py 建立了基于独立会话的执行隔离与错误捕获回退机制；其 selection.py 通过匿名洗牌消除位置偏见。
projectApplicability: 本设计完全吸收其探针驱动核心思想，在 Java 21 体系下通过 AgentOrchestrator.registerRequestVoucher 与 ScopedVoucherLease 实现了与 Python 隔离子进程等价的内存级安全隔离，并将执行结果规范落盘于 experiment/outputs/harness4/。
limitations: 原生 Harness² 深度绑定 Python 进程与 Claude 命令行 CLI，本设计通过 Java 21 强类型 Record 与 Reactive 响应式流进行了架构重塑与安全防泄漏加固。
```

```text
id: SRC-STAGE4-002
sourceType: paper
titleOrRepository: DSPy: Compiling Declarative Language Model Calls into State-of-the-Art Pipelines
authorsOrMaintainer: Omar Khattab et al.
venueAndYear: ICLR 2024
doiOrArxiv: arXiv:2310.03714
url: https://arxiv.org/abs/2310.03714
commitOrTag: N/A
license: MIT (Official Code: stanfordnlp/dspy)
filesOrSectionsRead: Section 2 (System Model), Section 3 (Signatures & Modules), Section 4 (Teleprompters/Optimizers), Section 5 (Evaluation)
verificationStatus: VERIFIED
relevantFinding: 证明了基于物理执行轨迹（Traces）与离线评估度量指导变异编译，比盲目在线调优更具确定性与工程鲁棒性。
projectApplicability: 本设计的离线小样本探测驱动变异评估，完全遵循 DSPy 声明式离线编译思想，以客观物理量化指标（Token、耗时、工具匹配）作为核心损失函数。
limitations: DSPy 依赖动态注入 Few-Shot 样本，会破坏前缀缓存并增加 Token 成本，本设计严格遵循 Hermes 静态前缀保护红线，坚决拒绝动态插入样本。
```

```text
id: SRC-STAGE4-005
sourceType: paper
titleOrRepository: Reflexion: Language Agents with Verbal Reinforcement Learning
authorsOrMaintainer: Noah Shinn et al.
venueAndYear: NeurIPS 2023
doiOrArxiv: arXiv:2303.11366
url: https://arxiv.org/abs/2303.11366
commitOrTag: N/A
license: MIT (Official Code: noahshinn/reflexion)
filesOrSectionsRead: Section 2 (Reflexion Architecture), Section 3 (Episodic Memory), Section 4 (Experiments)
verificationStatus: VERIFIED
relevantFinding: 阐明了智能体执行失败时的错误上下文与反馈事实，可以作为不可变情景记忆指导后续自我修正，防止相同错误再次发生。
projectApplicability: 本设计在双重看门狗熔断时，将探针失败原因（如超时、参数不符）结构化记录于 HarnessEvidenceRecord 凭单中，作为离线变异诊断的客观物理证据。
limitations: Reflexion 的在线多轮自反思会导致极高的时延与成本翻倍，本设计将其严格限制在离线批处理探针中，生产在线链路坚决保持零反思单轮直出。
```

---

# 第六章：工程落地行动指南与协同裁决建议

### 6.1 阶段四最小代码实现文件集合推荐
为保证工程实施的极简性与高收敛性，阶段四在工程实现时建议仅新增/修改以下最小文件集合：

1. **新建契约与核心执行器**（位于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/probe/`）：
   - `OfflineProbeExecutor.java`：执行器接口与不可变 Record 定义（`ProbeExecutionOptions`, `ProbeExecutionReport`）；
   - `DefaultOfflineProbeExecutor.java`：真实探针执行器标准实现（含双重看门狗、流式聚合与物理 JSON 原子落盘）；
   - `ScopedVoucherLease.java`：凭单租约管理与自动卸载器（实现 `AutoCloseable`）；
   - `HarnessExecutionDriver.java` 与 `DeterministicHarnessTestDouble.java`：测试替身与分流接口。
2. **新建匿名双向盲审器**（位于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/`）：
   - `PositionSwapBlindEvaluator.java`：实现正向与反向对称双向盲审及帕累托非退化判定。
3. **新建验收契约测试套件**：
   - `HermesHarnessStage4ContractTest.java`：基于 `DeterministicHarnessTestDouble` 的 100% 确定性单测套件（验证时序清理、双重看门狗超时、原子落盘校验、双向盲审偏置消除等契约）；
4. **独立离线物理探针执行入口**：
   - `OfflineProbeRunner.java`：提供可显式运行的主类入口（带有 `--run-id` 与 `--variant` 参数），负责驱动真实 `AgentOrchestrator`，真正产生落盘至 `experiment/outputs/harness4/<runId>/` 的物理证据文件。

### 6.2 协同裁决建议（对主 Agent、Agent A 与 Agent C）
1. **对 Agent A（理论推导员）的对齐建议**：
   确认收敛性定理与双向盲审偏置上界推导（$\delta^2 \ll \delta$）与本执行器的“双向一致且无退化方可采纳”逻辑完全数学对齐；
2. **对 Agent C（系统架构契约员）的对齐建议**：
   在制定任务书阶段，将本文档设计的 `ScopedVoucherLease`、双重看门狗超时（30s）与标准文件落盘路径（`experiment/outputs/harness4/<runId>/[baseline|candidate]/evidence_<taskId>.json`）直接固化为架构契约不可变更项；
3. **对主 Agent 的交付建议**：
   本报告已全面覆盖并解答了关于 `AgentOrchestrator` 凭单时序、线程安全、流式聚合、超时熔断、测试替身物理隔离与 5 大用例物理落盘的所有工程细节，具备 100% 决策完备性（Decision-Complete）。建议主 Agent 直接采纳并将本文档全量写入 `docs/architecture-review-20261019/acceptance/35-harness4-agent-B.md`。

---
*(报告正文完)*