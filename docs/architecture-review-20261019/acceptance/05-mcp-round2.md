# 第二轮验收：MCP 修复复核报告

- 任务：task-17（MCP 第二轮验收，第一轮复核员执行）
- 基线：`HEAD = 1ff190d0`（`fix(ci): 修复CI自动装配依赖缺失与测试套件断言对齐`），工作区 clean（`git status --porcelain` 空）
- 本轮新增提交：`54fea59b`（fix(core) 深度闭环）、`1ff190d0`（fix(ci) 自动装配依赖 + 单测桩）
- 自证材料：`docs/architecture-review-20261019/verify/06-deep-fix-resolution.md`（由 `54fea59b` 引入，`git log -- 该文件` 仅一条）
- 方法：只读静态审计（`git show` / `git diff` / grep / sed）；未运行 mvn、未跑测试、未启动服务
- 证据等级：**已验证**＝源码/配置行直接证明；**推断**＝多行逻辑串联、未运行验证；**未知**＝需运行或上下文加载确认

---

## 1 结论总表

| 编号 | 复核项 | 54fea59b 声明/动作 | HEAD(1ff190d0) 实际 | 裁决 |
|---|---|---|---|---|
| A | 4 个 Provider 假数据 | 全部改回 `CallToolResult.error` | 4/4 仍为 `error`（未被后续提交改动） | **已修复** |
| B | Phase46 契约测试 | 断言 error + 给 KB Provider 配 delegate | 断言已改（4 处 `isError`）；`:203` 配 delegate；`@Test` 计数 10 | **已修复（静态核验；未执行）** |
| C | ToolNodeBO 错误映射 | 新增 `containsKey("error")` 兜底 + adapter==null 返回 failure | `containsKey("error")` 保留（`:148`）；**adapter==null 被 1ff190d0 改回 mock SUCCESS**（`:132-134`） | **部分修复 + 新增回归** |
| D | 自动装配规范 | `@AutoConfiguration` + `@ConditionalOnMissingBean`；移除 `SemanticToolRegistry @Component` | 均已落实（客户端/服务端两处）；同名 Bean 双源已解决 | **已修复（客户端）** |
| E | 服务端去孤岛 | 服务端自动装配 | `qknow-server` 仍无 `qknow-mcp-server` 依赖 → 配置类不加载；且无 transport/端点 | **未修复** |
| F | 语义工具向量 | 未声明修复（自证报告 MCP1~4 却称闭环） | 控制面无 `EmbeddingModel` Bean（hermes 包被扫描排除）→ 仍注入 null；空索引仍返回全量；投影仍传 null | **未修复** |
| G | stdio 凭据隔离 | `clear()+白名单`、HOME 沙箱、代理白名单 | 已落实；但沙箱目录未创建、固定共享、缺 CA/locale 变量 | **部分修复（实质改善）** |
| H | GitHub MCP 超时/开关/版本 | 未声明 | 异步注册无超时/无开关；`npx -y @modelcontextprotocol/server-github` 未固定版本 | **未修复** |
| I | 协议版本 | 未声明 | 仍 2024-11-05（3 处）/ 2025-03-26（1 处）双轨 | **未修复** |
| J | 自证报告一致性 | 称 R4/MCP1~4 闭环、"100%" | 被 `1ff190d0` 部分推翻，报告未更新 | **报告与 HEAD 不一致** |

一句话裁决：**Provider 假数据（R4）真实修复；自动装配规范化在客户端成立、服务端仍不生效；语义向量仍未修复；而 `1ff190d0` 在 `54fea59b` 之后 53 分钟又把 ToolNodeBO 的"拒绝假成功"改回"mock SUCCESS"，重新引入伪成功，且方向与 `54fea59b` 完全相反。**

---

## 2 R4：四个 Provider 与 ToolNode 映射

### 2.1 Provider：已修复（已验证）

| Provider | HEAD 行为 | 行号 |
|---|---|---|
| CodeSandbox | 无委托 → `CallToolResult.error("受限代码沙箱执行器未配置或环境未就绪")` | `MSRV/provider/CodeSandboxMcpProvider.java:78` |
| DataAgent | 无执行器 → `error("未找到数据源 [%s] 或底层 SQL 执行器未配置就绪")` | `MSRV/provider/DataAgentMcpProvider.java:113` |
| KnowledgeBase | 无委托 → `error("知识库 [Corpus=%d] 底层向量检索服务未绑定或尚未就绪")` | `MSRV/provider/KnowledgeBaseMcpProvider.java:97` |
| KnowledgeGraph | 无委托 → `error("底层 Neo4j 图数据库连接未就绪或未配置执行器委托")` | `MSRV/provider/KnowledgeGraphMcpProvider.java:80` |

