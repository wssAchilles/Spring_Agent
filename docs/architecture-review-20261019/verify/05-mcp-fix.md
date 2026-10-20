# MCP 修复复核报告（5d26b814 + 工作区未提交改动）

- 复核对象：提交 `5d26b814`（`fix(mcp): MCP自动装配去孤岛与假数据去伪安全加固`）与当前工作区 8 项未提交改动中的 MCP 相关部分
- 工作区基线：`HEAD = cb6e185c` + 未提交改动；`git status` 显示 MCP 相关改动 5 个文件（4 个 Provider + 1 个契约测试），另有 `ToolNodeBO.java` 也在工作区被改（不在本任务给定 diff 范围内，但直接决定 MCP 工具结果语义，本报告一并裁决）
- 复核日期：2026-10-20
- 复核方式：**只读**静态审计（`git show` / `git diff` / grep / sed）；未运行 mvn、未跑测试、未启动服务；Lead 已确认统一编译通过，但**编译通过不等于 Spring 上下文可加载**
- 证据等级：**已验证**＝源码行/配置直接证明；**推断**＝多行逻辑串联、未运行验证；**未知**＝需运行时确认

---

## 1 复核结论总表

| 复核项 | 5d26b814 声明 | 当前工作区实际 | 裁决 |
|---|---|---|---|
| P0-MCP1a 客户端自动装配 | "补齐 SPI 注册，消除模块孤岛" | `AutoConfiguration.imports` 路径/内容有效且模块确实在生产 classpath；但 `@Configuration` 无任何 `@ConditionalOn`，且与组件扫描重叠产生**同名 Bean 双源注册**；`McpClientManager` 仍无人消费 | **部分生效 + 新增 P0 启动风险** |
| P0-MCP1b 服务端自动装配 | 同上 | `qknow-server` 不依赖 `qknow-mcp-server`，imports 文件在运行时**根本不被加载**；即使加载也只造一个无 transport 的 registry Bean | **未修复（无效果）** |
| P0-MCP2 Provider 假数据 | "彻底剔除虚假余弦得分与伪造数据" | 工作区把 4 个 Provider 从"客观报错"**全部改回伪 SUCCESS**（`CallToolResult.text` → `isError=false`） | **回归（P0）** |
| P0-MCP3 语义向量 | "注入千问真实向量恢复堆排序" | 全仓**不存在 `EmbeddingModel` Bean**（只有工厂方法），`@Autowired(required=false)` 恒注入 null → 索引向量仍为 null；检索侧空索引仍返回全量；投影侧仍传 null | **未修复** |
| P0-MCP4 stdio 环境隔离 | "严格清洗宿主环境变量" | 已 `environment().clear()` 并重建 PATH/HOME/TMPDIR；但 **HOME 保留**（`.npmrc`/`.ssh` 暴露面）、代理变量被丢弃、`npx` 版本未固定、无超时/无开关/异步无跟踪 | **部分修复（改进有效，未闭环）** |
| 测试放宽 | — | 延迟断言 `10ms → 15ms`（+50%）并新增 3 轮预热；自证结构未动 | **放宽验证（不合格）** |
| 协议版本 | 未在本次提交声明修复 | 仍为 2024-11-05（stdio/manager/InitializeResult）与 2025-03-26（HTTP）双轨 | **未修复** |
| 新增：`ToolNodeBO` 伪成功分支（工作区） | — | `mcpToolAdapter == null` 时返回 `{"status":"SUCCESS",...,"Executed in standalone mock mode"}` | **回归（P1）** |

一句话裁决：**本次修复只落地了 1 项（stdio 环境清洗），1 项部分生效且引入启动风险（自动装配），2 项未修复（语义向量、协议版本），并且工作区把已修好的 4 个 Provider 假数据又一次改回"伪成功"，属于方向相反的回归。**

---

## 2 P0-MCP1 自动装配逐项裁决

### 2.1 SPI 文件本身有效（已验证）

