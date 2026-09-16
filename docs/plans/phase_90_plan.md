# Phase 90 实施详案：企业级生产 MCP 工具链智能动态编排、跨微服务拓扑自治路由与流式容错自愈中枢

## 1. 核心待验证假设与边界定义

### 1.1 唯一待验证算法假设
**`H-PHASE90-001`**：
在企业级分布式复杂工具链生态中，通过构建**跨微服务 MCP 拓扑图与自适应测地路由引擎 (CrossMicroserviceMcpTopologyRouter)**、**工具链有向依赖无环图验证与死锁环路检测自愈器 (ToolchainDependencyDagGuard)**、**多源流式工具自适应容错重试与反事实变异自愈器 (StreamingToolchainFaultToleranceGovernor)**，以及 **1000Hz 定长 4096 槽位 Disruptor 无锁工具编排中枢总线 (McpOrchestrationControlBus)**：
1. 在包含 20+ 个异构微服务节点的分布式拓扑中，加权测地最优工具链路径求解耗时严格 $\le 100\mu\text{s}$，跨微服务工具寻路成功率 $\ge 99.5\%$；
2. 实时检测多工具链数据依赖中的死锁环路，在 $\le 50\mu\text{s}$ 内主动破环切断并注入解耦补偿，工具链执行死锁发生率严格为 $0.0\%$；
3. 针对微服务抖动或分块超时，自适应三态断路器在 $\le 10\text{ms}$ 内完成熔断切换，3 轮反思式自愈成功率 $\ge 90\%$；
4. 1000Hz Disruptor 无锁总线非阻塞写入 $\le 50\text{ns}$，JitterGuard 监控时钟抖动瞬切 `STATUS_DEGRADED_TOOL_FALLBACK` 软着陆，不可变存证凭单 SHA-256 自签名验真 100% 通过。

### 1.2 输入、输出与不可变量
- **输入**：用户业务查询意图 $q$（对应阿里千问 1536 维超球面单位向量 $\mathbf{v}_q$）、微服务注册表 $\mathcal{V}_{\text{svc}}$（含各服务工具集、实时 RTT、错误率、节点向量）、工具间依赖候选集合 $\mathcal{A}_{\text{dep}}$；
- **输出**：无死锁的工具链有序执行序列 $\mathcal{T}_{\text{exec}}$、各工具实际调用与自愈结果、不可变存证凭单 `McpOrchestrationReceipt`；
- **不可变量**：
  - 阿里千问 1536 维向量模长恒等于 $1.0 \pm 10^{-4}$；
  - 经过 DAG 门禁后的调用链必须严格为有向无环图，死锁率恒等于 0；
  - 存证凭单密码学签名由载荷 SHA-256 哈希计算，保证防篡改。

### 1.3 Baseline 与 Candidate 精确定义
- **Baseline（基线）**：Phase 85 交付的单点 MCP 中继网关（`StreamingMcpRelayGateway`），仅支持单工具调用与静态契约匹配，无跨微服务拓扑路由、无全局 DAG 依赖死锁排查，上游微服务网络抖动时直接抛出异常失败；
- **Candidate（候选）**：Phase 90 全量中枢系统，由拓扑测地路由器、DAG 死锁守卫、流式自适应容错调节器与 Disruptor 4096 槽位无锁总线联动，支持多跳自治寻路、死锁自动解耦与 3 轮变异自愈。

---

## 2. 实验消融与反事实设计

1. **消融实验 1：测地大圆弧加权路由 vs 朴素随机/轮询路由**：
   - 在 20 节点拓扑中注入 3 个高延迟（RTT > 1000ms）与 2 个高错误率（ErrorRate > 80%）的亚健康节点；
   - 验证加权测地路由是否能 100% 自动绕开故障节点，将端到端 P99 延迟稳定在最优区间。
2. **消融实验 2：DAG 死锁拦截自愈 vs 裸跑执行**：
   - 人为构造包含 $T_1 \to T_2 \to T_3 \to T_1$ 的三元环形依赖；
   - 验证 ToolchainDependencyDagGuard 是否在 $\le 50\mu\text{s}$ 内精准检测出环路，并执行最小破环自愈，输出合法拓扑序。
3. **消融实验 3：自适应断路自愈 vs 持续重试**：
   - 模拟上游服务断网，对比三态断路器（快速切入 OPEN 降级）与固定重试（引发线程池阻塞）在背压控制与吞吐量上的差异，验证李雅普诺夫强稳定性。

