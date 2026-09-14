# Phase 47 实施方案：分布式多智能体网格与声明式 A2A (Agent-to-Agent) 通信 DSL 引擎

## 一、方案核心目标与唯一可证伪假设

### 1.1 唯一待验证假设 (Unique Falsifiable Hypothesis)
> **假设 H-PHASE47-001**：在分布式多智能体协作网络中，引入不可变标准通信信封 `A2AMessageEnvelope`（携带分布式追踪链路与时效安全租约）、基于阿里千问 1536 维超球面测地距离的 `AgentCard` 语义自适应竞标路由机制，以及内置 Kahn 算法拓扑排查与 Schema 静态门禁的声明式工作流 DSL 引擎 `DslWorkflowEngine`，能够在向后兼容现有 `TopologicalPhasedDispatcher` 与 `SharedBlackboard` 的前提下，实现多智能体工作流 DSL 静态编译耗时 $\le 10\text{ms}$、拓扑死锁环路静态拦截率 100%、Agent Card 语义意图匹配度 $\ge 90\%$，且后端全库既有 1010 项测试 100% 保持全绿。

---

## 二、架构设计与落地组件清单

所有新增组件均严格按照企业级 DDD 与 SOLID 原则，落位于 `backend/qknow-hermes/qknow-hermes-core` 模块中：

```text
backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/a2a/
├── envelope/                                  # A2A 协议信封层
│   ├── A2AMessageType.java                    # 消息类型枚举 (CFP_SOLICIT, BID_PROPOSE, VOTE, SYNC, etc.)
│   └── A2AMessageEnvelope.java                # 不可变协议信封 Record (携带 traceId, leaseToken, payload)
├── card/                                      # 智能体名片与能力网格层
│   ├── AgentCard.java                         # 智能体语义名片 Record (含千问 1536 维超球面中心与信誉)
│   └── AgentMeshRegistry.java                 # 智能体分布式能力注册网格与自适应竞标调度器
└── dsl/                                       # 声明式工作流 DSL 引擎
    ├── WorkflowDefinition.java                # 工作流定义根模型 (name, version, nodes, edges)
    ├── WorkflowNode.java                      # 任务节点模型 (id, capability, timeout, required)
    ├── WorkflowEdge.java                      # 依赖有向边模型 (sourceNodeId, targetNodeId)
    ├── DslWorkflowCompiler.java               # 三阶编译门禁器 (Schema校验, Kahn算法无环排查, 存活断言)
    ├── DslWorkflowEngine.java                 # 声明式 DSL 并发调度执行引擎
    └── DslCyclicDependencyException.java      # 编译期拓扑环路异常
```

---

## 三、8 大严苛契约测试规划 (`Phase47A2ADslMeshContractTest`)

1. **Contract 1: A2AMessageEnvelope 不可变消息信封序列化与反序列化契约**：
   - 验证信封结构完备性，traceId、spanId、leaseToken、时间戳无损序列化；
2. **Contract 2: AgentCard 语义名片注册与千问 1536 维超球面测地线内积竞标契约 (定理 1.2)**：
   - 验证凸组合得分 $\mathcal{S}(Q, \mathcal{A}) = 0.70 \cos(\theta) + 0.30 R$，高相关专家节点精准胜出（得分 $\ge 0.85$）；
3. **Contract 3: 瞬态安全租约（Security Lease Token）时效验证与抗重放拦截契约**：
   - 验证在租约有效期（60s）内合法放行，过期或篡改租约 100% 阻断；
4. **Contract 4: 声明式 DSL JSON/YAML 解析与 WorkflowDefinition 契约**：
   - 验证工作流 JSON 字符串无损解析为强类型对象模型；
5. **Contract 5: 三阶编译门禁之 Kahn 拓扑排序无环检查与死锁 100% 静态拦截契约 (定理 1.1)**：
   - 构造包含 A->B->C->A 简单环的有向图，编译器在 $\le 1\text{ms}$ 内检测并抛出 `DslCyclicDependencyException`，准确指明环路节点；
6. **Contract 6: 三阶编译门禁之能力存活断言与兜底回退降级契约**：
   - 验证节点声明了未在线的能力时，触发降级至 DefaultFallbackWorker 或编译警告，不引发悬挂；
7. **Contract 7: DslWorkflowEngine 端到端拓扑分层调度与黑板事实回填契约**：
   - 构造 4 节点钻石型 DAG（A -> [B, C] -> D），验证 B 与 C 并发执行，D 汇聚 B 和 C 的黑板上下文并输出最终答案；
8. **Contract 8: DSL 编译性能与轻量原子重载契约**：
   - 验证百级节点拓扑图的 Kahn 算法编译与分层排版耗时严格 $\le 10\text{ms}$，支持运行时基于 `AtomicReference` 零停机热更。