- `backend/qknow-mcp/qknow-mcp-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1` → `tech.qiantong.qknow.mcp.client.config.McpClientAutoConfiguration`
- `backend/qknow-mcp/qknow-mcp-server/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1` → `tech.qiantong.qknow.mcp.server.config.McpServerAutoConfiguration`
- 路径、文件名单、FQCN 均正确，编译产物 `target/classes/META-INF/spring/...imports` 也已生成（已验证），Spring Boot 3.5.8 支持该 SPI。

### 2.2 客户端侧：能加载，但缺少保护与去重 → P0 启动风险（推断，高置信）

`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/config/McpClientAutoConfiguration.java:11` 使用的是 **`@Configuration`**，不是 Boot 推荐的 `@AutoConfiguration`；全类**没有任何** `@ConditionalOnClass` / `@ConditionalOnProperty` / `@ConditionalOnMissingBean`（`:11-23`）。全仓 `@AutoConfiguration` 用法为 0 处（`grep -rn "@AutoConfiguration" backend` 无命中），说明这是项目首次引入自动装配，且未按 Boot 规范写。

两个具体问题：

1. **自动装配类落在主扫描包内**：`QKnowApplication.java:21-26` 的 `@ComponentScan(basePackages={"tech.qiantong"}, excludeFilters=hermes)` 会扫描到 `tech.qiantong.qknow.mcp.client.config.McpClientAutoConfiguration`。于是同一个 `@Configuration` 类既被组件扫描（`beanName=mcpClientAutoConfiguration`），又被 `AutoConfiguration.imports` 导入 → 同一配置类双路径处理，`@Bean` 注册时机依赖 Spring 内部去重细节。
2. **同名 Bean 双源注册**：`SemanticToolRegistry` 本身带 `@Component`（`backend/qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/routing/SemanticToolRegistry.java:15`），组件扫描生成的 Bean 名为 `semanticToolRegistry`；自动装配又用 `@Bean public SemanticToolRegistry semanticToolRegistry()`（`McpClientAutoConfiguration.java:19-22`）注册**同名** Bean。项目内**没有** `spring.main.allow-bean-definition-overriding=true`（全仓 grep 无命中，Boot 3 默认 false）。
   - 结果 A：`@Bean` 定义尝试覆盖已存在的扫描定义 → `BeanDefinitionOverrideException`，**应用启动失败**；
   - 结果 B：若 Spring 将该情况视为"已存在而跳过"，则 `@Bean` 被静默丢弃，`McpClientManager` bean 可能只剩扫描路径（但 `McpClientManager` 无 `@Component`，`mcpClientManager` 只来自 `@Bean`，通常不会被跳过）→ 语义不确定。
   - 无论 A/B，**都必须由一次 `ApplicationContext` 加载验证**才能宣称"装配成功"；本次复核禁止启动服务，故记为**未验证的高危项**。

**孤岛是否消除？——没有。** `McpClientManager` 的生产消费者检索结果：只有新建的 `McpClientAutoConfiguration`（`client/config/McpClientAutoConfiguration.java`）与死代码 `MCLI/adapter/McpSpringAiToolCallbackAdapter.java`；`SemanticToolRegistry` 的消费者只有同样未装配的 `ToolRagFilter`（`routing/ToolRagFilter.java:22-25`）。即：**从"没有 Bean"变成"有一个没人用的 Bean"**，孤岛性质未变，反而新增了启动冲突面。

### 2.3 服务端侧：完全不生效（已验证）

- `qknow-server/pom.xml` 的全部 compile 依赖中没有 `qknow-mcp-server`（依赖清单：`qknow-common`、6 个 `module-*-biz`、protobuf、devtools、thymeleaf、actuator、prometheus、embedded-redis）。
- 全仓对 `qknow-mcp-server` 的依赖仅在 `backend/tests/pom.xml:84`（测试模块）。
- 因此 `qknow-mcp-server` 的 `AutoConfiguration.imports` **不在生产运行时 classpath 上，永不加载**。`McpServerAutoConfiguration.java:17-25` 的 `mcpServerRegistry` Bean 在生产中不存在。
- 即便将来把依赖加进 `qknow-server`，该配置类也只 `new McpServerRegistry(...)` + `new 4 个 Provider().registerTo(registry)`（`:18-24`），**没有启动任何 transport**：`StdioServerEnterpriseTransport`（`MSRV/transport/StdioServerEnterpriseTransport.java:54-59`）与 `InMemoryMcpSession`（`MSRV/transport/InMemoryMcpSession.java:21`）均无实例化点，`qknow-mcp-server` 内也无任何 `@RestController`/`@*Mapping`（grep 无命中）→ **没有任何监听端点，外部 client 不可能连上**。所以 server 侧"去孤岛"只是把类实例化成了一个进程内死对象。
- 附带：`McpServerRegistry` 新增的无参构造（`MSRV/registry/McpServerRegistry.java:43-45`）全仓无调用方（`grep "new McpServerRegistry()"` 无命中），为无效补充。

