# Hermes Harness² 阶段二架构多智能体交叉辩论与裁决报告

> **执行阶段**：阶段 3（多 Agent 交叉辩论与终审裁决）  
> **辩论主体**：
> - **Agent A**：Harness² 理论与算法审计员 (`a654165a-f8a8-4e7d-a8bf-524ed32c03f8`)
> - **Agent B**：生产工程与 Java 适配审计员 (`478dab7a-15d7-4840-9255-c6f950f378de`)
> - **Agent C**：评测、风险与反事实审计员 (`630415a9-01f9-4bc0-9f26-84343f5121e4`)
> - **主 Agent**：终审主裁决人 (Chief Architecture Arbiter)  
> **基准分支**：`feat/hermes-harness-stage2`  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

## 一、Round 1：首轮核心主张与争鸣对决

### 1. 焦点议题一：Harness² 的“Propose–Probe–Compose”闭环是否可以直接用于生产？
- **Agent A（理论视角）**：
  从数学动力学推导，Harness² 的 Propose–Probe–Compose 本质是一个基于物理差分（$\Delta \tau$）的无标注强化变异算子。当变异步数 $k \ge 2$ 且满足预算恒等式（$R=4k, P=3k$）时，它确实能在多项任务中收敛。但论文消融实验直接证明：纯 Prompt 微调的增益仅有 1.5 分，核心收益（-3.7 与 -5.3 分）均来自于执行脚本与结构化技能。如果把这一套照搬到生产实时流量中，模型对 System Prompt 的微小改动将直接触发极端语义漂移，在缺乏大量重复采样（$k \ge 2$）的情况下，根本无法突破模型输出的“物理噪声底（Noise Floor）”。
- **Agent B（工程视角）**：
  直接平移到生产无异于工程灾难！Harness² 的 Python 源码中充满高频的本地磁盘同步读写（`context.py` 创建海量目录树）与阻塞子进程执行。更致命的是，Harness² 动态修改 `systemprompt.md` 会彻底打碎 DeepSeek 官方 API 的 1M Context Caching（上下文缓存），导致每次请求都无法复用静态 KV 缓存，首字延迟增加 300%~500%，Token 账单暴涨 10 倍。
- **Agent C（风控视角）**：
  坚决反对！Harness² 在学术环境评测通过修改 Prompt 适应测试集，在企业生产中属于严重的过拟合与数据渗透风险。企业系统要求 100% 幂等性、确定性与可追溯性，绝对不允许生产流量出现未经验证的“在线变异”。

### 2. 焦点议题二：动态变异 Prompt 究竟收益大还是风险大？
- **争鸣共识**：
  三方一致裁定：**在生产运行时，动态变异 Prompt 风险呈指数级放大，而边际收益趋近于零**。
  - 风险 1（性能与成本毁灭）：击穿 DeepSeek 缓存，延迟与成本失控；
  - 风险 2（安全越狱）：LLM 动态生成的 Prompt 可能被恶意用户 Prompt Injection 诱导越狱；
  - 风险 3（不可复现）：故障发生时无法定位是哪次微小变异破坏了业务规则。

### 3. 焦点议题三：阶段二到底应不应该触碰 MCP / SWARM / 审批流？
- **Agent B 提出警示**：
  当前 Hermes 的 MCP stdio 连接池、SWARM 拓扑与 HITL 审批流是高度复杂的分布式状态机。阶段一刚刚建立了不可变 Harness 契约，如果阶段二同时修改这些核心模块，将引入大量不确定性变量，根本无法建立可证伪的单变量因果实验。
- **Agent C 坚决支持**：
  阶段二必须严守《AGENTS.md》业务边界铁律！严禁扩散到 MCP 与 SWARM，必须锁定在 **SINGLE 模式** 下完成“契约真正驱动参数 + 旁路只读证据采集”，严格实行单变量验证。
- **Agent A 理论对齐**：
  单 Agent 架构是多智能体网络（SWARM）的最小细胞基元。细胞内部的契约与证据链未完全闭环前，向多细胞网络扩展只会成倍放大通信与状态同步噪声。

### 4. 焦点议题四：怎样才能让执行证据链“绝对可信、不可篡改”？
- **Agent B 提出方案**：
  必须基于纯 Java 21 Record 构建不可变 `HarnessEvidenceRecord`。记录单次请求的 SHA-256 护具指纹、版本号、时间戳、RAG 检索分片与分数、工具调用输入输出与耗时、Token 成本以及最终答案。比对必须使用 `MessageDigest.isEqual()` 常量时间防御侧信道。
- **Agent A 补充约束**：
  证据采集必须是只读旁路（Read-Only Bypass），通过响应式流的 `doOnNext` 或监听器模式解耦，绝不修改、拦截或延迟主执行流水线的打字机输出。
- **Agent C 提出判据**：
  证据记录生成后必须支持计算整体摘要哈希（Digest），能够被测试套件确定性反序列化与离线对账校验。

---

## 二、Round 2：相互找漏洞、反事实质疑与共识收敛