`CallToolResult.error(...)` 构造 `isError=true`（`MCORE/model/CallToolResult.java:16-18`），与 `text(...)` 的 `isError=false`（`:12-14`）语义正确区分。**R4 的 Provider 层已闭环。**

### 2.2 Phase46 契约测试：已改（静态核验，未执行）

- KB：`:109-110` `assertTrue(result.isError())` + 断言"底层向量检索服务未绑定或尚未就绪"；
- KG：`:133`；DataAgent：`:155`；Sandbox：`:183`；
- 适配器链路：`:203` `kbProvider.setSearchDelegate(params -> CallToolResult.text(...))` 后构造 `McpSpringAiToolCallbackAdapter`，验证委托注入后的正常链路；
- `@Test` 计数 = 10（`grep -c "@Test"`），与自证"10/10"数量一致；**执行结果未知**（本任务禁止运行测试）。

### 2.3 ToolNodeBO：错误映射增强但仍留后门

- 增强（有效）：`KB/service/flow/bo/ToolNodeBO.java:146-148` 现同时识别 `isError==true`、`status==ERROR/FAILURE`、以及 `resObj.containsKey("error")`；命中后返回 `failure(...)`（`:155-156`）。这补上了 `McpToolAdapter.executeTool` 以 `{"error": ...}` JSON 返回失败（如客户端未连接、Schema 校验失败）的场景，是真实改进。
- 后门（回归）：`:132-134` 当 `mcpToolAdapter == null` 时返回
  `{"status":"SUCCESS","tool":"...","message":"Executed in standalone mock mode"}`，
  随后 `:164` 置 `output["status"]="SUCCESS"`、`:166-170` 返回节点 `SUCCESS`。该 JSON **不含 `error` 键、`isError` 缺省 false、status 为 SUCCESS**，因此完美绕过 `:146-148` 的全部判定。

---

## 3 本轮关键回归：`1ff190d0` 把"拒绝假成功"改回"mock SUCCESS"

### 3.1 方向相反的直接证据（已验证）

| 版本 | `ToolNodeBO` adapter==null 分支 |
|---|---|
| `54fea59b`（19:56） | `log.error("MCP 工具适配器未初始化，拒绝假成功执行")` → `return NodeRunResultBO.failure(..., "MCP 工具适配器未初始化，拒绝伪造成功执行")`（`git show 54fea59b -- ToolNodeBO.java`） |
| `1ff190d0`（20:49） | `log.debug("...回退为单体模式执行桩")` → `callResult = "{\"status\":\"SUCCESS\",...}"`（`git show 1ff190d0 -- ToolNodeBO.java`；HEAD 行号 `:132-134`） |

提交信息自述："完善 ToolNodeBO 在未注入适配器时的单体执行桩保证 ReAct 熔断测试稳定运行"——即**为让测试变绿而恢复伪成功**，与 `54fea59b` 及自证报告 R4 的"坚决避免假成功"（`06-deep-fix-resolution.md:70`）直接矛盾。

### 3.2 可达性（推断）

- `KB/service/flow/factory/NodeFactory.java:39-40` 对 `McpToolAdapter` 使用 `@Autowired(required = false)` → **适配器缺席被设计为可容忍**，该分支不是死代码而是"设计允许的路径"；
- `ToolNodeBO` 构造器（`:29-39`）不校验 adapter 非空；
- 控制面主应用由 `HermesConfiguration`（`qknow-server`）提供 adapter，通常非空；但任何 adapter Bean 缺失的上下文（测试片段、未来拆分应用、Bean 条件失效）都会落到该分支并**静默伪成功**。

### 3.3 危害

1. **绕过了同批次刚补的失败识别**：mock JSON 的 `status=SUCCESS` 与无 `error` 键，使 `:146-148` 的高级判定失效；
2. **下游节点误判**：Condition/LLM/Reply 节点按 SUCCESS 继续执行，最终回答可能声称"工具已执行/已查询"；
3. **可观测性污染**：工作流运行记录为 SUCCESS，熔断/重试/告警不触发；
4. **契约自相矛盾**：`Phase46McpContractTest` 断言 Provider 层 `isError=true`，而 `module-kb` 的 `KbFlowServiceToolNodeTest.java:31` 用 **null adapter** 构造 ToolNodeBO 并断言 `SUCCESS`（`:39-40`）→ 两层测试对"工具不可用应如何表现"给出相反答案；后者正是本次回归的直接驱动。

