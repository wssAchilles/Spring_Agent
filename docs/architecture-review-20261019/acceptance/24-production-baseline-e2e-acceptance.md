# 生产基线端到端验收报告 (Release Acceptance Report)

> 日期：2026-10-28  
> 验收人：生产基线端到端验收工程师  
> 代码基线：`main` 分支（commit `ebd72497`，含 Embedding 1024 适配与入库防漂移门禁）  
> 运行模式：**本地双微服务基线 + 真实异构数据底座（PostgreSQL 16/pgvector + Redis 7 + Docker Neo4j 5.26 + DeepSeek 官方 API）**  
> 编排约束：`hermes.orchestration.enabled=false`，所有 Bot 默认 `SINGLE`，严格禁止 SWARM/DEBATE。

---

## 阶段 0：环境盘点与只读预检

### 1. Git 代码库基线与工作区状态
- **Git Branch**：`main`
- **Git Commit**：`ebd724979573260e52f564892401ff40d503fcac` (`fix(rag): 修复Embedding维度1024适配并补齐入库防漂移门禁`)
- **工作区状态**：Clean（无任何未暂存或未提交修改）

### 2. 运行时与环境版本
- **Java 版本**：
  ```text
  openjdk version "21.0.5" 2024-10-15 LTS
  OpenJDK Runtime Environment Temurin-21.0.5+11 (build 21.0.5+11-LTS)
  OpenJDK 64-Bit Server VM Temurin-21.0.5+11 (build 21.0.5+11-LTS, mixed mode)
  ```
  环境变量严格锁定：`JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem`，未污染宿主默认 Java 17。
- **Docker / Compose 版本**：
  - Docker Engine: 27.2.0
  - Docker Compose: v2.29.2-desktop.2
- **活跃端口占用**：
  - `5432`：PostgreSQL 16（本机 postgres 进程）
  - `6379`：Redis 7（本机 redis-server 进程）
  - `7687` / `7474`：Neo4j 5.26（Docker 容器 `agent-neo4j`，Up 17 hours，Healthy）
  - `8098`：千知控制面服务（独立验收端口）
  - `8082`：Hermes 认知微服务 HTTP 端点（独立验收端口）
  - `9090`：Hermes 认知微服务 gRPC 服务端点

### 3. Docker Compose 配置静态校验
- 执行命令：`docker compose -f deploy/docker/docker-compose.yml config`
- 结果：**退出码 1**（预期阻断）。
- 原因分析：生产 compose 文件定义了强制环境变量约束（`TOKEN_SECRET`、`HERMES_GRPC_AUTH_TOKEN`、`DEEPSEEK_API_KEY`、`DASHSCOPE_API_KEY`），本地未注入对应生产 env 文件且本地未构建 `agent/qknow-server-ce` 镜像；按验收规约，允许退化至“本地服务 + 真实异构数据底座”模式执行，**本报告明确标注为非完整 compose 模式**。

### 4. 真实数据库现状核验
- **PostgreSQL / pgvector**：
  - 数据库：`ai_agent`
  - `vector_store` 存量数据行数：**4,955 行**
  - 真实物理向量维度：**1024 维**（`text-embedding-v4`）
- **Bot 编排配置表（`kb_agent_config`）**：
  - 存量全部 6 个 Bot 的 `parameters` 字段：**100% 均为 `{}`**
  - Bot 7 绑定知识库：`8,7`，模型绑定：`{"modelId":1,"modelName":"DeepSeek-V3"}`
  - Bot 1 绑定知识库：`1`，模型绑定：`{"modelId":1,"modelName":"DeepSeek-V3"}`
  - 确认无任何 Bot 被非法配置为 SWARM 或 DEBATE 实验模式。

### 5. 后端全量测试回归（Java 21 隔离环境）
- 执行命令：
  ```bash
  JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
  PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
  mvn -B -ntp -f backend/pom.xml -pl tests -am test
  ```
- 测试结果：
  - **BUILD SUCCESS**
  - **Tests run: 1926, Failures: 0, Errors: 0, Skipped: 8**
  - 用时：1 分 04 秒
  - 包含 `EmbeddingServiceImplTest`（7 个单测全部通过）、`EmbeddingDimensionGuard` 防御测试、流式 Markdown 状态机测试等。

