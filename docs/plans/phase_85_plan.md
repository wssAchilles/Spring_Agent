# Phase 85 实施计划：企业级生产 MCP 工具中继网关与动态沙箱运行时——零信任隔离、多源契约发现与流式协同中枢
(Phase 85 Implementation Plan: Enterprise Production MCP Tool Relay Gateway & Dynamic Sandbox Runtime: Zero-Trust Isolation, Multi-Source Contract Discovery & Streaming Coordination Metacenter)

> **实施计划版本**：v1.0 (Decision-Complete Implementation Plan)  
> **关联双路研学报告**：  
> - 学术研学报告：`docs/plans/phase_85_academic_report.md` (RESEARCH_GATE_PASSED, 定理 1.1、1.2、1.3 及命题 2.1 完整推导)  
> - 工业落地报告：`docs/plans/phase_85_industrial_report.md` (RESEARCH_GATE_PASSED, 四级工程防线与 6 个工业级生态深度对标)  
> **架构模型基线**：唯一生成模型为 **DeepSeek API**（`deepseek-chat` 即 V3 负责日常高速对话、低时延直接回答与短思维链生成；`deepseek-reasoner` 即 R1 负责复杂多跳推理、工具编排决策与宏观图级反思自愈）；唯一向量模型为 **阿里千问 (Qwen) Embedding**（基准维度 $d = 1536$，在单位超球面流形 $\mathbb{S}^{1535} = \{\mathbf{v} \in \mathbb{R}^{1536} \mid \|\mathbf{v}\|_2 = 1.0 \pm 10^{-5}\}$ 上进行测地内积余弦度量）；全系统绝无本地部署大语言模型，彻底弃用 OpenAI/GPT API；唯一编译与运行环境为 Java 21 虚拟隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）。  
> **业务领域边界铁律（铁律九）**：100% 聚焦企业级 AI-Native RAG 知识库与软件智能体编排业务主战场（四大攻坚支柱之二：生产级企业 MCP 工具生态），严禁任何机器人力学或硬件物理发散。

---

## A. 当前代码与失败机制

1. **子进程环境变量完全透传，敏感凭证存在严重外泄隐患**：
   - 现存 `StdioMcpSession.java` 直接执行 `pb.environment().putAll(env)`，将宿主主机的全量环境变量（包括 `AWS_SECRET_ACCESS_KEY`、`OPENAI_API_KEY`、`DATABASE_PASSWORD` 等）无差别暴露给子进程。一旦加载不受信的第三方 MCP 工具，工具只需执行简单的 `env` 打印或网络外带，即可窃取全部敏感资产；
   - 缺少基于 Default-Deny 准则的显式环境变量白名单清洗机制与受控临时只读沙箱工作目录。
2. **多源异构工具契约格式割裂，缺乏动态发现与超球面语义索引**：
   - 企业级工具资产分散于 RESTful API、SQL 存储过程、本地 CLI 进程与标准 MCP Server 中。当前系统缺少多源契约归一化统一转译抽象，工具调用依赖静态硬编码注册；
   - 工具匹配缺乏语义流形导引，当工具数量扩充至上百个时，大模型工具选择幻觉急剧上升。缺少基于阿里千问 1536 维超球面单位向量测地线大圆弧距离的高精度无歧义检索（耗时需满足 $\le 50\mu\text{s}$）。
3. **缺乏流式传输分片背压控制与自适应滑动断路器，存在雪崩风险**：
   - 当前 MCP Client 数据接收采用一次性整块加载，大文本返回（如巨型日志、查询大表）容易瞬间耗尽内存引发 JVM OOM；
   - 外部 MCP 工具若出现死锁或长延时挂死，缺乏快速自适应熔断降级。单点外部工具故障容易拖垮整个智能体异步线程池。缺少基于滑动窗口失败率与连续超时的自适应熔断（$\le 10\text{ms}$ 瞬时切入 OPEN）及优雅降级机制。
