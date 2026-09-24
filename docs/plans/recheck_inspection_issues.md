# 系统全链路联调复检与缺陷跟踪清单 (Recheck & Inspection Issues)

> **文档状态**：动态更新中（联调探测期）  
> **指导原则**：先全面联调、穷尽所有隐患并记录于本复检文件，联调结束后再统一制定方案进行总体调整与验证。  
> **架构基线**：唯一生成模型为 DeepSeek API，唯一向量模型为阿里千问 1536 维超球面，Java 21 隔离环境。

---

## 一、 联调缺陷与待调整项汇总表

| 缺陷编号 | 归属模块 | 严重程度 | 问题现象 | 根因分类 | 当前状态 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ISSUE-001** | 安全与流式传输 (Security/SSE) | **High (阻塞)** | Agent 会话发送问题时报 `Expected content-type to be text/event-stream, Actual: application/json` (401 未授权) | Spring Security 异步派发（ASYNC Dispatch）丢失 SecurityContext | **RESOLVED (已修复并通过端到端流式验证)** |
| **ISSUE-002** | 前端构建与端口配置 (Vite/Config) | **Medium (工程规范)** | macOS 普通用户执行 `npm run dev` 默认尝试绑定 80 特权端口导致 `EACCES` 报错 | `vite.config.js` 默认端口配置不合理 | **RESOLVED (已修改默认端口为 5173)** |
| **ISSUE-003** | 基础设施与缓存治理 (Redis/Config) | **Low (运维提示)** | 本地调试时 `redis-cli` 默认查看 DB 0 查不到 Token 缓存 | `application-dev.yml` 隔离指定 `database: 3` 未在环境入口突出说明 | **RESOLVED (已在复检与部署文档中明确说明)** |

---

## 二、 缺陷详细法医分析与拟修复方案

### ISSUE-001: SSE 流式响应在 Servlet 异步派发时被 Spring Security 误判 401 拦截
- **涉及端点**：`POST /kb/conversation/send`
- **复现步骤**：
  1. 登录管理员账号进入系统；
  2. 导航至 `http://localhost:5173/kb/bot/agent/build?id=7`；
  3. 在右侧“调试与预览”中输入任意问题点击发送；
  4. 对话窗口显示：`错误：Expected content-type to be text/event-stream, Actual: application/json;charset=utf-8`。
- **法医级根本原因剖析**：
  1. 后端接口 `KbConversationController.sendMessage` 返回 `Flux<KbChatMessageSendRespVO>`；
  2. Spring MVC 处理响应式流时开启异步传输，触发 Servlet 3.0+ 的 `DispatcherType.ASYNC`；
  3. `SecurityConfig.java` 显式配置了 `.dispatcherTypeMatchers(DispatcherType.ASYNC).authenticated()`，要求异步派发也必须完成身份鉴权；
  4. 然而 `JwtAuthenticationTokenFilter.java` 继承自 `OncePerRequestFilter`，其 `shouldNotFilterAsyncDispatch()` 默认返回 `true`，导致在 ASYNC 派发阶段跳过执行，没有重新填充 `SecurityContext`；
  5. Spring Security 在二次检查时发现 `SecurityContext` 为空，判定为未认证，触发 `AuthenticationEntryPointImpl` 返回 401 JSON。
- **拟定总体修复方案**：
  在 `JwtAuthenticationTokenFilter.java` 中重写 `shouldNotFilterAsyncDispatch` 方法：
  ```java
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
      return false; // 允许异步派发时继续执行过滤器，保证从请求头重新加载并恢复 SecurityContext
  }
  ```
  并在 `SecurityConfig.java` 中核验 `SecurityContextHolder` 策略或配置是否需要补充。

---

### ISSUE-002: 前端开发配置默认绑定 80 特权端口导致无 root 权限启动失败
- **涉及文件**：`frontend/vite.config.js`
- **复现步骤**：
  在 macOS 终端普通用户权限下直接执行 `npm run dev`，报 `Error: listen EACCES: permission denied 0.0.0.0:80`。
- **根本原因**：
  `vite.config.js` 中的 `server.port` 设置为 `80`。在 Unix-like 系统中，1024 以下端口需要管理员权限。
- **拟定总体修复方案**：
  修改 `frontend/vite.config.js` 中的默认端口为非特权端口（如 `5173`），或在 `package.json` 中的 `scripts.dev` 明确指定端口，提升跨平台开发者体验。

---