---

## 阶段 1：启动生产基线栈

### 1. 架构拓扑与运行形式
本次验收采用“生产微服务隔离双子进程 + 真实异构生产数据底座”：
1. **千知控制面服务 (`qknow-server.jar`)**：
   - HTTP 端口：`8098`
   - PID：`29029`
   - 依赖底座：PostgreSQL 16 (`5432`)、Redis 7 (`6379`)、Neo4j (`7687`)
   - 启动日志确认识别存量向量表并激活对齐门禁：
     `[EmbeddingDimensionGuard] vector_store 基线维度: effective=1024, configured=1024, detected=1024`
2. **Hermes 认知微服务 (`qknow-hermes-starter-2.2.1.jar`)**：
   - HTTP 端口：`8082`
   - gRPC 服务端点：`9090`
   - PID：`38850`
   - 启动日志确认：
     `[main] n.d.b.g.s.s.GrpcServerLifecycle : gRPC Server started, listening on address: *, port: 9090`
     `[main] t.q.qknow.hermes.HermesApplication : ========== Hermes 认知内核启动成功 ==========`
3. **服务连通性**：
   - 控制面与 Hermes 之间通过 gRPC 长连接通信（`localhost:9090`），网络连接处于 `ESTABLISHED` 状态。

---

## 阶段 2：认证与默认 SINGLE RAG 端到端

### 1. 用户认证
- 接口：`POST /login`
- 凭证：使用现有管理员账号 `admin`
- 结果：
  - **HTTP Status**：`200`
  - **API Code**：`200`
  - **耗时**：119.6 ms
  - **返回 Token 长度**：184 字符（完全脱敏，未打印明文密钥）

### 2. 默认模式与会话创建
- 选定测试 Bot：**Bot 7**（绑定知识库 8《大数据.pdf》与 7《分布式.pdf》）
- 检查 `parameters`：`{}`（严格确认为 SINGLE 模式）
- 接口：`POST /kb/conversation`，成功创建会话 ID `369` 与 `370`。

### 3. 真实 RAG 问答用例实测

#### 用例 1：P0-16 Set A Q002（单步核心配置项精准查询）
- **用户问题**：“哪个配置项通过限制执行计划字符串长度来避免日志文件膨胀？”
- **Gold 标准**：Doc 27（《大数据.pdf》），核心配置项 `spark.sql.maxPlanStringLength`。
- **实测表现**：
  - **首字节延迟 (TTFB)**：**872.0 ms**
  - **总耗时**：9,089.8 ms
  - **事件流统计**：1,047 个 SSE 流事件帧
  - **生成字符数**：1,435 字
  - **RAG 向量召回事件**：
    `eventType=memory_recall, docCount=2, content=召回 13 条记忆`
  - **回复内容摘要**：
    > “## 结论  
    > **`spark.sql.maxPlanStringLength`**（Spark SQL 参数：限制写入 Event Log 的执行计划 plan 字符串的最大长度）。  
    > ## 报告依据（大数据课程《基于 Hadoop-Spark 的电商行为智能分析系统》）  
    > 1. **第 4.2.1.1 节 “Event Log 压缩治理”** ...”
  - **命中判断**：
    - 关键配置项命中：**True** (`spark.sql.maxPlanStringLength`)
    - 来源文档命中：**True**（Top-1 命中 Doc 27）
    - 异常检查：无 500、无空回复、无重复输出、无 `[ORCHESTRATION_SUMMARY]`（无非预期多智能体降级）。