### 3.4 判定与修复建议

**新增回归（P0/P1，取决于对 adapter 缺席的运行时概率）**。最小修复：回退 `:132-134` 为 `failure(...)`，同时把 `KbFlowServiceToolNodeTest` 改为注入 mock `McpToolAdapter`（而非 null）来测正常路径，另加一条 "adapter==null → FAILED" 的断言；如确需单机演示，用显式 profile/开关 + 返回 `isError=true` 或 `"mock":true` 且 `status=FAILED`，禁止默认伪成功。

### 3.5 同提交相邻风险（超出 MCP 范围，仅提示）

`1ff190d0` 同时删除了 `KbFlowServiceImpl` 中 `wakeSuspendedWithLock` 失败分支的短路返回（`git show 1ff190d0 -- KbFlowServiceImpl.java`），使 CAS 唤醒失败后继续 `loadCheckpoint` 并可能抛异常/重复恢复；与本报告 MCP 主题无关，但属同提交引入的语义回退，建议由 DAG 验收方复核。

---

## 4 自动装配：规范已补，服务端仍未生效

### 4.1 客户端：规范达标（已验证）

- `MCLI/client/config/McpClientAutoConfiguration.java:13` `@AutoConfiguration`；`:14` `@ConditionalOnClass(McpClientManager.class)`；`:17-21` `mcpClientManager` + `@ConditionalOnMissingBean`；`:23-27` `semanticToolRegistry` + `@ConditionalOnMissingBean`；
- `MCLI/client/routing/SemanticToolRegistry.java:14` 已去掉 `@Component`（现仅 `@Slf4j`），同名 Bean 双源**已解决**；
- `SemanticToolRegistry` 的唯一消费者 `ToolRagFilter`（`routing/ToolRagFilter.java:16-25`）为 `@Component` 且构造注入该类型，由自动装配 Bean 满足依赖，不构成缺失。
- 编译证据：`qknow-mcp/qknow-mcp-client/target/classes/tech/qiantong/qknow/mcp/client/config/McpClientAutoConfiguration.class` 存在（2026-10-20 20:26），说明 `spring-boot-autoconfigure` 可达。

### 4.2 两个残留问题（P2）

1. **依赖未显式声明**：`qknow-mcp-client/pom.xml` 的 dependencies 中**没有** `spring-boot-autoconfigure`，当前靠 `qknow-ai → spring-boot-starter-web → spring-boot-autoconfigure` 传递可用；`1ff190d0` 只给 `qknow-mcp-server/pom.xml:26-29` 补了该依赖。传递依赖一旦被排除即编译失败，建议显式声明。
2. **自动装配类仍位于组件扫描范围内**：两类都在 `tech.qiantong.qknow.mcp.*.config`，而 `QKnowApplication.java:21-26` 扫描 `tech.qiantong`（仅排除 hermes）。`@AutoConfiguration` 是 `@Configuration` 的元注解 → 会被组件扫描再次发现（"扫描 + SPI"双路径）。当前因同一配置类、同一 `@Bean` 方法而被 Spring 去重，未再现上一轮的同名 Bean 覆盖风险，但违反 Spring Boot "自动装配类不得纳入组件扫描"的规范，建议把配置类移到 `...mcp.autoconfigure` 包或在该应用中排除 `tech\\.qiantong\\.qknow\\.mcp\\..*\\.config`。

### 4.3 服务端：仍完全未生效（未修复，已验证）