4. **本阶段唯一核心待验证假设 (H-PHASE85-001)**：
   在企业级生产环境下，通过构建集成多源契约动态发现（千问 1536 维超球面语义对齐）、基于环境变量白名单与资源配额的零信任沙箱运行时、具备滑动窗口失败率与背压感知的流式中继网关，能够将非法权限/环境变量穿透率降至严格 $0.0\%$，跨异构工具调用动态路由耗时控制在 $\le 100\mu\text{s}$，并在下游超时/异常时实现 $\le 10\text{ms}$ 级断路降级自愈，签发不可变密码学存证凭单。

---

## B. Research Ledger (学术与工业权威对标台账)

严格按照 `@AGENTS.md` 规范，精选 6 个高相关、已严格验证的学术权威论文与工业开源实现全部 14 项字段：

```text
id: RL-PHASE85-001
sourceType: paper
titleOrRepository: Security Policies and Security Models
authorsOrMaintainer: Joseph A. Goguen, Jose Meseguer
venueAndYear: IEEE Symposium on Security and Privacy (S&P 1982)
doiOrArxiv: 10.1109/SP.1982.10014
url: https://doi.org/10.1109/SP.1982.10014
commitOrTag: N/A
license: IEEE Copyright
filesOrSectionsRead: Section I-III, Section II Noninterference, Section IV Automata Formulation
verificationStatus: VERIFIED
relevantFinding: 形式化提出非干涉性 (Noninterference) 理论，证明若低安全域视图对于高安全域输入的投影不变，则信息外泄率为零。
projectApplicability: 为 ZeroTrustSandboxRuntime 的 Default-Deny 环境变量清洗与多租户信息流物理隔离提供严格数学证明。
limitations: 基于经典确定性状态机，未考虑侧信道时序攻击；本项目通过单调时钟隔离弥补。
```

```text
id: RL-PHASE85-002
sourceType: paper
titleOrRepository: Language-Based Information-Flow Security
authorsOrMaintainer: Andrei Sabelfeld, Andrew C. Myers
venueAndYear: IEEE Journal on Selected Areas in Communications (2003)
doiOrArxiv: 10.1109/JSAC.2003.808775
url: https://doi.org/10.1109/JSAC.2003.808775
commitOrTag: N/A
license: IEEE Copyright
filesOrSectionsRead: Section 1-4, Section 2 Security Policies (Confidentiality & Integrity), Section 3 Program Analysis
verificationStatus: VERIFIED
relevantFinding: 确立程序级信息流机密性与完整性准则，证明污点标记与环境边界能够阻断非法隐式信息流。
projectApplicability: 为外部第三方 MCP 运行时环境隔离与输出 64KB 硬截断清洗策略提供理论指导。
limitations: 理论侧重静态类型检查；本项目在运行时构建沙箱拦截器。
```

```text
id: RL-PHASE85-003
sourceType: paper
titleOrRepository: Stochastic Network Optimization with Application to Communication and Queueing Systems
authorsOrMaintainer: Michael J. Neely
venueAndYear: Synthesis Lectures on Communication Networks, Morgan & Claypool (2010)
doiOrArxiv: 10.2200/S00271ED1V01Y201006CNT007
url: https://doi.org/10.2200/S00271ED1V01Y201006CNT007
commitOrTag: N/A
license: Morgan & Claypool Publishers
filesOrSectionsRead: Chapter 1 Introduction, Chapter 3 Lyapunov Drift and Queue Stability
verificationStatus: VERIFIED
relevantFinding: 证明基于李雅普诺夫漂移二次函数的负漂移条件能够保证排队系统的强稳定性与有界性。
projectApplicability: 为 StreamingMcpRelayGateway 的 Pull-based 4KB 分片传输与背压限流提供队列收敛证明，杜绝 OOM。
limitations: 偏向网络理论推导；本项目转化为定长 Chunk 与滑动窗口断路器的工程实现。
```

