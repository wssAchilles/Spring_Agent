# Harness² 只读审计与架构对照报告 (阶段 0：只读审计与假设锁定)

> **审计基准代码库**：`/Users/achilles/Documents/优秀开源项目/harness2`（Commit `1cfe254e8d25b15f37048c3730134686abe60335`，Apache-2.0）  
> **本项目阶段一基准**：分支 `feat/hermes-harness-stage2`（继承自 `feat/hermes-harness-contract`，Commit `770d7db9`）  
> **环境约束**：Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）、DeepSeek 官方 API、阿里千问 1024 维超球面 Embedding、单 Agent 默认生产基线。  
> **性质**：只读审计与假设锁定，本阶段零业务代码改动。

---

## 一、Harness² 真实算法流水线全景解构

通过对 `harness2/core/` 核心源码（`spec.py`、`tree.py`、`context.py`、`proposer.py`、`recursion.py`、`selection.py`、`library.py`、`ledger.py`、`runmark.py`）的逐行法医级代码审计，还原其真实算法流水线如下：

### 1. 核心范式：双层递归动力学 (Two-Level Recursion)
- **核心定义**：$\text{Agent} = \text{Model} + \text{Harness}$。模型权重 $\mathcal{M}$ 保持冻结，智能体外周装配系统 $\mathcal{H} = (c^1, \dots, c^8)$ 承担所有行为约束与上下文组织；
- **任务级局部递归 (Task-level Recursion: Propose–Probe–Compose)**：
  1. **Reference Execution**：基于继承的 Base Harness 进行参考运行，产出基线轨迹与交付物；
  2. **Propose**：Editor 在正交的两个维度生成变异：
     - **Invariant (INV)**：通用工作流程、自检反思、工具链调用规范（“How the agent works”）；
     - **Domain (DOM)**：领域行业规范、术语定义、法定格式与经验法则（“What knowledge the agent needs”）；
  3. **Probe**：将生成的候选护具置入 Substrate 隔离沙盒中执行，收集真实物理行为差分 $\Delta \tau$（工具报错、退出码、死循环、文件格式偏差等）；
  4. **Compose**：将多候选的执行差分注入合成上下文，利用物理可观测信号（无需外部 Ground-Truth）合成出更优的 Refined Harness；
  5. **Scaling 模式**：
     - **Parallel Recursion**：探索 $k$ 个 INV 与 $k$ 个 DOM，生成 $k$ 个 Compositions，由匿名选择器统一盲审裁决；
     - **Sequential Recursion**：迭代执行 $k$ 步连续深度嵌套微调。
- **护具级跨任务元递归 (Harnessing-level Recursion: Experience Library)**：
  任务采纳后，执行装备重置为通用初始态，而改进装备通过自省更新全局领域经验库：
  $$\mathcal{R}_{t+1} = \text{Update}(\mathcal{R}_t, \mathcal{H}_t^\star, \mathcal{D}_t)$$
  生成包含 `cue`（触发线索）、`lesson`（经验法则）、`evidence`（差分证据）与 `confidence`（置信度）的结构化 `EXPERIENCE.md`。

### 2. 八大组件映射与生命周期契约
- **常驻组件 (Always-On)**：
  - $c^1$ **System Prompt**（`systemprompt.md`）：全局角色定义；
  - $c^2$ **Guardrails**（`guardrails.md`）：硬性安全红线与行为规范；
  - $c^3$ **Memory**（`LongTermMEMORY.md`）：跨任务/同领域常驻事实；
  - $c^4$ **Tool Guidance**（`tool_descriptions/tool_guidance.md`）：工具何时使用与调用指南；
  - *硬门禁*：OpenCode 限制 24,000 字符，Codex 限制 8,000 字符，超出直接抛出 `ValueError`。
- **按需加载与拦截组件 (On-Demand & In-The-Loop)**：
  - $c^5$ **Subagents**（`sub_agents/<name>/AGENT.md`）：在**独立隔离上下文**中执行的收敛型子任务；
  - $c^6$ **Skills**（`skills/<name>/SKILL.md`）：正文不常驻，仅名称描述暴露在常驻层，触发时动态载入上下文；必须具备 `autotrigger` 机制，禁止孤岛技能；
  - $c^7$ **Scripts**（`scripts/<name>.py`）：确定性 Python 辅助脚本，通过 Bash 显式调用，编译期强校验 `compile(src, "exec")` 与引用真实性；
  - $c^8$ **Plugins / Hooks**（`plugin/<name>.ts` 或 `.codex/hooks.json`）：唯一不可被模型跳过的中间件，提供 10 类生命周期事件强制拦截与断言。