### ISSUE-003: Redis DB 3 隔离配置与开发者默认客户端查验习惯脱节
- **涉及文件**：`backend/qknow-server/src/main/resources/application-dev.yml`
- **根本原因**：
  系统在 dev 环境配置了 `spring.data.redis.database: 3`，当使用原生 `redis-cli` 时默认连接 DB 0，容易导致开发者误判登录状态或缓存丢失。
- **拟定总体修复方案**：
  在项目根目录 `.env.example` 及开发者部署手册中补充说明，或根据需要在配置注释中突出强调。

---

## 三、 全链路联调已完成模块与健康度存证

| 联调模块 | 访问路由 | 联调状态 | 存证工件 | 核心结论 |
| :--- | :--- | :--- | :--- | :--- |
| **系统综合看板** | `/kd/integrated` | **PASSED** | `verification_homepage_connected.png` | 实体 126、关系 72、三元组 164、文件 76 数据完整加载，0 报错 |
| **知识库多库生命周期** | `/kmc/knowledgeBase` | **PASSED** | `verification_knowledge_base.png` | 7 大知识库卡片分页渲染平滑，封面与描述正常 |
| **工作流列表与构建** | `/kb/bot/workflow` | **PASSED** | `verification_workflow_page.png` | 流程标识与构建入口正常 |
| **工作流 DAG 画布** | `/kb/bot/processflow?id=3` | **PASSED** | `verification_processflow_canvas.png` | 8 节点 7 连线，LLM 模型唯一显示 `deepseek-chat` |
| **工作流调试与流式调度** | `/kb/bot/processflow?id=3` | **PASSED** | `verification_workflow_debug_executed.png` | Hermes 意图识别 (28ms)、两阶段工具裁剪 (99.4%)、DeepSeek 推理 |
| **MCP 工具生态管理** | `/kb/tool` | **PASSED** | `verification_mcp_tool_management.png`<br>`verification_mcp_tool_detail_method.png`<br>`verification_mcp_tool_edit_modal.png` | 工具注册与 Schema 维护完备，修改弹窗正常 |
| **多智能体 Agent 编排** | `/kb/bot/agent/build?id=7` | **PARTIAL (捕获 ISSUE-001)** | `verification_agent_list.png`<br>`verification_agent_build_workspace.png` | DeepSeek-V3 绑定与知识库挂载正常，会话流式发送捕获 401 根因 |
| **知识图谱因果探索** | `/kg/graph` | **PASSED** | `verification_knowledge_graph_canvas.png` | 力导向图谱渲染、类型着色与社区检测正常 |
| **应用中心大屏与列表** | `/kac/overview`<br>`/kac/myApp` | **PASSED** | `verification_kac_overview.png` | 行业应用解决方案、多维度快捷入口加载正常 |
| **知识库文档分块详情** | `/kmc/8/knowledgeSegment/index` | **PASSED** | `verification_kmc_document_segments.png` | 课设报告切片列表与全文解析完整无损 |
| **缓存监控中枢** | `/monitor/cache` | **PASSED** | `verification_monitor_cache.png` | Redis 8.10.2 单机状态、36 Keys、内存与命令统计正常 |
| **服务监控 (Java 21 铁证)** | `/monitor/server` | **PASSED** | `verification_monitor_server_java21.png` | 明确验证运行在 OpenJDK 21.0.5 虚拟机，内存与 CPU 负载健康 |
| **定时任务监控** | `/monitor/job` | **PASSED** | `verification_monitor_job.png` | 知识库同步与日志清理任务调度正常 |
| **LLM 可观测性中枢** | `/kd/observability` | **PASSED** | `verification_llm_observability_dashboard.png` | LangFuse 状态已连接，Trace 追踪表与指标就绪 |
| **Bot 运营看板** | `/kd/botOperation` | **PASSED** | `verification_bot_operation_dashboard.png` | Bot 运行态统计与调用趋势呈现完备 |

---

## 四、 总体调整建议路线图 (Plan for Holistic Adjustments)

在完成全量模块联调探测后，拟按以下步骤实施总体调整：
1. **第一步（后端安全与流式修复）**：修改 `JwtAuthenticationTokenFilter.java` 重写 `shouldNotFilterAsyncDispatch() -> false`，并使用隔离 Java 21 执行 Maven 编译打包，重启后端服务；
2. **第二步（前端端口与配置规范）**：优化 `vite.config.js` 默认端口配置，避免非特权启动报错；
3. **第三步（端到端回归复检）**：在 Chrome DevTools 会话中重测 `POST /kb/conversation/send`，验证 Agent 调试对话流式打字机逐字输出、思考链折叠与工具调用结构化消息；
4. **第四步（工件归档与提交）**：更新 `walkthrough.md` 与 Git 提交记录。

