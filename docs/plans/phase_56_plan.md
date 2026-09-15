# Phase 56 实施方案：多智能体自组织动态微服务网关、意图自适应 RPC 通信总线与零信任主动防御协议栈

## 一、算法假设与问题边界

### 1. 唯一待验证假设 (H-PHASE56-001)
在具备自组织注册与动态健康探活的多智能体微服务架构中，通过将自然语言意图投影至阿里千问 1536 维超球面流形 $\mathbb{S}^{1535}$ 进行最近邻综合效用路由，并结合基于滑动窗口（$\Delta T \le 60\,\text{s}$）时间戳、128-bit Nonce 与 HMAC-SHA256 签名的双重零信任防御门禁与李雅普诺夫强稳定多路复用背压 RPC 总线，能够同时达成：
1. **语义意图路由准确率**：在异构智能体集群中意图路由准确率 $\ge 98\%$，单次路由代数耗时严格控制在 $\le 5.0\,\text{ms}$；
2. **零信任主动防御健全性**：过期报文、重放报文与签名被篡改的伪造报文拦截率严格为 $100\%$，未授权访问成功率为 $0\%$；
3. **动态拓扑自愈可用性**：亚健康或异常智能体节点在探活失败后 $\le 10\,\text{ms}$ 自动从路由表中摘除，流量自动平滑重选可用节点，系统无死锁、无消息悬挂；
4. **不可篡改存证闭环**：全流程签发不可变 Java 21 Record `GatewayAuditReceipt`，自验 SHA-256 摘要一致性通过率 $100\%$。

---

## 二、契约定义与核心组件设计

### 1. 核心模型与组件职责

```
+----------------------------------------------------------------------------------------------------+
|                                    SelfOrganizingGatewayCoordinator                                |
|                                       (端到端多智能体网关协调总控)                                     |
+----------------------------------------------------------------------------------------------------+
       |                                       |                                      |
       v                                       v                                      v
+-----------------------------+ +-----------------------------+ +-----------------------------------+
|    AgentGatewayRegistry     | |     IntentAdaptiveRouter    | |       ZeroTrustSecurityGate       |
| (自组织注册中心与探活自愈)  | | (千问1536维超球面意图路由)  | |  (滑动窗口Nonce防重放+ABAC鉴权)   |
+-----------------------------+ +-----------------------------+ +-----------------------------------+
                                               |
                                               v
                                +-----------------------------+
                                |    IntentAdaptiveRpcBus     |
                                | (多路复用RPC总线与背压流控) |
                                +-----------------------------+
                                               |
                                               v
                                +-----------------------------+
                                |     GatewayAuditReceipt     |
                                | (不可变Java 21 Record凭单)  |
                                +-----------------------------+
```

1. **`GatewayAuditReceipt`**：
   - 不可变 Record，字段包括：`receiptId`, `msgId`, `senderId`, `targetAgentId`, `routingPath`, `rttLatencyMs`, `nonce`, `clientSignature`, `abacDecision`, `timestamp`, `receiptHash`；
   - 提供 `verifyIntegrity()` 验证 SHA-256 自签名。
2. **`AgentGatewayRegistry`**：
   - 管理注册智能体实例元数据（ID, Name, Endpoints, Capabilities, 阿里千问 1536 维超球面聚类向量, HealthScore, Status）；
   - 支持动态注册（register）、注销（deregister）、心跳上报（heartbeat）；
   - 探活与亚健康隔离：若超时未收到心跳（TTL > 15000ms）或健康度 < 0.3，自动置为 DEGRADED 或 DEAD 并在路由中剔除。
3. **`IntentAdaptiveRouter`**：
   - 接收自然语言意图或特征向量，计算到所有健康智能体能力中心的测地线角余弦相似度；
   - 综合考虑相似度、健康得分与当前活跃并发负载，输出最优智能体。
4. **`IntentAdaptiveRpcBus`**：
   - 多路复用 RPC 总线，模拟全双工通道与请求分发；
   - 维护有界排队缓冲区（容量 $\le 1000$），当积压超过水位线（80%）时触发背压削峰；
   - 支持超时自动取消与断路降级。
5. **`ZeroTrustSecurityGate`**：
   - 维护滑动窗口时间戳校验（$\Delta T \le 60\,\text{s}$）；
   - 维护基于时间过期与容量淘汰的高性能 Nonce 缓存集合；
   - 校验 HMAC-SHA256 签名，并对调用方属性与目标资源执行 ABAC 规则鉴权；
   - 拦截过期、重放、假冒与未授权请求，生成审计拒绝原因。