### 3. 证据树架构 (Evidence Tree)
- **外置化设计原则**：`context/task.md`、`context/rollouts/<label>.md`（完整轨迹不截断）、`context/deliverables/<label>/`（产出物）、`context/harness/`（组件树）、`context/pool/`（候选集）；
- **指针驱动**：Prompt 仅为一个极短的指令指针（`MISSION.md`），所有沉重证据作为文件存放在工作空间，由 Agent 按需查阅，彻底解决上下文窗口超限与 Prompt 截断失真问题。

### 4. 盲审隔离、预算审计与断点自愈
- **选择器隔离与匿名化 (Selection / Judge)**：
  - 选择器完全隔离于真实 Grader 评分细则（Rubric）；
  - 候选标签通过 `assign_ids` 基于任务 ID 与种子哈希打乱（`A..Z, AA..AZ`），彻底消除大模型对固定位置的偏置（Position Bias）；
- **预算记账审计 (Ledger)**：
  - 严格满足预算恒等式：$R = 4k$（$k$ base + $2k$ probes + $k$ finals）次 Rollout，与 $P = 3k$（$k$ INV + $k$ DOM + $k$ Compositions）次改进会话；
  - 记录每次执行的 USD 成本、Token 消耗与缓存命中状态，结束前调用 `assert_closed()` 严格校验；
- **断点自愈与防篡改 (Runmark & Resume)**：
  - 使用 `.adapt_complete` 标记完成，基于目录树深度计算唯一 SHA-1 指纹（`fingerprint`），一旦检测到 Harness 目录变动或答案缺失立即自愈重跑。

---

## 二、Hermes 与 Harness² 的深度对照分析

对照本项目阶段一基线（`qknow-hermes-core`，Commit `770d7db9`），进行严密的四象限对照：

### 1. 哪些机制 Hermes 已具备（Already Present）
- **不可变 8 大组件契约与凭单模型**：阶段一已通过纯 Java 21 Record 完成了全部 8 大组件建模，并由 `HermesHarnessVoucher` 聚合；
- **静态前缀防护与 DeepSeek 1M Context Caching 隔离**：`InvariantSystemPromptRecord` 构造期 Fail-Close 正则拦截动态变量；`HermesHarnessAssembler` 强制将动态 RAG 上下文放入当前轮次 `UserMessage` 前缀，SystemPrompt 静态不可变；
- **基础守卫与熔断**：`ReActCycleGuard`（防死循环与震荡）与 `ModelCallLimitHook`（步数截断）；
- **密码学常量时间指纹比对**：`matchesFingerprint()` 使用 `MessageDigest.isEqual()`；
- **流式追踪透传**：`StreamingChunk` 已支持 `harness_version` 与 `harness_fingerprint` 透传。

### 2. 哪些机制只是“命名相似，实际完全不同”（Superficial Resemblance, Fundamentally Different）
- **Scripts（脚本）**：
  - *Harness²*：允许 LLM 在运行时动态编写未经验证的 Python 脚本并通过 Bash 执行（在真实生产中极易产生 RCE 漏洞与依赖冲突）；
  - *Hermes*：遵循企业零信任原则，`SandboxedExecutionPlanRecord` 强制锁定 `allowArbitraryScripts=false`，仅允许声明式受控参数槽位，计算必须下沉到 Java 沙箱或预编译的 MCP Tool；
- **Memory（记忆）**：
  - *Harness²*：仅为一个扁平的追加式文本文件（`LongTermMEMORY.md`），无数学度量，长序列下容易产生检索稀释性遗忘；
  - *Hermes*：三层递进记忆体系（Redis 短期滑动窗口 + PostgreSQL 关系兜底 + 阿里千问 1024 维超球面单位向量余弦检索 $\cos\theta = \mathbf{u} \cdot \mathbf{v}$）；