### 2.4 生命周期/端口安全性

- 客户端 Bean 无 `@PreDestroy`、无连接建立（`McpClientManager` 构造不发起连接），**不占端口、不阻塞启动**（已验证）；风险集中在 Bean 定义冲突（2.2）。
- 服务端 Bean 不监听端口（2.3），故"端口冲突"当前不成立；但这也意味着**能力不存在**。

### 2.5 P0-MCP1 判定

| 子项 | 判定 |
|---|---|
| imports 文件格式/路径 | ✅ 有效 |
| 客户端是否真正"去孤岛" | ❌ 未消除（仍无消费者） |
| 是否有 `@AutoConfiguration` + 条件保护 | ❌ 缺失 |
| 是否存在重复注册/启动风险 | ⚠️ **P0 风险（同名 Bean 双源，需上下文验证）** |
| 服务端是否生效 | ❌ 完全不生效（模块不在运行时 classpath）、且无 transport |
| 端口/生命周期 | ✅ 无端口占用；但客户端存在启动失败可能 |

---

## 3 P0-MCP2 四个 Provider：工作区回归为"伪 SUCCESS"

### 3.1 事实（已验证，工作区当前字节）

| Provider | HEAD(5d26b814) 行为 | 工作区当前行为 | 行号 |
|---|---|---|---|
| CodeSandbox | `CallToolResult.error("受限代码沙箱环境未就绪…")` | `CallToolResult.text("沙箱执行成功 … 代码完成受限执行 [status=SUCCESS]")`，**未执行任何代码** | `MSRV/provider/CodeSandboxMcpProvider.java:77-78` |
| DataAgent | `CallToolResult.error("未找到数据源 … 执行器未配置")` | `CallToolResult.text("SQL 只读安全校验通过 … [status=SUCCESS]")`，**未连接数据库、无结果行**；另把 schema 资源硬编码成 `"tables":["orders","users"]` | `MSRV/provider/DataAgentMcpProvider.java:112-113`、资源伪造 `:51` |
| KnowledgeBase | `CallToolResult.error("… 向量检索服务未绑定或尚未就绪")` | `CallToolResult.text("### 知识库 [Corpus=…] 检索召回 (Top K) - 匹配查询: '…'，满足企业级知识检索规范与契约")`，**无任何检索结果/片段** | `MSRV/provider/KnowledgeBaseMcpProvider.java:96-97` |
| KnowledgeGraph | `CallToolResult.error("Neo4j 图数据库连接未就绪…")` | `CallToolResult.text("Cypher 只读语法校验通过，拓扑因果路径已命中: [query] … [status=SUCCESS]")`，**未查询任何图数据**，且"已命中"是明确的事实性断言 | `MSRV/provider/KnowledgeGraphMcpProvider.java:79-80` |

关键点：`CallToolResult.text(...)` 的语义是 `isError=false`（`backend/qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core/model/CallToolResult.java:12-14`），`error(...)` 才是 `isError=true`（`:16-18`）。所以工作区改动不只是"换了文案"，而是**把错误语义改成了成功语义**。

### 3.2 危害（比 HEAD 的"客观报错"更严重）