- `qknow-server/pom.xml` 中 `grep "qknow-mcp"` 结果为空；全仓 `qknow-mcp-server` 引用仅：`tests/pom.xml:90`（test）、`qknow-mcp/qknow-mcp-client/pom.xml:45`（test）、以及 `qknow-mcp/pom.xml:19`（module 声明）。`1ff190d0` 只改 `qknow-mcp-server/pom.xml` 与 `tests/pom.xml`，**未给控制面加依赖**。
- 因此 `MSRV/.../McpServerAutoConfiguration.java`（`:16-29`，已规范为 `@AutoConfiguration`+`@ConditionalOnClass`+`@ConditionalOnMissingBean`）**在控制面运行时完全不加载**，`mcpServerRegistry` Bean 不存在。
- 即使将来加载：`:22-28` 只 `new McpServerRegistry(...)` 并注册 4 个无委托 Provider，**不启动任何 transport**（`MSRV/transport/StdioServerEnterpriseTransport.java:54-59` 与 `MSRV/transport/InMemoryMcpSession.java:21` 均无实例化点），`qknow-mcp-server` 内也无 `@RestController/@*Mapping`（grep 无命中）→ 平台仍**不是** MCP Server，外部 client 无法连接。

### 4.4 孤岛是否消除

- `McpClientManager`：消费者仍只有新自动装配与死代码 `MCLI/adapter/McpSpringAiToolCallbackAdapter`（该 adapter 本身无装配）→ 从"无 Bean"变为"有 Bean 但无业务调用"；
- `SemanticToolRegistry`：消费者 `ToolRagFilter`（亦无消费者）→ 仍孤岛。
- 结论：**客户端"去孤岛"仅完成到"Bean 可注入"，未接到任何真实工具调用链**；服务端孤岛未动。

---

## 5 语义工具向量：控制面仍为 null（未修复）

### 5.1 本轮新发现：存在合规的 Qwen 1536 EmbeddingModel Bean，但控制面拿不到

- `HP/memory/MemoryConfiguration.java:24-36`：`@Bean EmbeddingModel embeddingModel(...)` 用 `OpenAiEmbeddingModel` 指向 DashScope 兼容端点，`model=text-embedding-v3`、`dimensions=1536`（`:33-34`）——与"唯一 Qwen 1536 维"基线一致，是本项目唯一真实 embedding Bean。
- 但该配置类位于 `tech.qiantong.qknow.hermes.memory`，而**控制面启动类显式排除** `tech\\.qiantong\\.qknow\\.hermes\\..*`（`QKnowApplication.java:23-25`）→ 控制面上下文**没有** `EmbeddingModel` Bean；
- 全仓无 `@Import(MemoryConfiguration.class)`/spring.factories 注册（`grep "MemoryConfiguration"` 除自身外无命中）；
- 唯一激活处是 `HermesApplication`（`qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:17-21` 扫描 `tech.qiantong.qknow.hermes`）。而 `ToolNodeBO`/工作流执行在控制面（module-kb）。
- 因此：`McpToolAdapter.java:63` 的 `@Autowired(required=false) EmbeddingModel` 在控制面注入 null → `:171` 分支永不进入 → `:178` `indexTool(..., null)` → 索引向量仍为空。

### 5.2 检索与投影侧仍未修

- `HP/mcp/governance/McpToolSemanticRetriever.java:117-118`：`toolVectors.isEmpty()` → 直接返回**全部工具**（非 Top-K、非相似度排序）；
- `HP/tool/discovery/ToolDiscoveryService.java:77`：向 `McpTwoPhaseProjectionGovernor` 仍传 `null` 向量 → 投影仍为全零向量退化；
- 检索/投影方法在控制面无新增消费者（第一轮结论未变）。

### 5.3 判定与最小修复

**未修复**（"接入了字段，但没有可注入 Bean，也没有检索消费者"）。最小修复路径（本轮新增可执行建议）：
1. 在控制面 `HermesConfiguration` 增加 `@Import(MemoryConfiguration.class)`，或直接注入/复刻该 1536 维 Qwen `EmbeddingModel` Bean；
2. `McpToolAdapter.indexTool` 传入真实向量，且 `McpToolSemanticRetriever.java:73` 维度不符时 fail-fast（不要仅 warn）；
3. `:117-118` 空索引禁止返回全量（返回空 + 告警或降级为显式 `tools/list` 全量标记）；
4. `ToolDiscoveryService.java:77` 换真实 query 向量，并补一条"真实向量下 recall@5"的集成测试。

---

## 6 stdio 环境隔离与 GitHub MCP

### 6.1 stdio：实质改善，但未完全闭环