- **Subagents（子智能体）**：
  - *Harness²*：基于独立 Markdown 描述并在 Bun/OpenCode 命令行隔离进程启动；
  - *Hermes*：基于响应式 Flux 拓扑与 gRPC 双向流（`MultiAgentOrchestrationService`，受控 SINGLE/SWARM/DEBATE），受 `DeepSeekCostGovernor` 强行保护。

### 3. 哪些机制值得并在阶段二需要改造移植（Must Adapt in Stage 2）
- **组件契约真正驱动运行时参数（Contract-Driven Runtime）**：
  - *现状*：阶段一虽然完成了 8 大 Record 建模，但 `MemoryContextStrategyRecord`（短期窗口 20/长期召回 5/反思深度 1）、`ToolGuidancePolicyRecord`（自动工具引导/Hoare 形式化校验开关）以及 `GuardrailsSpecificationRecord`（成本上限）尚未全面贯通到 `AgentOrchestrator` 底层的具体执行调用；
  - *改造*：阶段二必须让运行时参数 100% 动态读取当前绑定的 `HermesHarnessVoucher`，彻底废除硬编码分散参数；
- **全链路结构化证据采集器 (`HarnessEvidenceCollector`)**：
  - *移植点*：吸收 Harness² 的 `ledger.py`、`context.py` 与 `runmark.py` 思想，设计一套无侵入性的只读证据采集器；
  - *采集内容*：在单次会话执行流中，完整记录 `harness_fingerprint`、`voucher_version`、用户原始输入、RAG 检索命中文档与得分、工具调用时序/输入输出/耗时/退出状态、Token 实际消耗与费用评估、错误堆栈与最终生成答案；
  - *隔离性*：只读旁路记录，绝不干涉打字机流式输出与模型答复。

### 4. 哪些机制必须坚决拒绝（Must Reject）
- **坚决拒绝在线生产流量自动动态修改 Prompt / Guardrails / ToolGuidance**：
  - *理由*：在线自动变异会导致生产答复确定性崩溃、延迟暴涨 10 倍、彻底击穿 DeepSeek 缓存，引发严重的安全合规风险；
- **坚决拒绝未受控代码动态生成与热加载**：
  - *理由*：严禁向大模型开放宿主环境任意代码编译执行权限；
- **坚决拒绝大规模盲目学术 Benchmark 跑分（WorkBuddy / LAB）**：
  - *理由*：偏离企业知识库主业；阶段二必须聚焦于本地可控、低成本、高保真的小样本离线实测（如 Set A / P0-16 真实知识库用例）。

---

## 三、阶段二唯一待验证假设锁定

根据上述审计与边界裁决，阶段二坚决不将“在线自我修改”作为主线，正式锁定本阶段**唯一待验证科学与工程假设**：

> ### 【阶段二唯一待验证假设 (Hypothesis H-STAGE2-01)】
> **在 Hermes 单 Agent (SINGLE 模式) 架构上，将阶段一建立的不可变 Harness 组件契约（`GuardrailsSpecificationRecord`、`MemoryContextStrategyRecord`、`ToolGuidancePolicyRecord`）真正接入并驱动运行时执行链路，并挂载轻量只读的 `HarnessEvidenceCollector`（证据采集器）：**
> 1. **在保持默认生产行为与回答语义 100% 零回归的前提下；**
> 2. **能够对单次请求产生确定性、包含完整时序/RAG/工具/成本物理证据的不可变凭单（`HarnessEvidenceRecord`）；**
> 3. **全量通过 Java 21 1942+ 项既有测试，为后续离线小样本下的受控 Harness 变异与探针评测提供唯一可复现的基石证据。**

---

## 四、只读审计结论

1. **架构契合度确认**：Harness² 论文与代码中的“外周装配（Harness）抽象”和“行为差分证据收集（Evidence Accounting）”是真正具备生产价值的硬核机制；
2. **阶段二核心收口目标明确**：
   - 坚决不做在线自动演化；
   - 坚决不碰 MCP stdio、HITL、SWARM；
   - 聚焦完成两件事：**① 组件契约深度接管运行时硬编码；② 落地不可变证据采集器（Evidence Collector）**。
3. **准入判定**：阶段 0 只读审计完成，未触碰任何业务代码与生产配置，唯一待验证假设已形式化锁定，准予进入阶段 1（定向论文与代码检索）。