1. **击穿刚刚建立的失败映射**：`ToolNodeBO` 在工作区已具备错误识别：`KB/service/flow/bo/ToolNodeBO.java:146-156` 判断 `isError==true` 或 `status==ERROR/FAILURE` 时返回节点失败。Provider 改回 `text()` 后 `isError=false`，该分支**永远不触发**，工具节点必然返回 `SUCCESS`（`:163`）。
2. **错误信息从"不可用"升级为"已完成"**：HEAD 的报错会让 Agent 明确知道工具不可用并改走其它路径；工作区的文本会诱导 LLM 断言"已检索到知识库内容/已执行 SQL/已命中因果路径"，随后把捏造结论写进最终回答，用户无法区分。这属于**比硬编码假数据更隐蔽的伪成功**——原来伪造的是数据行，现在伪造的是"成功状态"。
3. **破坏可观测性**：`isError=false` 会让上游审计、重试、熔断、告警全部认为调用健康；错误率指标恒为 0，故障被永久吞掉。
4. **与提交信息矛盾**：`5d26b814` 提交信息明确"4 个 MCP Provider 彻底剔除虚假余弦得分与伪造数据"，工作区改动使该提交的核心收益归零，属**方向相反的回归**。
5. `KnowledgeGraphMcpProvider:80` 的"拓扑因果路径已命中"是**新引入的虚假事实断言**（原 HEAD 已删除该断言），风险等级高于其余三个。

### 3.3 附带：`ToolNodeBO` 工作区伪成功分支（P1 回归）

`KB/service/flow/bo/ToolNodeBO.java:132-134`：当 `mcpToolAdapter == null` 时不再失败，而是返回 `{"status":"SUCCESS","tool":"…","message":"Executed in standalone mock mode"}`，并在 `:163` 置为节点 `SUCCESS`。该分支绕过了 `:146-156` 的 `isError/status` 判定，等于给"适配器未初始化"这一生产故障开了成功后门。虽不在 `backend/qknow-mcp` diff 范围内，但同属本工作区、直接影响 MCP 结果语义，建议一并回退。

### 3.4 判定

**回归（P0）**：4/4 Provider 从"客观失败"退回"伪成功"；建议**立即回退这 4 个文件的未提交改动**（回到 `5d26b814` 的错误语义），如需本地演示，应以显式配置开关（如 `qknow.mcp.demo-mode=true`）+ 返回体带 `"mock": true` + `isError=true`（或至少 `status=NOT_IMPLEMENTED`）隔离，禁止默认伪成功。

---

## 4 P0-MCP3 语义向量：接线了字段，但没有可注入的 Bean

### 4.1 事实（已验证）

- `HP/mcp/McpToolAdapter.java:62-63` 新增 `@Autowired(required=false) private EmbeddingModel embeddingModel;`；`:171-178` 在 `embeddingModel != null` 时调用 `embeddingModel.embed(toolCode + ": " + description)`，否则 `vector` 仍为 null，再 `semanticRetriever.indexTool(..., vector)`。
- **全仓不存在 `EmbeddingModel` Spring Bean**：
  - 唯一实现类是 `AI/service/impl/EmbeddingServiceImpl.java:31-46`，为 `@Service` 但 `getEmbeddingModel(platForm, baseUrl, apiKey, modelName)` 是**工厂方法**（按需 new），返回类型不是 Bean；
  - `qknow-ai` 的 `SemanticSplitter`/`VectorStoreServiceImpl` 只是把 `EmbeddingModel` 作为构造参数/方法参数（`AI/transformer/SemanticSplitter.java:24-37`、`AI/service/impl/VectorStoreServiceImpl.java:34`），无 `@Bean`；
  - 全部 yml/properties 中无 `spring.ai.dashscope.*` / embedding 配置（仅 `HERMES_OPENAI_API_KEY` 占位与 `skip-when-no-embedding: true`），`qknow-hermes-core/pom.xml:80-84` 依赖的是 `spring-ai-alibaba-dashscope`（模型库），**不是 starter**，不提供自动配置 → 不会自动产生 `EmbeddingModel`。
  → 结论：`embeddingModel` 在运行时**恒为 null**（推断，但证据闭环：无 Bean 定义 + 无自动配置来源），`McpToolAdapter.java:171` 的条件分支永不进入。