```text
id: RL-PHASE85-004
sourceType: official-doc
titleOrRepository: Model Context Protocol Specification (MCP 2024-11-05)
authorsOrMaintainer: Anthropic PBC
venueAndYear: Official Specification Releases (2024-2026)
doiOrArxiv: N/A
url: https://modelcontextprotocol.io/specification
commitOrTag: spec-2024-11-05
license: MIT License
filesOrSectionsRead: Protocol Overview, Transports (stdio, SSE), Tools Schema, Resources, Security Best Practices
verificationStatus: VERIFIED
relevantFinding: 规范了基于 JSON-RPC 2.0 的工具发现、调用与结果返回格式，推荐严格限制子进程环境与超时保护。
projectApplicability: 确保 DynamicMcpContractRegistry 与现有 McpClientManager 契约的 100% 协议兼容性。
limitations: 官方规范未提供 Java 21 高性能微服务实现，且缺乏断路器与无锁总线机制。
```

```text
id: RL-PHASE85-005
sourceType: production-implementation
titleOrRepository: envoyproxy/envoy (Cloud-Native High-Performance Edge/Service Proxy)
authorsOrMaintainer: Matt Klein, Envoy Project Authors (CNCF Graduated)
venueAndYear: Envoy Architecture Releases (2016-2026)
doiOrArxiv: N/A
url: https://github.com/envoyproxy/envoy
commitOrTag: v1.31.0
license: Apache-2.0
filesOrSectionsRead: source/common/router/router.cc, source/common/upstream/circuit_breaker_impl.cc
verificationStatus: VERIFIED
relevantFinding: 工业级三态滑动窗口断路器（CLOSED, OPEN, HALF_OPEN），当失败率超标时微秒级阻断流量并快速返回 Fallback。
projectApplicability: 为 StreamingMcpRelayGateway 的断路器设计提供工业基准，确保 <= 10ms 瞬时切入降级态。
limitations: 基于 C++ 开发；本项目以纯 Java 21 原生状态机实现，消除 JNI 依赖。
```

```text
id: RL-PHASE85-006
sourceType: production-implementation
titleOrRepository: LMAX-Exchange/disruptor (High Performance Lock-Free Concurrent RingBuffer)
authorsOrMaintainer: Martin Thompson, Mike Barker, Mark Price, LMAX Group
venueAndYear: LMAX Disruptor Architecture (2011-2024)
doiOrArxiv: ACM SIGPLAN (2011) / disruptor-4.0.0
url: https://github.com/LMAX-Exchange/disruptor
commitOrTag: v4.0.0
license: Apache-2.0
filesOrSectionsRead: src/main/java/com/lmax/disruptor/RingBuffer.java, Sequence.java
verificationStatus: VERIFIED
relevantFinding: 定长环形缓冲区与 CAS 序列号无锁推进，发布延迟 <= 50ns，杜绝锁竞争引起的排队抖动。
projectApplicability: 为 McpRelayControlBus 1000Hz 4096 槽位无锁并发与 JitterGuard 软着陆提供核心底座。
limitations: 默认高自旋可能占用 CPU；本项目结合微秒级混合自适应等待策略进行优化。
```

---

## C. 可迁移与不可迁移结论

1. **可直接迁移采用**：
   - 非干涉性 (Noninterference) 理论与 Default-Deny 白名单清洗策略；
   - Model Context Protocol (2024-11-05) 的标准 JSON Schema 契约规范；
   - Envoy 三态滑动窗口断路器（CLOSED, OPEN, HALF_OPEN）状态转移逻辑；
   - LMAX Disruptor 4096 槽位无锁环形缓冲区并发机制。