---

## 3. 数据泄漏防护与指标预算

### 3.1 泄漏防护与隔离
- 测试数据集与生产拓扑隔离，使用确定性拓扑种子生成微服务测试网络；
- 契约单测不进行真实外部 HTTP/gRPC 网络请求，采用纯内存微秒级仿真桩验证核心数学算法与状态机流转。

### 3.2 性能与资源预算
- **拓扑路由单步求解耗时**：$\le 100\mu\text{s}$（实测预期 $\le 40\mu\text{s}$）；
- **DAG 拓扑死锁检测与自愈耗时**：$\le 50\mu\text{s}$（实测预期 $\le 20\mu\text{s}$）；
- **断路器状态判定耗时**：$\le 10\mu\text{s}$；
- **Disruptor 总线写入延迟**：$\le 50\text{ns}$；
- **堆内存额外开销**：单会话增量对象 $\le 128\text{KB}$，无内存泄漏。

### 3.3 失败语义与固定错误码
- `ERR_MCP_TOPO_UNREACHABLE`：微服务拓扑图无连通路径；
- `ERR_MCP_DAG_DEADLOCK_DETECTED`：检测到不可调和的工具依赖环路；
- `ERR_MCP_CIRCUIT_OPEN`：下游微服务已处于熔断隔离状态；
- `ERR_MCP_STREAM_CHUNK_TIMEOUT`：流式分块传输超出看门狗阈值；
- `ERR_MCP_SELF_HEALING_EXHAUSTED`：反思变异达到 3 轮上限，触发安全软着陆降级。

---

## 4. 最小实施文件集合与明确禁止修改边界

### 4.1 新增文件清单 (共 9 个文件)
1. 契约 DTO 模块（位于 `qknow-mcp/qknow-mcp-core/src/main/java/tech/qiantong/qknow/mcp/core/orchestration/dto/`）：
   - `McpTopologyNodeState.java`：微服务节点状态与千问超球面单位向量 Record
   - `ToolchainDependencyEdge.java`：工具链有向依赖弧与解耦标记 Record
   - `StreamingToolChunkEventFrame.java`：1000Hz 流式分块事件帧 Record
   - `McpOrchestrationReceipt.java`：不可变密码学存证凭单 Record
2. 核心引擎模块（位于 `qknow-mcp/qknow-mcp-client/src/main/java/tech/qiantong/qknow/mcp/client/orchestration/engine/`）：
   - `CrossMicroserviceMcpTopologyRouter.java`：多微服务拓扑测地路由引擎
   - `ToolchainDependencyDagGuard.java`：DAG 依赖死锁检测与破环自愈器
   - `StreamingToolchainFaultToleranceGovernor.java`：自适应三态断路器与 Reflexion 自愈器
   - `McpOrchestrationControlBus.java`：1000Hz 定长 4096 槽位 Disruptor 无锁编排总线
3. 专属契约单元测试（位于 `backend/tests/src/test/java/tech/qiantong/qknow/mcp/`）：
   - `Phase90McpOrchestrationContractTest.java`：8 项严苛契约测试套件

### 4.2 明确禁止修改的边界
- 严禁修改历史 Phase 01~89 已交付的任何生产源码与测试文件；
- 严禁改动任何数据库 DDL 与生产配置文件；
- 严禁在未经用户审批前擅自创建或修改业务代码。

---

## 5. 完整可复现验证命令

```bash
# 1. 局部模块编译与安装
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean compile -pl qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn install -pl qknow-mcp/qknow-mcp-core,qknow-mcp/qknow-mcp-client -DskipTests

# 2. Phase 90 专属契约测试 (8/8 全绿)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase90McpOrchestrationContractTest

# 3. 全库全量防退化回归测试 (突破 1376 项全绿大关)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests

# 4. 前端生产打包构建校验
npm --prefix frontend run build:prod
```

---

## 6. 独立授权边界与停止条件

- **第一回合边界**：仅限只读检查、双路学术与工业研学、编制详案与实施计划 Artifact，等待用户审批；
- **立即停止条件**：
  - 契约测试出现不可控的逻辑冲突或死锁自愈失效；
  - 单步拓扑求解耗时超过 $100\mu\text{s}$ 或 DAG 环路检测耗时超过 $50\mu\text{s}$；
  - 全量防退化测试出现任一失败或错误。