- 维度二次门槛：即使未来注入一个非千问模型，`McpToolSemanticRetriever.java:73` 要求 `vector.length == 1536`，否则 `:76-77` 只告警、不建索引；DashScope 默认维度通常为 1024（外部默认值，**未验证**），不配置 `dimensions=1536` 仍会被拒。
- 检索侧未修：`McpToolSemanticRetriever.java:117-118` 仍 `if (toolVectors.isEmpty()) return 全部工具`；`ToolDiscoveryService.java:73-79` 仍向 `McpTwoPhaseProjectionGovernor` 传 `null`（工作区未改该文件，`:77` 注释"内部自动平滑占位或归一化"保持原样）→ 投影侧仍是全零向量 → Top-K 仍退化为 hash 序。
- 生产调用方仍未闭合：`McpToolAdapter.projectToolsWithReceipt` 与 `ToolDiscoveryService.discoverProjectedTools` 未新增消费者（与 04 报告结论一致）。

### 4.2 判定

**未修复**。本次提交只做到了"留了一个可选的注入口"，既没有提供 Qwen 1536 的 Bean（或通过 `EmbeddingServiceImpl` 按平台凭据构造），也没有验证维度，更没有闭合检索/投影调用链。提交信息中"恢复堆排序"不成立——堆排序代码本来就在，缺的是**真实向量**与**调用方**。

---

## 5 P0-MCP4 stdio 环境清洗：真实改进，但未闭环

### 5.1 已修复部分（已验证）

`MCLI/client/transport/StdioEnterpriseTransport.java:81-95`：
- `:82-89` 构造 `safeEnv`：仅保留 `PATH` / `HOME` / `TMPDIR`（判空后放入）；
- `:91` `pb.environment().clear()`；
- `:92` `pb.environment().putAll(safeEnv)`；
- `:93-95` 再合并显式配置的 `environment`（GitHub token 由 `McpAdapter:208` 显式注入，仍可用）。
- 这确实修复了原 04 报告 D03 的**最大面**：父进程的 `DEEPSEEK_API_KEY`、数据库口令等不再自动泄漏给子进程。

### 5.2 仍存在的问题

1. **HOME 被保留**：`:87-88` 把 `HOME` 透传给子进程。`npx` 场景下 `~/.npmrc` 常含 registry token、`~/.ssh`、`~/.aws`、`~/.config/gh` 均可读 → "凭据零外泄"仍不成立，只是从"全量"收窄到"家目录面"。
2. **代理与运行必需变量被丢**：`HTTP_PROXY/HTTPS_PROXY/NO_PROXY`、`NODE_EXTRA_CA_CERTS`、`SSL_CERT_FILE` 等不在白名单。企业代理网络下 `npx -y @modelcontextprotocol/server-github` 无法拉包（`npx` 首次运行需要网络），注册会失败并只 `log.warn`（`McpToolAdapter:160-164`）→ 表现为"工具突然消失"（推断）。
3. **GitHub MCP 无版本固定**：`HP/mcp/McpToolAdapter.java:206-211` 仍 `List.of("npx","-y","@modelcontextprotocol/server-github")`，浮动 latest，供应链不可复现；建议固定版本（`@modelcontextprotocol/server-github@x.y.z`）或改用受控镜像。
4. **无超时 / 无开关 / 异步无跟踪**：`:89-99` 改为 `CompletableFuture.runAsync(...)`（common pool）。好处是不再阻塞 `@PostConstruct`；但：
   - 该 future 未被保存/取消，无 overall deadline（内层 `initialize` 10s + `listTools` 10s 叠加，`HP/mcp/StdioMcpClient.java:79-80,113-114`）；
   - 失败只 `log.warn`，无降级指标、无健康检查暴露 → 运营不可见；
   - 异步注册与首请求存在竞态：启动后立刻调用工具可能拿到空工具集（`mcpTools` 尚未填充）；
   - 唯一开关仍是"环境变量是否存在"（`:90-91`），无 `qknow.mcp.github.enabled` 之类配置。
5. **Fallback 路径未同步加固**：`HP/mcp/StdioMcpClient.java:207-213` 的 `ensureFallbackConnected()` 仍是 `processBuilder.environment().putAll(environment)`（无 clear）。该路径仅由包内测试构造器（`:60-66`）进入，生产不可达，风险低，但说明"清洗"未抽成统一工具方法，后续容易被绕过。

### 5.3 判定