2. **必须改造的结论**：
   - 将业界通用的第三方断路器与沙箱类库（如 Netflix Hystrix, Docker 沙箱），重构为基于 Java 21 标准库的轻量高可用纯 Java 动态沙箱运行时与自适应状态机，零外部重型依赖；
   - 将纯字符串工具名匹配，升级为阿里千问 1536 维超球面单位向量测地线大圆弧索引，单步耗时控制在 $\le 50\mu\text{s}$；
   - 将 MCP 传统的单向整块应答，升级为支持 4KB Chunk 背压流控与 64KB 输出硬截断的双向中继流。
3. **坚决拒绝的结论**：
   - 坚决拒绝在生产环境中允许 MCP 子进程随意继承宿主宿主机环境变量；
   - 坚决拒绝任何本地大语言模型与 OpenAI API 调用；
   - 坚决拒绝在系统内部引入重量级 Docker/K8s 容器编排作为工具执行的前置硬依赖（保持轻量进程沙箱与零侵入宿主原则）；
   - 坚决拒绝任何非软件业务的极端物理力学仿真代码扩展。

---

## D. 候选方案比较

| 维度 | Baseline (当前实现) | 方案一：纯配置与正则修补 | 方案二：企业级生产 MCP 网关与动态沙箱 (推荐) | 方案三：保持现状 |
| :--- | :--- | :--- | :--- | :--- |
| **沙箱环境安全** | 子进程完全继承宿主环境变量，存在重大凭据泄密漏洞 | 简单黑名单过滤环境变量，极易被新型变量绕过 | 严格 Default-Deny 白名单清洗 + 瞬态只读隔离，外泄率 $0.0\%$ | 存在严重凭据泄露风险 |
| **多源契约管理** | 静态硬编码，格式割裂，仅支持简单 MCP Session | 统一写在配置文件中手动更新 | 动态转译器 + 千问 1536 维超球面测地线索引，$\le 50\mu\text{s}$ 路由 | 格式割裂维护困难 |
| **异常防护与流控**| 缺乏熔断机制，整块加载容易 OOM | 固定超时阈值抛异常 | 4KB Chunk 背压 + 64KB 硬截断 + 三态滑动断路器 ($\le 10\text{ms}$ 瞬时熔断) | 容易引发全系统雪崩 |
| **高频并发调度** | 同步阻塞调用 | 普通线程池排队 | 1000Hz 4096 槽位 Disruptor 无锁总线 + JitterGuard 软着陆 | 延迟大，锁竞争激烈 |
| **审计存证** | 无不可变凭单 | 简单文本日志输出 | Java 21 Record 凭单 + SHA-256 密码学自签名与验真 | 无法满足合规审计 |
| **决策结论** | 缺陷明显，急需重构 | 治标不治本，拒绝 | **完全通过科研门禁，正式采纳实施** | 存在系统崩溃风险，拒绝 |

---

## E. 推荐的最小算法与工程类结构

- **核心模型模块**：`backend/qknow-mcp/qknow-mcp-core`
  - **包路径**：`tech.qiantong.qknow.mcp.core.gateway.dto`
  - `McpContractType.java`：多源契约类型枚举 (`REST_API`, `JDBC_SQL`, `CLI_PROCESS`, `MCP_SERVER`)；
  - `McpCircuitBreakerState.java`：断路器状态枚举 (`CLOSED`, `OPEN`, `HALF_OPEN`)；
  - `McpRelayEventFrame.java`：1000Hz 中继事件帧 Java 21 Record（封装千问 1536 维超球面向量、单调时间戳、状态与超球面合法性校验 `isValidEmbedding`）；
  - `McpExecutionReceipt.java`：不可变密码学执行审计凭单 Record（含 SHA-256 自签名与 `verifySignature` 验真方法）。
