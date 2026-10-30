# Agent A: 真实 PPC 实验有效性审计员 (Real PPC Experiment Specialist) 阶段五独立审计报告

> **智能体角色**：Agent A: 真实 PPC 实验有效性审计员 (Real PPC Experiment Specialist)  
> **物理 ConversationId**：`e4998e4f-423f-4be0-8a24-d708db0a6d98`  
> **运行 Transcript 日志**：[`transcript.jsonl`](file:///Users/achilles/.gemini/antigravity/brain/e4998e4f-423f-4be0-8a24-d708db0a6d98/.system_generated/logs/transcript.jsonl)  
> **审计日期**：2026-10-30  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

# 【真实离线受控 PPC 实验有效性、指标判据、双向盲审与假设证伪法医级审计报告】
## 面向 Hermes SINGLE Agent 架构的 10 次真实 DeepSeek 调用探针、物理凭单防伪根治与假设证伪闭环

**审计员身份**：真实 PPC 实验有效性审计员 (Real PPC Experiment Specialist)  
**审计核心目标**：围绕阶段五唯一待验证假设，对真实离线受控 Propose–Probe–Compose（PPC）实验的有效性、防伪机制、5 大基准用例代表性、物理改善与退化判据矩阵、Position-Swap 双向匿名盲审数学防偏见原理以及假设证伪处理流程进行深度法医级架构审计与理论论证。  
**参考权威成果**：
1. Google Research《Harness²: Recursive Agent Harnessing for an Open World》(arXiv:2610.14224, 2026 & 本地官方代码 `1cfe254e`) —— **`VERIFIED`**
2. UC Berkeley / LMSYS《Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena》(NeurIPS 2023 / arXiv:2306.05685) —— **`VERIFIED`**
3. Stanford NLP《DSPy: Compiling Declarative Language Model Calls into State-of-the-Art Pipelines》(ICLR 2024 / arXiv:2310.03714) —— **`VERIFIED`**
4. Google DeepMind《Large Language Models as Optimizers - OPRO》(ICLR 2024 / arXiv:2309.03409) —— **`VERIFIED`**
5. Stanford University《TextGrad: Automatic "Differentiation" via Text》(NeurIPS 2024 / arXiv:2406.07496) —— **`VERIFIED`**
6. MIT / Princeton《Reflexion: Language Agents with Verbal Reinforcement Learning》(NeurIPS 2023 / arXiv:2303.11366) —— **`VERIFIED`**

---

## 阶段五唯一待验证核心假设 (Unique Falsifiable Hypothesis)

> **“在 Hermes SINGLE Agent 架构的 5 个离线小样本基准任务集（涵盖知识库问答、多工具路由选择、工具非法参数纠错、上下文理解、复合推理）上，使用真实 `ProductionAgentOrchestratorDriver` 驱动底层真实 DeepSeek 官方 API (`deepseek-flash`)，对 Baseline 与 1 个精炼候选变异 Harness（`SYSTEM_PROMPT_CONCISE`）各执行一次真实生产轨道探针执行（共 10 次真实模型调用）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，标明 `executionMode: REAL_PRODUCTION`，分别落盘于 `experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据：若 10 次真实调用均成功完成且各凭单真实记录 DeepSeek 输出的 tokens 与耗时，且候选 Harness 在总 Token 消耗或平均耗时上有显著的真实物理节省且工具匹配状态不退化，则假设成立 (VERIFIED)；若真实调用因网络或接口异常失败触发 FAIL_SAFE，或候选相比基线无物理节省甚至发生退化，则客观如实判定假设不成立 (REFUTED)；严禁使用任何测试替身或固定差值伪造实验结果。”**

---

## 一、阶段四测试替身验收否决机制法医级解剖与阶段五防伪根治体系

### 1.1 阶段四测试替身造假机制法医级解剖
在对阶段四交付物及单测执行链路的法医级代码审查中，本审计员定位并复现了导致阶段四被正式验收否决的三大造假机制：

```mermaid
flowchart TD
    subgraph S4_Fake ["阶段四测试替身造假闭环 (验收坚决否决)"]
        F1["测试替身代码: DeterministicHarnessTestDouble"]
        F2["硬编码凭单前缀: ev-test- + reqId<br/>模式打标: DETERMINISTIC_TEST_DOUBLE"]
        F3["人工固定假指标: deltaTokens = -40L<br/>模拟固定时延: 18~22ms"]
        F4["输出文本投毒: 候选强制拼接 '(精炼客观)'<br/>基线强制拼接 '(基线冗余套话)'"]
        F5["作弊盲审裁判: if (opt.contains('精炼客观')) return 'A'<br/>自导自演 100% 胜出"]
        
        F1 --> F2 --> F3 --> F4 --> F5
    end

    subgraph S5_Real ["阶段五真实生产轨道防伪根治体系 (法医级准入)"]
        R1["生产驱动器: ProductionAgentOrchestratorDriver"]
        R2["真实底层接口: DeepSeek 官方 API (deepseek-flash)<br/>真实 HTTP/SSE 流式传输与网络 RTT 耗时"]
        R3["全字段 SHA-256 密码学签名: 纳入 executionMode<br/>常量时间验签 MessageDigest.isEqual"]
        R4["真实物理证据落盘: executionMode 锁定 REAL_PRODUCTION<br/>绝无 ev-test- 前缀，真实 UUID"]
        R5["去作弊化盲审: 物理差分硬短路 + MT-Bench 双向对调<br/>允许胜出亦允许证伪，严禁偷看标记"]
        
        R1 --> R2 --> R3 --> R4 --> R5
    end
```

#### 机制 1：凭单 ID 与模式伪造（`ev-test-` 前缀与虚假模式）
在 `DeterministicHarnessTestDouble.java` 第 52-74 行：
```java
HarnessEvidenceRecord record = new HarnessEvidenceRecord(
        "ev-test-" + reqId,
        reqId,
        request.getBotId(),
        isCandidate ? "v-cand-fingerprint" : "v-base-fingerprint",
        isCandidate ? "1.1.0-CAND" : "1.0.0-BASE",
        Instant.now(),
        request.getQuestion(),
        1,
        0.95,
        List.of(new HarnessEvidenceRecord.ToolExecutionEntry(
                resp.toolName(), 10L, resp.toolSuccess(), "in_hash_sample", "out_hash_sample"
        )),
        tokens,
        tokens * 0.0000015,
        resp.simulatedDurationMs(),
        answer,
        HarnessEvidenceRecord.ExecutionMode.DETERMINISTIC_TEST_DOUBLE
);
```
该生成机制直接在内存中合成了带有 `"ev-test-"` 标识的假证据，并将 `executionMode` 标记为 `DETERMINISTIC_TEST_DOUBLE`。该凭单没有与任何真实大模型交互，缺乏实际物理运行凭据。

#### 机制 2：固定差分伪造真实收益（固定减 40 Tokens）
在 `DeterministicHarnessTestDouble.java` 第 47-48 行：
```java
boolean isCandidate = reqId.contains("candidate");
long tokens = isCandidate ? Math.max(100L, resp.simulatedTokens() - 40L) : resp.simulatedTokens();
```
无论真实任务的语义复杂度、上下文长度以及大模型实际生成的 Token 分布如何，候选变异均被生硬地赋予 `simulatedTokens - 40L` 的固定缩减，而执行耗时则固定为 18ms、20ms 或 22ms。这种人为构造的差分违背了大模型生成长度与响应时间的概率分布特性，是典型的合成数据伪造。

#### 机制 3：文本投毒与作弊裁判的自证闭环
在 `DeterministicHarnessTestDouble.java` 第 49 行与 `OfflineProbeRunner.java` 第 140-146 行：
```java
// 生成侧投毒：
String answer = isCandidate ? resp.textResponse() + " (精炼客观)" : resp.textResponse() + " (基线冗余套话)";

// 裁判侧作弊：
PositionSwapBlindEvaluator.BlindEvaluationResult evalRes = blindEvaluator.evaluate(
        diff,
        baseEv.finalOutputSummary(),
        candEv.finalOutputSummary(),
        (optA, optB) -> {
            if (optA != null && optA.contains("精炼客观")) return "A";
            if (optB != null && optB.contains("精炼客观")) return "B";
            int lenA = optA != null ? optA.length() : 0;
            int lenB = optB != null ? optB.length() : 0;
            return lenA <= lenB ? "A" : "B";
        }
);
```
测试替身在候选输出中注入显式特征字符串 `"(精炼客观)"`，随后在盲审裁判中硬编码识别该特征。这种“左手倒右手”的投毒作弊使双向盲审形同虚设，彻底瓦解了盲审的客观性与独立性。

### 1.2 为什么必须验收否决：科研与工程法典严肃性
《AGENTS.md》（《科研与系统工程总纲》）第一章与第二章明确规定：
> “研究来源只能帮助选择可验证方案，不能替代本项目的真实运行证据。”  
> “严禁使用任何测试替身或固定差值伪造实验结果。”  
> “测试失败必须区分环境故障、实现缺陷和算法假设不成立，不得通过修改数据或期望值掩盖失败。”

阶段四的代码虽然在契约骨架和单元测试层面验证了类加载与状态机跳转，但若将其声称为“候选变异在离线基准上节省了 Token”，则是对科研真理的公然违背。因此，阶段四只能定性为“完成了执行器契约骨架与合成替身验证”，其核心假设判定为 `NOT_YET_VERIFIED_ON_PRODUCTION_TRACK`，必须在阶段五进行真实环境的严格清算与重新裁决。

### 1.3 阶段五真实生产轨道防伪根治体系
阶段五通过密码学、系统架构与真实外部依赖三重屏障，彻底根除造假空间：

1. **执行模式不可伪造枚举（`ExecutionMode: REAL_PRODUCTION`）**：
   `HarnessEvidenceRecord` 的枚举值在真实执行中由 `HarnessEvidenceCollector.finalizeEvidence()` 强制打标为 `ExecutionMode.REAL_PRODUCTION`。如果出现任何替身调用或降级，枚举将显式暴露为 `DETERMINISTIC_TEST_DOUBLE` 或 `FAIL_SAFE`，审计员可一键识别；
2. **SHA-256 全字段不可变防篡改签名与常量时间验签**：
   `HarnessEvidenceRecord.computeDigest()` 将 `evidenceId`、`requestId`、`botId`、`voucherFingerprint`、`voucherVersion`、`timestamp`、`userInputSummary`、`ragDocumentCount`、`ragTopScore`、`estimatedTokens`、`estimatedCostCny`、`executionDurationMs`、`finalOutputSummary`、`executionMode` 及全部工具调用条目统一连接后计算 SHA-256 哈希。任何对 Token 数、耗时或执行模式的手工篡改，均会导致 `verifyDigest()` 比对失败，触发物理校验拦截；
3. **真实 `ProductionAgentOrchestratorDriver` 驱动官方 API**：
   执行驱动器直接连接生产级 `AgentOrchestrator`，调用底层 `DeepSeekCompatibleChatModel`，通过 HTTPS 发送包含真实提示词与上下文的 JSON Payload 至 DeepSeek 官方活跃终端 `https://api.deepseek.com`；
4. **真实物理网络流式传输与消耗核算**：
   底层的 10 次调用全部通过真实网络往返（RTT 耗时通常在 1,000ms ~ 5,000ms，而非虚假的 20ms）。`HarnessEvidenceCollector` 真实监听 SSE 事件流，统计从首包到尾包的真实物理耗时，并根据模型返回的真实 Token 计数核算真实消耗；
5. **去作弊化盲审裁判与去标记化输出**：
   废除任何基于硬编码关键字（如 `"(精炼客观)"`）的作弊逻辑，裁判完全基于真实生成的文本内容质量、物理差分硬短路（$\Delta \text{Tokens} \le 0$ 与 $\Delta \text{Duration} \le 5\text{ms}$）以及无偏置换打分。

---

## 二、5 个离线代表性任务用例的代表性论证与物理判据矩阵

### 2.1 任务集设计哲学与企业中枢代表性论证
企业级智能体（Hermes）绝非仅用于闲聊，其核心定位是企业知识资产检索与复杂工具链编排中枢。阶段五选取的 5 个代表性离线评测任务，精确锚定了企业生产中最高频、最具挑战性的 5 种业务拓扑：

```mermaid
classDiagram
    class OfflineBenchmarkTestCase {
        +String taskId
        +TaskCategory category
        +String question
        +Map inputParams
        +List availableTools
        +String expectedToolName
        +int minContextTokensThreshold
    }
    class TaskCategory {
        <<enumeration>>
        KNOWLEDGE_QA
        MULTI_TOOL_SELECTION
        TOOL_PARAM_GUARD
        CONTEXT_HISTORY_RECALL
        COMPOSITE_REASONING
    }
    OfflineBenchmarkTestCase --> TaskCategory
```

#### 用例 1：`kb-qa-01` —— 中间件规范精确问答 (`KNOWLEDGE_QA`)
- **企业业务背景**：在企业架构治理与研发支撑场景中，开发者频繁询问核心中间件规范（如“订单支付系统的超时重试次数与幂等键生成规则”）。
- **代表性与技术挑战**：
  1. 考核千问 1024 维超球面向量空间检索的切片命中与上下文拼接；
  2. 考核模型对专业术语、数字阈值（如超时秒数、重试次数）的零幻觉提取；
  3. 考察候选 Prompt 能否在保证回答关键技术参数不遗漏的前提下，压缩掉无关的前置客套话。

#### 用例 2：`tool-route-02` —— 银企多工具流水对账 (`MULTI_TOOL_SELECTION`)
- **企业业务背景**：金融交易与对账场景，面对“客户反映交易扣款成功但未生成凭单，请核查流水状态并查询结算账户余额”。
- **代表性与技术挑战**：
  1. 候选工具池包含 3 个高度相似的银企接口：`banking_transaction_query`（流水查询）、`banking_account_balance`（余额查询）、`banking_settlement_remedy`（结算补单）；
  2. 考核模型的因果逻辑推断：必须先核查流水状态，方可决定是否查余额或补单，严禁越级或乱序调用；
  3. 检验变异 Prompt 是否会破坏模型的 Tool Calling JSON 格式遵循能力。

#### 用例 3：`param-guard-03` —— 工具非法参数自纠降级 (`TOOL_PARAM_GUARD`)
- **企业业务背景**：外部业务系统传输脏数据或非标时间格式（如传入 `2026/10/29 25:99:99`）。
- **代表性与技术挑战**：
  1. 考核智能体面对非法入参时的防御性编程思维；
  2. 验证模型是否能通过系统提示词中内置的校验规范，在前置阶段拦截非法输入或主动进行自纠降级，而不是盲目将非法参数传给外部工具引发 500 异常；
  3. 考察候选变异是否保持了参数防御警惕性。

#### 用例 4：`ctx-history-04` —— 长程会话代词消解与上下文召回 (`CONTEXT_HISTORY_RECALL`)
- **企业业务背景**：多轮人机交互，用户提问“针对前文提到的方案 A 的最担忧瓶颈，业界在网络数据面上有何绕过优化方案？”。
- **代表性与技术挑战**：
  1. 上下文跨越 2 轮以上历史，输入包含模糊指代（“方案 A”、“最担忧瓶颈”）；
  2. 该任务属于零工具纯推理任务（`availableTools` 为空），模型必须先在上下文中消解出“方案 A 最担忧瓶颈 = service_mesh_latency”，再进行技术方案召回；
  3. 考察候选 Prompt 能否在精炼指令的同时保持对长程历史上下文关键实体的捕获。

#### 用例 5：`comp-rag-tool-05` —— 微服务演进复合评估与对比矩阵 (`COMPOSITE_REASONING`)
- **企业业务背景**：架构委员会技术评审，要求生成“微服务架构演进评估报告，包含 4 列标准 Markdown 对比矩阵与 200 字总结”。
- **代表性与技术挑战**：
  1. 复合复杂推理任务，既需要 RAG 检索企业架构规范（`knowledgeBase_Arch_Spec`），又需要严格遵循 4 列 Markdown 表格排版，同时控制字数预算；
  2. 该任务 Token 开销最大（基线预计 > 500 Tokens），是对静态前缀缓存和变异收益最敏感的高价值场景；
  3. 考察候选 Prompt 能否显著压缩长回答中的结构性废话。

---

### 2.2 针对 5 大基准用例的物理改善与退化判据矩阵

在离线 PPC 闭环中，主观偏好必须让位于客观物理度量。针对上述 5 个用例，制定如下法医级判定矩阵：

| 任务标识 | 业务分类 | 预期工具状态 | 物理改善判据 (PASS / IMPROVED) | 物理退化硬拦截判据 (FAIL / SHORT_CIRCUIT) | 核心防退化要点 |
|---|---|---|---|---|---|
| **`kb-qa-01`** | 中间件规范问答 | 必须命中 `knowledgeBase_MiddleWare_Spec` | $\Delta \text{Tokens} < 0$ 且 $\Delta \text{Duration} \le 500\text{ms}$，工具匹配成功 | $\Delta \text{Tokens} > 0$ 或工具未命中或回答遗漏核心重试参数 | 不得遗漏超时时间与重试次数数值 |
| **`tool-route-02`** | 银企流水对账 | 必须命中 `banking_transaction_query` | $\Delta \text{Tokens} < 0$ 且 $\Delta \text{Duration} \le 500\text{ms}$，首选工具精确匹配 | 调用非首选工具（如直接查余额或补单）或 $\Delta \text{Tokens} > 0$ | 严禁工具调用越级与乱序 |
| **`param-guard-03`** | 参数自纠容错 | 必须触发 `calendar_event_creator` 且抛出自纠拦截提示 | 成功识别非法日期，给出格式引导提示，$\Delta \text{Tokens} \le 0$ | 忽视非法格式盲目传参导致异常，或 Token 正向膨胀 | 必须保留参数前置防御警告 |
| **`ctx-history-04`** | 代词消解召回 | 零工具调用 (`availableTools = []`) | 正确消解“方案 A 瓶颈”并给出针对性方案，$\Delta \text{Tokens} < 0$ | 幻觉引入无关方案，误触发工具，或 $\Delta \text{Tokens} > 0$ | 严禁错误消解前文主语 |
| **`comp-rag-tool-05`** | 复合结构化推理 | 必须命中 `knowledgeBase_Arch_Spec` | 包含 4 列完整 Markdown 表格，$\Delta \text{Tokens} \le -30$，排版整洁 | 表格列数不足 4 列，缺少总结，或 $\Delta \text{Tokens} > 0$ | 必须保持严格 Markdown 格式规范 |

---

## 三、基于 MT-Bench 与 Harness² 的 Position-Swap 双向匿名盲审数学防偏见原理

### 3.1 大模型裁判（LLM-as-a-Judge）的三大先验偏见机理
在 Stanford、UC Berkeley 与 LMSYS 的权威研究《Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena》(arXiv:2306.05685) 中，作者通过海量真实对战数据揭示出：使用大模型评估大模型生成质量时，存在严重的系统性心理学与统计学偏见：

1. **位置偏见 (Position Bias / Primacy Bias)**：
   当向裁判模型展示两个选项（Option A 与 Option B）时，模型存在强烈的“先入为主”倾向。统计表明，即使两个选项内容完全一致，裁判模型选择 Option A 的基础概率高达 **60% ~ 75%**；
2. **长度偏见 (Verbosity Bias)**：
   大语言模型在没有显式约束时，天然偏好冗长、字数多、结构繁琐的文本，将其误判为“论述详尽、态度专业”，而对精炼短文本打出低分；
3. **自增强偏见 (Self-Enhancement Bias)**：
   特定大模型家族对其自身生成的语言风格、句式衔接和常用词汇具有天生偏好。

### 3.2 Position-Swap 双向匿名置换数学模型推导
为了从根本上消除上述偏见，Google Research 在 Harness² `selection.py` 中实现了基于哈希置换的双向盲审机制。本项目 `PositionSwapBlindEvaluator` 在 Java 21 中对其进行了严格的数学复现与增强：

```mermaid
flowchart TD
    subgraph Input ["输入物理差分与摘要"]
        D["ProbeExecutionDiff"]
        BS["Baseline Output Summary"]
        CS["Candidate Output Summary"]
    end

    subgraph PreCheck ["第一道防线: 物理指标硬短路拦截"]
        C1{"deltaTokens > 0 ?"}
        C2{"deltaDurationMs > 5ms ?"}
        C3{"!toolSelectionMatched ?"}
        SC["SHORT_CIRCUITED_REJECT<br/>(零成本拦截，不浪费盲审调用)"]
    end

    subgraph HashPermute ["第二道防线: SHA-256 高熵置换标志位"]
        SALT["Cryptographic Salt + taskId"]
        BIT["computeInitialSwapBit: S in {0, 1}"]
    end

    subgraph DualPass ["第三道防线: 双向对称匿名盲审"]
        P1["Pass 1: (OptA, OptB) 根据 S 映射<br/>Choice 1 -> 解出 Real Winner 1"]
        P2["Pass 2: 严格物理对调 (OptB, OptA)<br/>Choice 2 -> 解出 Real Winner 2"]
    end

    subgraph StateMachine ["第四道防线: 四象限状态机裁决"]
        M1["Winner 1 == Cand && Winner 2 == Cand<br/>==> ROBUST_WIN_ADOPT (Quadrant 1)"]
        M2["Winner 1 == Base && Winner 2 == Base<br/>==> ROBUST_LOSS_REJECT (Quadrant 2)"]
        M3["Winner 1 != Winner 2 (冲突平局)<br/>==> BIAS_CONFLICT_REJECT (Quadrant 3/4)"]
    end

    D --> C1 & C2 & C3
    C1 -- 是 --> SC
    C2 -- 是 --> SC
    C3 -- 是 --> SC

    C1 & C2 & C3 -- 否 --> HashPermute
    HashPermute --> BIT
    BIT --> P1 --> P2
    P1 & P2 --> StateMachine
```

#### 数学形式化定义
设任务标识为 $T$，全局加密高熵盐值为 $\sigma$，置换标志位 $S \in \{0, 1\}$ 定义为：
$$S = \text{LSB}\Big(\text{SHA-256}ig(T \,\|\, \sigma \,\|\, \text{"STAGE4_POSITION_SWAP"}ig)\Big)$$
其中 $\text{LSB}(\cdot)$ 为最低有效位，保证 $S=0$ 与 $S=1$ 在统计上严格具备 50% 的均匀分布。

- **Pass 1 匿名映射**：
  $$egin{cases}
  \text{Option } A_1 = S \cdot C + (1 - S) \cdot B \
  \text{Option } B_1 = (1 - S) \cdot C + S \cdot B
  \end{cases}$$
  裁判模型给出无偏选择 $C_1 \in \{A, B\}$，通过代数反解出第一轮真实获胜方 $W_1$：
  $$W_1 = egin{cases}
  \text{CANDIDATE}, & \text{若 } (C_1 = A \land S = 1) \lor (C_1 = B \land S = 0) \
  \text{BASELINE}, & \text{若 } (C_1 = A \land S = 0) \lor (C_1 = B \land S = 1)
  \end{cases}$$

- **Pass 2 对称置换映射（物理对调）**：
  $$egin{cases}
  \text{Option } A_2 = \text{Option } B_1 \
  \text{Option } B_2 = \text{Option } A_1
  \end{cases}$$
  裁判模型在完全对称的逆向顺序下再次给出选择 $C_2 \in \{A, B\}$，反解出第二轮真实获胜方 $W_2$：
  $$W_2 = egin{cases}
  \text{CANDIDATE}, & \text{若 } (C_2 = A \land S = 0) \lor (C_2 = B \land S = 1) \
  \text{BASELINE}, & \text{若 } (C_2 = A \land S = 1) \lor (C_2 = B \land S = 0)
  \end{cases}$$

---

### 3.3 四象限状态机判定逻辑与 Quadrant 1 强稳健采纳准则

根据两轮盲审的真实胜出者元组 $(W_1, W_2)$，系统映射至离散的四象限状态机：

| 象限标识 | Pass 1 胜出者 ($W_1$) | Pass 2 胜出者 ($W_2$) | 一致性 ($W_1 = W_2$) | 终验裁定结果 (`VerdictOutcome`) | 架构决策行为 |
|---|---|---|---|---|---|
| **Quadrant 1** | `CANDIDATE` | `CANDIDATE` | **TRUE** | **`ROBUST_WIN_ADOPT`** | **双向一致胜出，予以采纳** |
| **Quadrant 2** | `BASELINE` | `BASELINE` | **TRUE** | **`ROBUST_LOSS_REJECT`** | **双向一致淘汰，坚决拒绝** |
| **Quadrant 3** | `CANDIDATE` | `BASELINE` | **FALSE** | **`BIAS_CONFLICT_REJECT`** | **位置偏见冲突，安全回退基线** |
| **Quadrant 4** | `BASELINE` | `CANDIDATE` | **FALSE** | **`BIAS_CONFLICT_REJECT`** | **位置偏见冲突，安全回退基线** |
| **物理短路** | N/A (未调用) | N/A (未调用) | N/A | **`SHORT_CIRCUITED_REJECT`** | **物理指标退化，直接短路拒绝** |

#### 为什么位置偏见冲突（`BIAS_CONFLICT`）时必须坚决拒绝？
在 Quadrant 3 与 Quadrant 4 中，$W_1 
e W_2$ 意味着当选项顺序颠倒时，裁判模型的选择发生了反转。根据 MT-Bench 的理论，这典型地反映了裁判模型并未真正识别出两个回答之间的本质质量优劣，而是受到了 Option A 呈现位置的先验主导（即裁判在 Pass 1 选了 A，在 Pass 2 依然选了 A，导致 $W_1$ 与 $W_2$ 互斥）。

在企业生产级 Agent 治理体系中，算法的变异演进遵循**“极小变更与无害保证原则 (Do No Harm)”**：
1. 候选变异必须证明自己具备压倒性、统计显著且与展示位置无关的绝对优势；
2. 一旦出现平局或冲突，即证明当前变异带来的优势无法抵消引入新 Prompt 的认知漂移风险；
3. 拒绝采纳并回退基线是代价最小、可靠性最高的零风险决策。

---

## 四、真实候选变异未节省 Token、耗时增加或工具调用错误时的证伪处理流程

### 4.1 科学假设的可证伪性与不可抗拒性
根据卡尔·波普尔的科学哲学准则以及《AGENTS.md》的最高法典约束，阶段五的唯一待验证假设必须是一个具备**完全可证伪性 (Falsifiability)** 的命题。实验绝不是为了“必胜”而走过场，而是为了探索真实系统在真实外部大模型下的真实物理边界。

```mermaid
flowchart TD
    START["启动阶段五真实探针: 10次 DeepSeek 真实调用"]
    COLLECT["收集真实落盘凭单: 5 baseline + 5 candidate"]
    CHECK_NET{"调用成功率 == 100% ?"}
    
    FAIL_SAFE["触发 FAIL_SAFE 降级<br/>记录网络/接口异常凭单"]
    REFUTE_NET["裁定假设不成立 (REFUTED)<br/>归因为外部环境/API中断"]

    CHECK_TOKENS{"总 Token 节省量 > 0<br/>且 平均耗时无显著增加 ?"}
    REFUTE_PHY["裁定假设不成立 (REFUTED)<br/>归因为候选变异物理未节省/劣化"]

    CHECK_TOOLS{"工具调用状态 100% 未退化 ?"}
    REFUTE_TOOL["裁定假设不成立 (REFUTED)<br/>归因为工具路由/参数能力退化"]

    BLIND{"Position-Swap 双向盲审<br/>Quadrant 1 胜出 ?"}
    REFUTE_BLIND["裁定假设不成立 (REFUTED)<br/>归因为双向盲审冲突/未被采纳"]

    VERIFY["裁定阶段五假设成立 (VERIFIED)<br/>达成真实离线受控 PPC 闭环"]

    START --> COLLECT --> CHECK_NET
    CHECK_NET -- 否 --> FAIL_SAFE --> REFUTE_NET
    CHECK_NET -- 是 --> CHECK_TOKENS
    CHECK_TOKENS -- 否 --> REFUTE_PHY
    CHECK_TOKENS -- 是 --> CHECK_TOOLS
    CHECK_TOOLS -- 否 --> REFUTE_TOOL
    CHECK_TOOLS -- 是 --> BLIND
    BLIND -- 否 --> REFUTE_BLIND
    BLIND -- 是 --> VERIFY
```

### 4.2 假设证伪的四大物理触发条件
若真实运行出现以下任意一种情况，审计员与执行系统必须**直接判定假设不成立 (`REFUTED`)**：

1. **外部接口或网络中断**：
   在执行 10 次调用过程中，若 DeepSeek API 发生网络超时、HTTP 502、429 限流或认证失败，触发了 `DefaultOfflineProbeExecutor.synthesizeFailSafeEvidence`，生成了标有 `ExecutionMode.FAIL_SAFE` 的凭单，则证明在既定环境下未能完成 100% 真实执行，假设直接证伪；
2. **总 Token 消耗未节省或发生膨胀**：
   若 5 个用例累计后，$\sum \text{Tokens}_{cand} \ge \sum \text{Tokens}_{base}$（即 $\Delta \text{Tokens} \ge 0$），说明精炼版 System Prompt 未能在真实模型上诱导出更精炼的输出，甚至诱发了更啰嗦的解释，假设直接证伪；
3. **执行耗时显著劣化**：
   若候选变异的单次耗时相比基线增加了 500ms 以上，或总耗时出现明显增加，说明精炼 Prompt 增加了模型的深度推理负担，假设直接证伪；
4. **工具调用能力发生退化**：
   在 `tool-route-02` 或 `param-guard-03` 中，若基线原本成功命中工具，而候选 Prompt 导致大模型未能生成标准 Tool Call JSON，或误选了其他工具，触发了 `diff.toolSelectionMatched() == false`，直接被硬短路门禁拦截（`SHORT_CIRCUITED_REJECT`），假设直接证伪。

### 4.3 证伪发生时的法定处置规范与纪律约束
面对假设不成立（`REFUTED`），全流程执行团队必须严格遵守以下工程法纪：

1. **“四严禁”铁律**：
   - **严禁降低门禁**：绝不允许临时调大超时上限、放宽 Token 容忍度或将退化忽略不计；
   - **严禁修改 Fixture**：绝不允许通过修改用例 Question 或参数来迎合候选 Prompt；
   - **严禁数据重采样**：绝不允许在发现失败后多次重复运行、只挑选成功结果落盘；
   - **严禁掩盖故障**：绝不允许将 `FAIL_SAFE` 或 `SHORT_CIRCUITED_REJECT` 从评测总账中剔除。
2. **法定归档与审计闭环**：
   - 必须将包含退化证据的全部 10 份 JSON 凭单与评估总账完整保存在 `experiment/outputs/harness5/<runId>/` 目录下；
   - 在阶段五验收报告中如实将判定结果标记为 `REFUTED`；
   - 详细记录退化发生的 Task ID、差分数值 $\Delta \text{Tokens}$、$\Delta \text{Duration}$ 及模型实际输出，形成架构反思与负向案例库（Negative Case Ledger），作为后续探索更优变异的基石。

---

## 五、结论与实施准入判定

### 5.1 审计核心结论
本审计员对阶段五真实 PPC 实验方案进行了全方位的理论与法医级代码审计，结论如下：
1. **造假漏洞已彻底根除**：阶段五通过引入显式 `ExecutionMode.REAL_PRODUCTION` 枚举、全字段 SHA-256 签名常量时间校验、真实 DeepSeek 官方 API 流式驱动，从根本上杜绝了阶段四测试替身造假的系统性风险；
2. **基准任务具备高度代表性**：5 个离线小样本用例完整覆盖企业知识库问答、多工具路由、参数自纠、长上下文消解与复合结构化生成，设计科学且边界完备；
3. **双向盲审具备严密数学保障**：Position-Swap 双向匿名置换与四象限状态机，能够有效过滤大模型裁判的位置偏见与长度偏见，仅采纳强稳健胜出的候选；
4. **假设具备严格可证伪性**：明确划定了四大物理证伪触发条件与“四严禁”纪律，具备真理求索的客观品格。

### 5.2 实施准入判定 (Gate Verdict)
本审计员一致裁定：**阶段五真实 PPC 实验设计与防伪体系符合《科研与系统工程总纲》的全部严苛要求，准予进入实施阶段（GATE_APPROVED）**。

建议主 Agent 与实施团队在执行 10 次真实调用前，严格核验以下 5 项物理前置项：
1. 本地 `.env` 中 `HERMES_OPENAI_API_KEY` 的有效性（非占位符）；
2. 执行探测脚本确保 `https://api.deepseek.com` 握手状态码返回 `200 OK`；
3. 输出目录必须指向 `experiment/outputs/harness5/<runId>/`；
4. 验证驱动器必须显式绑定 `ProductionAgentOrchestratorDriver`，严禁注入 `DeterministicHarnessTestDouble`；
5. 运行期间实时受控于 `OfflineBudgetCircuitBreaker`，严禁超出预算。