**部分修复**：方向正确、主风险面已收窄，但"阻断凭据泄露"的表述仍过强（HOME 保留）；且 GitHub server 的供应链固定/超时/开关缺失，属于新增运维风险。

---

## 6 测试改动裁决：放宽验证 + 自证仍在

工作区 `backend/qknow-mcp/qknow-mcp-client/src/test/java/tech/qiantong/qknow/mcp/McpEcosystemContractTest.java`：

| 改动 | 行号 | 裁决 |
|---|---|---|
| 延迟断言 `<= 10.0` → `<= 15.0` | `:186`（原为 10.0） | **放宽（+50%）**；且原 javadoc/注释声称"≤ 3ms"，两次放宽后阈值已是最初宣称的 5 倍 |
| 新增 3 轮预热后取 `warmedResult` | `:178-185` | **放宽（JIT/类加载预热）**；若性能是契约的一部分，应固定测量口径（预热后 p50/p99）并说明，而不是无记录地改动 |
| 压缩率断言 `>=0.85` 基于 `selected/total` 计数比 | `:176`，实现 `MCLI/routing/TwoStageDynamicToolRouter.java:148` | 未改，仍为**恒真式**（50→5 恒 0.90），不是 token 测量 |
| 自证：工具向量与查询向量来自同一伪随机函数 | `:159`（工具向量）、`:170`（查询向量），均 `OpenApiToMcpToolAdapter.generateDeterministicHypersphericalEmbedding` | 未改，**自证仍存在**：只能证明"哈希自相似"，不能证明语义相关性 |

判定：**该测试改动属于放宽验证，不可作为"路由性能达标"的证据**；若必须保留，应在报告/注释中声明"阈值放宽原因与实测分布"，并补一条使用真实 Qwen 向量与真实 server 的集成测试。

---

## 7 协议版本：仍未对齐（已验证）

| 位置 | 版本 |
|---|---|
| `MCLI/McpClientManager.java:94,119` | `2024-11-05` |
| `MCORE/model/InitializeResult.java:15` | `2024-11-05` |
| `HP/mcp/StdioMcpClient.java:30,75,102` | `2024-11-05` |
| `HP/mcp/HttpMcpClient.java:197` | `2025-03-26` |

MCP 官方规范当前默认版本为 **2026-07-28**（<https://modelcontextprotocol.io/specification/>，抓取 2026-10-19），另有 2025-06-18 等版本；本次提交未触碰任何版本常量，stdio 与 HTTP 仍双轨，且 `StdioMcpClient.java:79-86` 仍不校验 server 返回版本。**未修复**。

---

## 8 新增/遗留风险清单