`MCLI/client/transport/StdioEnterpriseTransport.java:82-101`：
- `:84-85` 保留 `PATH`；`:86-89` `HOME` 重定向为 `${java.io.tmpdir}/qknow-mcp-sandbox`；`:90-91` 保留 `TMPDIR`；`:92-95` 新增 6 个代理变量白名单；`:97` `clear()`；`:98` 写入安全环境；`:99-101` 合并显式配置环境。
- **已解决**：宿主 `~/.ssh`、`~/.npmrc` 等家目录凭据不再暴露（上一轮 R05 的核心风险面）；企业代理变量可透传。
- **残留**：
  1. 沙箱 HOME 目录**未创建**（全仓 `grep "qknow-mcp-sandbox"` 仅 `:88` 一处，无 `Files.createDirectories`）→ 依赖 `npx/npm` 自行创建，异常场景下可能失败（推断）；
  2. 固定共享路径（非 per-server/per-launch）→ 多个不可信 MCP server 共享同一 HOME，后者可写入 `~/.npmrc` 影响前者（跨 server 投毒面，P2）；
  3. 未透传 `LANG/LC_ALL`、`NODE_EXTRA_CA_CERTS`/`SSL_CERT_FILE`/`NODE_TLS_REJECT_UNAUTHORIZED` → 企业自签 CA/非 UTF-8 环境下 `npx` 可能失败（推断）；
  4. `PATH` 原样继承，若宿主 PATH 含可写目录仍存在 PATH 劫持面（低）。

### 6.2 GitHub MCP：仍无开关/超时/版本固定（未修复）

- `HP/mcp/McpToolAdapter.java:89-100`：`@PostConstruct` 内 `CompletableFuture.runAsync`（common pool），future 未保存、无 `orTimeout`、无取消；失败仅 `log.warn`（`:97`）；
- `:206-210`：命令固定为 `npx -y @modelcontextprotocol/server-github`，**未固定版本**（供应链不可复现）；
- 唯一"开关"是环境变量存在性（`:90-91`），无 `qknow.mcp.github.enabled` 等配置项；
- 异步注册与首请求存在竞态（启动后立即调用工具可能拿到空工具集）。

---

## 7 协议版本：仍未对齐（已验证）

| 位置 | 版本 |
|---|---|
| `MCLI/McpClientManager.java:94,119` | 2024-11-05 |
| `MCORE/model/InitializeResult.java:15` | 2024-11-05 |
| `HP/mcp/StdioMcpClient.java:30` | 2024-11-05 |
| `HP/mcp/HttpMcpClient.java:197` | 2025-03-26 |

官方规范当前版本为 2026-07-28（<https://modelcontextprotocol.io/specification/>，抓取 2026-10-19）；本轮两个提交均未触碰版本常量，也仍未校验 server 返回版本。**未修复**。

---

## 8 自证报告与 HEAD 的一致性核验

`docs/architecture-review-20261019/verify/06-deep-fix-resolution.md`（`54fea59b` 引入）关键声明 vs HEAD：

| 报告声明 | 行号 | HEAD 事实 | 判定 |
|---|---|---|---|
| R4"已彻底根除…`ToolNodeBO` 拦截异常" | `:21`、`:70` | Provider/Phase46 成立；但 `ToolNodeBO:132-134` 在下一提交被改回 mock SUCCESS | **部分失实（已被 1ff190d0 推翻）** |
| "MCP1~4 已闭环修复" | `:29` | MCP1 仅客户端达标（服务端不加载）；MCP2 成立；MCP3 未修；MCP4 部分 | **过度声明** |
| "系统可信度与安全性达到 100%" | `:71` | 无证据支撑，且存在上述回归 | **不可接受表述** |
| Phase46 10/10 | `:132` | `@Test` 计数 10 一致；执行结果未验证 | **数量一致；执行未知** |
| 报告未提及 `KbFlowServiceToolNodeTest` 的 null-adapter SUCCESS 断言 | — | 该测试（`KbFlowServiceToolNodeTest.java:31,39-40`）与本轮回归互为因果 | **测试口径自相矛盾** |

结论：自证报告在提交时点部分成立，但 **`1ff190d0` 之后未更新，已与 HEAD 不一致**；MCP1~4 的"闭环"表述应降级为"MCP2 已修、MCP1 客户端达标/服务端未生效、MCP4 部分、MCP3 未修"。

---

## 9 新增/残留风险清单