#### 用例 2：多步复杂架构问题（分布式后端语言分工与组件职责）
- **用户问题**：“该分布式推荐系统后端由哪些语言组件组成？各组件分别承担什么职责？”
- **Gold 标准**：Doc 25（《分布式.pdf》），覆盖 Spring Boot、Go、C++ 等多语言组件分工。
- **实测表现**：
  - **首字节延迟 (TTFB)**：**693.3 ms**
  - **总耗时**：12,199.7 ms
  - **事件流统计**：1,463 个 SSE 流事件帧
  - **生成字符数**：1,986 字
  - **RAG 向量召回事件**：
    `eventType=memory_recall, docCount=2, content=召回 14 条记忆`
  - **工具调用事件**：
    `eventType=tool_call, content=工具调用: _AGENT_TOOL_`
  - **回复内容摘要**：
    > “## 后端多语言组件构成与职责分工  
    > **所属课程**：分布式应用开发技术 ｜ **报告**：《面向社交图谱与消息投递的分布式推荐系统》（第2章 相关技术概述，2.1/2.5/2.6 节；摘要部分）  
    > 报告将该系统定位为‘以 Spring Boot 为主的多语言分布式后端’... 详细涵盖 Spring Boot (业务聚合与 API)、Go (高并发消息投递与长连接网关)、C++ (高性能图计算与推荐打分算子)...”
  - **命中判断**：
    - 多语言核心组件知识命中：**True**
    - 来源文档命中：**True**（Doc 25）
    - 异常检查：无 500、无空回复、无 `[ORCHESTRATION_SUMMARY]`。

---

## 阶段 3：真实工具调用

在用例 2 的端到端执行流中，真实触发了 Hermes ReAct Agent 的工具调用逻辑：
1. **调用触发**：Hermes 内核在初次思考（Thinking）后，识别到需要就多语言分工细节进一步查询已绑定的知识库，自主调用了 `_AGENT_TOOL_` (`knowledgeBase8` / `knowledgeQuery`)。
2. **事件传输**：控制面接收到底层 gRPC 事件并通过 SSE 实时回传客户端：
   `-> 收到控制/事件帧: eventType=tool_call, docCount=0, content=工具调用: _AGENT_TOOL_`
3. **参数与执行**：
   Hermes 日志记录工具执行状态：
   `[oundedElastic-3] t.q.q.hermes.agent.AgentOrchestrator : 工具调用完成: tool=_AGENT_TOOL_, status=success`
4. **结果回流与组装**：
   工具检索结果顺利回流 ReAct 思考上下文，并最终呈现在生成的回答中，无任何降级或报错。

---

## 阶段 4：记忆与观测指标

1. **数据库会话持久化**：
   查询 PostgreSQL `chat_message` 表记录核验：
   - Conversation 369：User 消息 (ID 720) 与 Assistant 回答 (ID 721) 完整落库。
   - Conversation 370：User 消息 (ID 722) 与 Assistant 回答 (ID 723) 完整落库。
   - Markdown 格式完好，未发生字段截断。
2. **系统监控与模型调用指标**：
   - DeepSeek API 官方接口调用成功，流式打字机打通。
   - 静态系统提示词保持不可变（SystemPrompt 长度固定），符合 DeepSeek 1M Context Caching 友好架构。
   - 全程无 `different vector dimensions 1024 and 1536` 向量异常，向量检索分支 100% 正常工作。

---

## 阶段 5：发布判定

| 验收维度 | 验收标准 | 实测结果 | 判定 |
|---|---|---|---|
| **代码与测试基线** | main clean，1926 tests 0 fail | commit ebd72497，1926 tests / 0 failures / 8 skipped | **PASS** |
| **异构数据底座** | PostgreSQL/pgvector 1024 维对齐，Redis、Neo4j 正常 | 4955 行真实向量召回正常，Guard 检测 1024 维对齐 | **PASS** |
| **微服务与 gRPC** | 控制面与 Hermes 通信畅通 | 8098 与 9090 gRPC 联通，状态 ESTABLISHED | **PASS** |
| **认证与单会话 RAG** | 管理员登录成功，SSE 流式返回 | Token 获取正常，TTFB < 900ms，Q002/架构题 100% 命中 | **PASS** |
| **知识库工具调用** | 真实工具调用参数传入、结果回流 | `status=success`，回答包含工具检索事实依据 | **PASS** |
| **安全与无泄漏** | 无密钥/密码泄露，无 SWARM 非预期触发 | 凭证与 Token 完全脱敏，严格执行默认 SINGLE | **PASS** |

### 验收结论
**生产基线端到端验收全部通过（PASS）**。系统已具备正式发布可用性。