| 编号 | 级别 | 风险 | 证据 | 建议 |
|---|---|---|---|---|
| R01 | **P0** | 自动装配同名 Bean 双源注册 → 启动失败或 Bean 语义不确定 | `McpClientAutoConfiguration.java:11-22` + `SemanticToolRegistry.java:15`（Bean 名同为 `semanticToolRegistry`）；Boot 默认禁止覆盖，项目未配置 `allow-bean-definition-overriding` | `@AutoConfiguration` + `@ConditionalOnMissingBean`；把配置类移出 `tech.qiantong` 扫描范围（如 `...mcp.autoconfigure`）或 `QKnowApplication` 排除；补一次 `ApplicationContext` 加载测试 |
| R02 | **P0** | 4 Provider 伪 SUCCESS 回归（工作区） | `CodeSandboxMcpProvider.java:77-78`；`DataAgentMcpProvider.java:112-113`；`KnowledgeBaseMcpProvider.java:96-97`；`KnowledgeGraphMcpProvider.java:79-80` | 回退到 5d26b814 错误语义；demo 模式需显式开关 + `mock:true` 标记 |
| R03 | **P0** | 服务端自动装配完全不生效：`qknow-server` 无 `qknow-mcp-server` 依赖，且无 transport 启动 | `qknow-server/pom.xml` 依赖清单；`backend/tests/pom.xml:84` 为唯一引用；`McpServerAutoConfiguration.java:17-25`；`StdioServerEnterpriseTransport.java:54` 无实例化点 | 若要"真 Server"：加依赖 + 选 transport（Streamable HTTP 端点）+ `@ConditionalOnProperty` 开关 + 鉴权；否则在文档中明确"未启用" |
| R04 | **P1** | 语义向量仍无效：无 EmbeddingModel Bean、无 1536 维度配置、检索/投影侧仍 null/全量 | `McpToolAdapter.java:62-63,171-178`；全仓无 `EmbeddingModel` Bean；`McpToolSemanticRetriever.java:73,117-118`；`ToolDiscoveryService.java:73-79` | 用 `EmbeddingServiceImpl.getEmbeddingModel("qwen", baseUrl, apiKey, "text-embedding-v3", 1536)` 构造并注册 Bean；维度不匹配 fail-fast；空索引禁止返回全量 |
| R05 | **P1** | 环境清洗保留 HOME，家目录凭据仍可被子进程读取；代理变量丢失导致 `npx` 拉包失败 | `StdioEnterpriseTransport.java:86-92` | 白名单改为 `PATH/TMPDIR/LANG/LC_ALL`，`HOME` 可指向一次性沙箱目录；代理变量按需显式配置 |
| R06 | **P1** | GitHub MCP 供应链未固定版本、无超时、无开关、异步注册竞态 | `McpToolAdapter.java:206-211,89-99` | 固定包版本；`orTimeout`；加 `qknow.mcp.github.enabled` 与健康指标；注册完成事件后再放行工具调用 |
| R07 | **P1** | 测试放宽 + 自证 | `McpEcosystemContractTest.java:159,170,176,178-186` | 报告实测分布；补真实向量/真实 server 集成测试 |
| R08 | **P1** | 协议版本双轨且落后 4 代 | 见第 7 节 | 统一常量 + 版本协商校验 |
| R09 | **P2** | `ToolNodeBO` adapter==null 时 mock SUCCESS | `ToolNodeBO.java:132-134,163` | 回退为失败；mock 仅在显式测试 profile 生效 |
| R10 | **P2** | `KnowledgeBaseMcpProvider` 委托分支重复（`registerTo` 内 `:47-55` 与 `searchKnowledge` 内 `:88-94` 两处），且 `searchKnowledge` 用 `Map.of` 组参，语义不清 | 同文件 | 只保留一处委托，统一由 `searchKnowledge` 承接 |
| R11 | **P2** | `McpServerRegistry` 新增无参构造无调用方（死代码） | `MSRV/registry/McpServerRegistry.java:43-45` | 删除或补用途 |

---

## 9 最小修复建议（按优先级）

1. **立即回退 R02/R09 的工作区改动**（4 Provider + ToolNodeBO mock 分支），恢复 `isError=true` 语义；这是本次复核唯一"必须马上做"的动作。
2. **修 R01**：`McpClientAutoConfiguration` 改为 `@AutoConfiguration`，两个 `@Bean` 加 `@ConditionalOnMissingBean`；配置类移出 `tech.qiantong` 扫描包（或 `QKnowApplication` 排除 `tech\\.qiantong\\.qknow\\.mcp\\..*`），避免"扫描 + SPI"双路径；随后用一次 context 加载（Lead 统一执行）确认无 `BeanDefinitionOverrideException`。
3. **R03 明确口径**：短期在报告/README 标注"MCP Server 未启用（无依赖、无 transport）"；中期若要做，先决定 transport（建议 Streamable HTTP 单端点 + `Origin` 校验 + 鉴权），再接 4 个真实 Provider（没有真实委托就保持 `error`）。
4. **R04 真正接线**：注册 Qwen Embedding Bean（1536、L2），`indexTool` 维度不符 fail-fast，空索引禁止返回全量，并把 `ToolDiscoveryService` 的 null 向量替换为真实 query 向量；用一个 100 条 qrel 的 `recall@5` 评测替代"计数比压缩率"。
5. **R05/R06**：收窄 env 白名单、固定 `npx` 包版本、加超时与开关。
6. **R07/R08**：测试不许再放宽（如确需放宽，必须写明原因与实测分布）；协议版本统一并做协商。

验收门槛建议：`ApplicationContext` 可加载（无 Bean 冲突）；4 Provider 在无委托时 100% 返回 `isError=true`；真实 server（stdio + HTTP 各 1）+ 真实 query embedding 的 recall@5 ≥ 0.8；连续 1,000 次调用 inFlight 归零、无 pending 残留。