6. **`SelfOrganizingGatewayCoordinator`**：
   - 编排入口：接收外部或智能体间调用请求，依次执行：零信任鉴权 -> 意图匹配路由 -> 动态探活感知 -> RPC 派发调用 -> 签发不可变存证凭单。

---

## 三、比较方案 (Baseline vs Candidate)

| 维度 | Baseline (传统静态网关) | 最小诊断方案 (静态规则加签) | 候选方案 (Candidate Phase 56) |
| :--- | :--- | :--- | :--- |
| **路由机制** | 静态 URL 路径与固定 Service 名 | 固定接口路由 + 静态权重负载均衡 | **千问 1536 维超球面意图测地线路由 + 动态负载加权** |
| **节点自愈** | 需人工刷新配置或被动等 TCP 超时 | 简单心跳，无亚健康降权自愈 | **动态自组织心跳探活 + 亚健康毫秒级剔除自愈** |
| **通信流控** | 无界缓冲，容易 Direct Memory OOM | 简单线程池限流，无背压流控 | **李雅普诺夫强稳定队列 + 反应式背压流控** |
| **安全防御** | 仅依赖静态 API Key，无防重放 | 时间戳静态校验，无 Nonce 查重 | **时间窗 + 128-bit Nonce 滑动缓存 + HMAC + 细粒度 ABAC** |
| **存证审计** | 文本日志打印，容易丢失或篡改 | 数据库异步写表，无密码学签名 | **不可变 Java 21 Record + SHA-256 密码学自验凭单** |

---

## 四、反事实消融设计与泄漏防护

1. **消融实验 A（关闭 Nonce 查重）**：
   - 重放完全相同的报文副本，若不校验 Nonce，第二条报文将被错误放行；开启 Nonce 缓存后，重放报文 100% 被实时拦截（返回 `REPLAY_ATTACK_DETECTED`）。
2. **消融实验 B（模拟智能体假死故障）**：
   - 将目标智能体标记为超时假死，路由引擎立即感知并排除该节点，将请求自动平滑路由至备用候选智能体，验证系统高可用与零悬挂。
3. **泄漏防护**：
   - 所有 Nonce 与密钥比对采用常量时间安全比较算法，杜绝基于时间差的时序侧信道攻击（Timing Attack）。

---

## 五、预算与性能指标约束

- **路由计算延迟**：单次超球面意图内积与多候选排序耗时 $\le 5.0\,\text{ms}$；
- **零信任鉴权延迟**：时间戳 + Nonce 查重 + HMAC 验签总耗时 $\le 2.0\,\text{ms}$；
- **端到端网关调度耗时**：端到端（鉴权+路由+调用+存证）代数开销 $\le 10.0\,\text{ms}$；
- **内存安全性**：Nonce 缓存与 RPC 队列严格有界，GC 水位平稳无内存泄漏。

---

## 六、最小实现文件集合与禁止修改边界

### 1. 新增核心文件（`backend/qknow-framework/qknow-ai`）
- `tech.qiantong.qknow.ai.gateway.GatewayAuditReceipt.java`
- `tech.qiantong.qknow.ai.gateway.AgentGatewayRegistry.java`
- `tech.qiantong.qknow.ai.gateway.IntentAdaptiveRouter.java`
- `tech.qiantong.qknow.ai.gateway.IntentAdaptiveRpcBus.java`
- `tech.qiantong.qknow.ai.gateway.ZeroTrustSecurityGate.java`
- `tech.qiantong.qknow.ai.gateway.SelfOrganizingGatewayCoordinator.java`

### 2. 新增契约测试文件（`backend/tests`）
- `tech.qiantong.qknow.ai.gateway.Phase56GatewayExecutionContractTest.java`

### 3. 禁止修改边界
- 严禁修改其他子模块生产表结构及已稳定的 Phase 01~55 既有核心业务代码；
- 严禁修改 Maven 依赖引入未经验证的第三方重量级微服务框架；
- 保持全系统 Java 21 隔离环境命令前缀运行。

---

## 七、验证命令与预期结果

1. **局部编译命令**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn compiler:compile jar:jar install:install -pl qknow-framework/qknow-ai -DskipTests
   ```
2. **Phase 56 专属契约单测验证**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests -Dtest=Phase56GatewayExecutionContractTest
   ```
   预期结果：8 项严苛单测全部通过（8/8 全绿，0 失败 0 错误）。
3. **全库全量防退化回归测试**：
   ```bash
   JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -pl tests
   ```
   预期结果：全库回归测试达到 **1090/1090 100% 全绿**（0 失败 0 错误）。
4. **前端生产构建检验**：
   ```bash
   cd frontend && npm run build:prod
   ```
   预期结果：0 错误 0 警告极速通过。