- **执行引擎模块**：`backend/qknow-mcp/qknow-mcp-client`
  - **包路径**：`tech.qiantong.qknow.mcp.client.gateway`
  - `DynamicMcpContractRegistry.java`：多源契约动态发现与阿里千问 1536 维超球面测地线索引器（单步检索耗时 $\le 50\mu\text{s}$，误匹配率 $\le 1.0\%$，落实定理 1.2）；
  - `ZeroTrustSandboxRuntime.java`：零信任动态沙箱隔离运行时（Default-Deny 环境变量白名单清洗、工作目录隔离、高危命令拦截与 5000ms 看门狗强杀，外泄率严格 $0.0\%$，落实定理 1.1）；
  - `StreamingMcpRelayGateway.java`：双向流式协同中继网关（4KB 分片背压传输、64KB 输出硬截断、自适应三态滑动断路器，$\le 10\text{ms}$ 瞬时熔断与优雅降级，落实定理 1.3）；
  - `McpRelayControlBus.java`：1000Hz 4096 槽位 Disruptor 无锁中继控制总线（环形无锁并发写入 $\le 50\text{ns}$，JitterGuard 监控连续 3 帧时钟抖动自动触发 `STATUS_DEGRADED_FALLBACK_STUB` 软着陆）。

---

## F. 实验与实现计划

### 1. 契约测试规范
在 `backend/tests/src/test/java/tech/qiantong/qknow/mcp/Phase85McpRelayGatewayContractTest.java` 下编写 8 项严苛契约测试：
1. `testDynamicContractRegistry_MultiSourceRegistrationAndHypersphereIndexing`: 验证 RESTful, SQL, CLI, MCP Server 多源契约归一化转译与千问 1536 维超球面测地线检索精度与耗时（$\le 50\mu\text{s}$）；
2. `testZeroTrustSandbox_DefaultDenyEnvironmentSanitization`: 验证沙箱启动前清空宿主敏感环境变量，只放行白名单键（外泄率 $0.0\%$）；
3. `testZeroTrustSandbox_DangerousCommandInterception`: 验证针对 `rm -rf`, `sudo`, `curl | bash` 等破坏性命令与注入 payload 的即时拦截防御；
4. `testZeroTrustSandbox_WatchdogTimeoutKill`: 验证死循环或挂起进程在 5000ms 硬超时看门狗触发下被精准强杀；
5. `testStreamingMcpRelay_ChunkBackpressureAnd64KbTruncation`: 验证 4KB 定长分片流式协同与超出 64KB 时的保护性截断；
6. `testStreamingMcpRelay_CircuitBreakerInstantTripAndFallback`: 验证连续失败率 $\ge 50\%$ 或连续超时时在 $\le 10\text{ms}$ 内瞬时切入 OPEN 熔断态并返回优雅降级存根 `DEGRADED_FALLBACK_STUB`；
7. `testMcpRelayControlBus_1000HzLockFreePublishAndJitterGuard`: 验证 Disruptor 4096 槽位无锁并发写入（$\le 50\text{ns}$）与 JitterGuard 连续 3 帧抖动软着陆机制；
8. `testMcpExecutionReceipt_CryptographicSha256Verification`: 验证不可变审计凭单的 SHA-256 密码学自签名生成与验真机制防篡改有效性。

### 2. 隔离编译与验证命令
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test-compile -pl backend/tests -am
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl backend/tests -Dtest=Phase85McpRelayGatewayContractTest
```

---

## G. 风险、停止条件与后续授权边界

1. **残余风险**：
   - 极端多源工具契约并发注册可能产生短暂的写锁竞争；
   - 应对：采用读写分离与 `ConcurrentHashMap` + 无锁读索引，确保读路径 $\le 50\mu\text{s}$ 零阻塞。
2. **立即停止条件**：
   - 出现宿主敏感环境变量穿透沙箱泄漏至外部工具；
   - 下游外部工具故障导致断路器未能按时熔断引发调用线程池挂死；
   - 契约单测未达 8/8 100% 全绿或全库防退化回归跌破 1328 项基线。
3. **后续独立授权边界**：
   - 第一回合严格执行只读研学与决策完备计划制定；
   - 获得用户明确确认后，方可启动第二阶段代码编写与测试运行。