### 1. 相互质疑与反事实攻防
- **质疑 1（Agent C 质询 Agent A）**：
  “Agent A 在理论推导中频繁提及跨任务经验库（Experience Library）和元规则提取。如果在阶段二引入类似 CLIN 的跨任务自动规则注入，如何保证历史错误经验不被反向放大？如果检索出的经验产生幻觉，如何自愈？”
  - **Agent A 回应与退让**：
    “Agent C 的质疑切中要害。跨任务经验库在数学上必须依赖严格的泛化门禁与反思置信度阈值。在 Hermes 目前仅完成阶段一契约的情况下，缺乏离线验证环境。**我同意在阶段二坚决不做跨任务自动规则生成**，仅在参数契约中保留离线可配置的槽位，留待后续阶段验证。”
- **质疑 2（Agent A 质询 Agent B）**：
  “Agent B 强调 Java 21 Record 的不可变性，但阶段一实施的 `HermesHarnessVoucher` 中的 `MemoryContextStrategyRecord` 与 `ToolGuidancePolicyRecord` 实际上并没有真正在 `AgentOrchestrator` 的执行路径中生效（短期窗口仍可能读默认值）。这是不是纸面契约？”
  - **Agent B 坦承并给出解决方案**：
    “Agent A 的批评完全属实！阶段一重点是解耦契约与防止 System Prompt 注入，而参数生效路径尚未完全闭环。**这正是阶段二的核心使命**：阶段二必须修改 `AgentOrchestrator`，让单次执行完全由传入的 Voucher 决定其记忆窗口深度、工具引导拦截策略与成本保护，彻底告别硬编码。”
- **质疑 3（Agent B 质询 Agent C）**：
  “Agent C 主张最小实现，如果阶段二只做参数接通和只读证据采集，不跑学术 Benchmark，怎么证明相比阶段一有提升？”
  - **Agent C 严密辩护**：
    “我们不做学术跑分，但做**可证伪的契约验证与真实用例回归**！第一，证明契约参数变动能够精准改变运行时行为（例如窗口截断、工具拦截）；第二，证明挂载证据采集器后，既有生产行为 100% 保持确定性、语义零破坏，1942+ 项全量测试 0 fail，且每次执行都能吐出完整、可核验的 `HarnessEvidenceRecord`。这就是工业级工程提升！”

### 2. 三方收敛共识
三方在第二轮辩论结束时达成高度一致的决策共识：
1. **坚决拒绝（Hard Reject）**：
   - 坚决拒绝在生产环境中在线动态重写 Prompt、Guardrails 或 ToolGuidance；
   - 坚决拒绝开放大模型在宿主环境生成并执行任意脚本；
   - 坚决拒绝在阶段二改动 MCP stdio、HITL 审批流或 SWARM 多智能体网络；
   - 坚决拒绝开展脱离企业私域知识库的大规模学术 Benchmark 跑分。
2. **阶段二核心落地（Stage 2 Must-Do）**：
   - **机制 1：契约参数全面驱动运行时**：让 `MemoryContextStrategyRecord`、`ToolGuidancePolicyRecord` 和 `GuardrailsSpecificationRecord` 深度接驳 `AgentOrchestrator` 运行链路；
   - **机制 2：全链路只读不可变证据采集器 (`HarnessEvidenceCollector`)**：吸收 `ledger.py` 与 `runmark.py` 思想，以 Java 21 Record 沉淀不可变执行凭单 `HarnessEvidenceRecord`（含指纹、RAG 片段、工具时序、成本、耗时）；
   - **机制 3：高保真离线小样本与全量回归验证**：编写完整的不可变契约测试，并执行 2 次全量 `mvn clean test` 保证 1942+ 项测试零回归。
3. **保留演进（Stage 3 Future Evolution）**：
   - 离线受控环境下的自动化变异与探针搜索（Offline Propose–Probe–Compose）；
   - 结构化跨任务经验沉淀与技能库进化（EnterpriseSkillBank 自省沉淀）。

---

## 三、终审裁决（主 Agent 裁决裁定）

作为终审主裁决人，综合 Agent A（理论）、Agent B（工程）与 Agent C（风控）的交叉辩论成果，宣布终审裁决：

### 1. 终审裁定意见
- **完全采纳三方共识**：立即启动阶段二任务书冻结，严禁概念漂移，坚决不碰在线 Prompt 变异与 MCP/SWARM 核心；
- **批准阶段二核心实施方案**：
  1. 深度接驳阶段一不可变契约，实现运行时参数的契约化驱动；
  2. 实现轻量、纯内存、只读旁路的 `HarnessEvidenceCollector` 与不可变凭单 `HarnessEvidenceRecord`；
  3. 新增契约驱动与证据链定向单测，并通过全量构建门禁验证。

### 2. 阶段二落地准入令
裁决生效，即刻进入阶段 4：编写并冻结《Hermes Harness² 阶段二架构实施任务书》，计算其 SHA-256 签名，随后进入代码实施。