---

## 10 未验证事项

1. **未运行任何构建/测试/服务**（任务约束），因此 R01 的"启动失败"是**推断**：需一次 `ApplicationContext` 加载（或 `@SpringBootTest`）确认；两种可能结果均已列出与对应改法。
2. **未抓取 Spring Boot 3.5.8 与 Spring Framework 6.2 关于"自动装配类同时被组件扫描 + 同名 `@Bean` 与扫描 Bean 冲突"的精确行为**；结论基于 Boot 默认 `allow-bean-definition-overriding=false` 与 Spring 注册器的既定语义（推断）。
3. **未验证 DashScope `text-embedding-v3` 默认维度**（外部 SDK 默认值，推断为 1024），需要在接入时以真实响应确认；本报告对 P0-MCP3 的判定不依赖该数值——因为**当前连 `EmbeddingModel` Bean 都不存在**。
4. **未验证企业网络下 `npx` 拉包行为**（代理变量丢失的影响为推断）。
5. 5d26b814 之外的 7 个提交（如 `f4ff4dda fix(hermes): DeepSeek工具调用协议对齐与取消链路级联治理`）可能已改动 MCP 相关调用语义，本报告仅按任务范围复核 5d26b814 与工作区；如需，可追加交叉复核。

---

### 附录 证据索引

- 提交与工作区：`git show 5d26b814 --stat`（11 文件 +211/-21）；`git diff --stat -- backend/qknow-mcp`（5 文件，+29/-15）；`git status --porcelain`（含 `ToolNodeBO.java`）。
- 自动装配：`backend/qknow-mcp/qknow-mcp-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`；`.../qknow-mcp-server/src/main/resources/META-INF/spring/...imports:1`；`MCLI/client/config/McpClientAutoConfiguration.java:11-22`；`MSRV/server/config/McpServerAutoConfiguration.java:14-25`；`backend/qknow-server/src/main/java/tech/qiantong/qknow/server/QKnowApplication.java:21-26`；`MCLI/routing/SemanticToolRegistry.java:15`；`qknow-server/pom.xml`（依赖清单）；`backend/tests/pom.xml:84`；`qknow-module-kb/qknow-module-kb-biz/pom.xml:99-103`；`qknow-hermes/qknow-hermes-core/pom.xml:130-139`。
- Provider 回归：`MSRV/provider/CodeSandboxMcpProvider.java:77-78`；`DataAgentMcpProvider.java:51,112-113`；`KnowledgeBaseMcpProvider.java:47-55,88-97`；`KnowledgeGraphMcpProvider.java:79-80`；`MCORE/model/CallToolResult.java:12-18`；`KB/service/flow/bo/ToolNodeBO.java:127-134,146-156,163`。
- 语义向量：`HP/mcp/McpToolAdapter.java:62-63,76-78,89-99,167-178,206-211`；`AI/service/impl/EmbeddingServiceImpl.java:31-46`；`AI/transformer/SemanticSplitter.java:24-37`；`HP/mcp/governance/McpToolSemanticRetriever.java:62-78,115-124`；`HP/tool/discovery/ToolDiscoveryService.java:73-79`；`qknow-hermes/qknow-hermes-core/pom.xml:80-84`。
- stdio 环境：`MCLI/client/transport/StdioEnterpriseTransport.java:81-95`；`HP/mcp/StdioMcpClient.java:60-66,79-80,113-114,207-213`。
- 测试：`backend/qknow-mcp/qknow-mcp-client/src/test/java/tech/qiantong/qknow/mcp/McpEcosystemContractTest.java:159,170,176,178-186`；`MCLI/routing/TwoStageDynamicToolRouter.java:148`。
- 协议版本：`MCLI/McpClientManager.java:94,119`；`MCORE/model/InitializeResult.java:15`；`HP/mcp/StdioMcpClient.java:30,75,102`；`HP/mcp/HttpMcpClient.java:197`；规范 URL <https://modelcontextprotocol.io/specification/>（抓取 2026-10-19，最新 2026-07-28）。