| 编号 | 级别 | 风险 | 证据 | 建议 |
|---|---|---|---|---|
| V2-01 | **P0** | ToolNodeBO adapter==null 伪成功回归（1ff190d0 反向修改） | `ToolNodeBO.java:132-134,164`；`git show 54fea59b` vs `git show 1ff190d0`；`KbFlowServiceToolNodeTest.java:31,39-40` | 回退为 `failure`；测试改为注入 mock adapter；如需 mock 必须 `isError=true`/显式开关 |
| V2-02 | **P1** | 服务端自动装配仍不生效，平台不是 MCP Server | `qknow-server/pom.xml` 无依赖；`tests/pom.xml:90`；`qknow-mcp-client/pom.xml:45`（test）；`McpServerAutoConfiguration.java:16-29`；无 transport/端点 | 加依赖 + 实现 Streamable HTTP 端点 + 鉴权 + 开关；否则文档明确"未启用" |
| V2-03 | **P1** | 语义向量未修复：控制面无 EmbeddingModel Bean、空索引返回全量、投影传 null | `McpToolAdapter.java:63,171-178`；`QKnowApplication.java:23-25`；`MemoryConfiguration.java:24-36`；`McpToolSemanticRetriever.java:117-118`；`ToolDiscoveryService.java:77` | `@Import(MemoryConfiguration.class)` + fail-fast + 空索引不返回全量 + 真实向量 recall 测试 |
| V2-04 | **P1** | GitHub MCP 无开关/超时/版本固定，异步注册竞态 | `McpToolAdapter.java:89-100,206-210` | 固定包版本 + `orTimeout` + `qknow.mcp.github.enabled` + 健康指标 |
| V2-05 | **P2** | 沙箱 HOME 固定共享且未创建；缺 CA/locale 变量 | `StdioEnterpriseTransport.java:86-95` | per-launch 唯一目录 + `createDirectories` + 按需 CA 变量 |
| V2-06 | **P2** | 自动装配类仍在组件扫描包内（双重发现）；`mcp-client` 未显式声明 `spring-boot-autoconfigure` | `McpClientAutoConfiguration.java:1-15`；`SemanticToolRegistry.java:1-14`；`qknow-mcp-client/pom.xml` | 移包/排除扫描；显式声明依赖 |
| V2-07 | **P2** | 协议版本双轨且落后多代 | 第 7 节行号 | 统一常量 + 协商校验 |
| V2-08 | **P2** | `1ff190d0` 顺带删除 `KbFlowServiceImpl` CAS 唤醒失败短路（非 MCP 范围） | `git show 1ff190d0 -- KbFlowServiceImpl.java` | 由 DAG 验收方复核 |
| V2-09 | **P2** | client Beans 仍无业务消费者（孤岛仅从"无 Bean"变为"有 Bean"） | `McpClientManager` / `SemanticToolRegistry` 消费者检索 | 接到真实工具调用链或标注未启用 |

---

## 10 建议与验收门槛

1. **立即回退 V2-01**（唯一 P0），并修正 `KbFlowServiceToolNodeTest`：正常路径注入 mock adapter，异常路径断言 `adapter==null → FAILED`。
2. **V2-03 最小修复**：控制面 `@Import(MemoryConfiguration.class)` 复用已合规的 1536 维 Qwen EmbeddingModel，禁用空索引全量返回，`indexTool` 维度 fail-fast。
3. **V2-02 口径或实现二选一**：短期在 `00-VERIFICATION-MASTER.md`/README 明确"MCP Server 未启用（不在控制面 classpath、无 transport）"；中期若要实现，先定 transport 与鉴权，再谈 Provider。
4. **V2-04/V2-05**：固定 `npx` 包版本、加超时与开关、沙箱 HOME 唯一化并 `createDirectories`。
5. **自证报告纪律**：每次提交后更新 `06-deep-fix-resolution.md` 的"现状"列，禁止使用"100%""彻底根除"等无证据表述；对同一能力的两层测试（Provider isError vs 节点 SUCCESS）必须先统一契约再改实现。

验收门槛（建议写入下一轮任务）：
- 静态：4 Provider 无委托 100% `isError=true`；`ToolNodeBO` 在 adapter==null 与 `{"error":...}` 两种输入下均返回 FAILED；
- 动态（Lead 执行）：`Phase46McpContractTest` 10/10 + `ApplicationContext` 加载无 Bean 冲突 + 一条 "adapter==null → FAILED" 单测；
- 语义：真实 Qwen 向量下 `recall@5 ≥ 0.8`（100 条 qrel），空索引不返回全量；
- 协议：stdio 与 HTTP 各接 1 个真实 server 完成 `initialize/tools/list(分页)/tools/call/取消`。

---

## 11 未验证事项

1. **未运行任何测试/构建/服务**（任务纪律），`Phase46` 10/10、`KbFlowServiceToolNodeTest` 现状均**未执行验证**，仅静态核对断言与计数；
2. **`ApplicationContext` 是否可加载未验证**：自动装配类"扫描 + SPI 双路径"当前判断为"因同类同方法而被去重、无覆盖异常"（推断），需一次上下文加载确认；
3. **`MemoryConfiguration` 是否被其他 `@Import`/spring.factories 激活未穷尽**：已 grep 全仓 `MemoryConfiguration` 引用仅自身；但 `spring.factories`/三方 starter 动态注册未穷尽（未知）；
4. **企业网络下 `npx` 在沙箱 HOME/代理白名单下的行为未验证**（推断）；
5. **`1ff190d0` 删除 `KbFlowServiceImpl` CAS 短路的影响未评估**（非 MCP 范围，仅提示）。

---

### 附录 证据索引

- 提交：`git show --stat 54fea59b`；`git show --stat 1ff190d0`；`git show 54fea59b -- backend/qknow-module-kb/.../ToolNodeBO.java`；`git show 1ff190d0 -- backend/qknow-module-kb/.../ToolNodeBO.java`、`-- backend/qknow-mcp/qknow-mcp-server/pom.xml`、`-- backend/tests/pom.xml`、`-- .../KbFlowServiceImpl.java`；`git log --oneline -- docs/architecture-review-20261019/verify/06-deep-fix-resolution.md`。
- Provider：`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/provider/{CodeSandboxMcpProvider.java:78, DataAgentMcpProvider.java:113, KnowledgeBaseMcpProvider.java:97, KnowledgeGraphMcpProvider.java:80}`；`backend/qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core/model/CallToolResult.java:12-18`。
- ToolNode：`backend/qknow-module-kb/qknow-module-kb-biz/src/main/java/tech/qiantong/qknow/module/kb/service/flow/bo/ToolNodeBO.java:127-134,146-156,164`；`.../factory/NodeFactory.java:39-40,67`；`backend/qknow-module-kb/qknow-module-kb-biz/src/test/java/tech/qiantong/qknow/kb/biz/flow/KbFlowServiceToolNodeTest.java:31,39-40`。
- 测试：`backend/tests/src/test/java/tech/qiantong/qknow/mcp/Phase46McpContractTest.java:109-110,133,155,183,203`（`grep -c "@Test"` = 10）。
- 自动装配：`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/config/McpClientAutoConfiguration.java:13-27`；`.../routing/SemanticToolRegistry.java:14`；`.../routing/ToolRagFilter.java:16-25`；`backend/qknow-mcp/qknow-mcp-server/src/main/java/tech/qiantong/qknow/mcp/server/config/McpServerAutoConfiguration.java:16-29`；两个 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；`backend/qknow-server/pom.xml`（无 qknow-mcp-*）、`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`、`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/config/HermesConfiguration.java:29-33`；`backend/qknow-mcp/qknow-mcp-client/pom.xml:45`（test scope）、`backend/tests/pom.xml:90`、`backend/qknow-mcp/qknow-mcp-server/pom.xml:26-29`。
- 语义向量：`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/tool/mcp/McpToolAdapter.java:63,171-178`；`.../hermes/memory/MemoryConfiguration.java:24-36`；`.../hermes/tool/mcp/governance/McpToolSemanticRetriever.java:73,117-118`；`.../hermes/tool/discovery/ToolDiscoveryService.java:77`；`backend/qknow-hermes/qknow-hermes-starter/src/main/java/tech/qiantong/qknow/hermes/HermesApplication.java:17-21`。
- stdio/GitHub：`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/transport/StdioEnterpriseTransport.java:82-101`；`backend/qknow-hermes/.../McpToolAdapter.java:89-100,206-210`。
- 协议版本：`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/McpClientManager.java:94,119`；`backend/qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core/model/InitializeResult.java:15`；`backend/qknow-hermes/.../StdioMcpClient.java:30`；`backend/qknow-hermes/.../HttpMcpClient.java:197`；规范 <https://modelcontextprotocol.io/specification/>（抓取 2026-10-19，最新 2026-07-28）。
- 自证报告：`docs/architecture-review-20261019/verify/06-deep-fix-resolution.md:21,29,70,71,132`。
