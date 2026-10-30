# Hermes Harness² 阶段五前沿文献与工业实践深度调研报告

> **报告版本**：5.0.0-STAGE5-RESEARCH  
> **关联分支**：`feat/hermes-harness-stage4` -> `feat/hermes-harness-stage5`  
> **调研负责人**：Hermes 阶段五架构研究与实施团队  
> **对齐基线**：Java 21 隔离运行环境、DeepSeek 官方 API 规约、千问 1024 维超球面向量空间  
> **完成日期**：2026-10-30  

---

## 一、调研背景与阶段五唯一待验证假设

在阶段四构建的离线探针执行器契约骨架之上，阶段五的核心攻坚点为：**从测试替身合成走向真实生产轨道执行**。通过使用真实的 `ProductionAgentOrchestratorDriver` 驱动底层 DeepSeek 官方 API，在 5 大基准任务集上运行 Baseline 与 Candidate（共 10 次真实调用），获取具备真实不可篡改密码学凭据的 `REAL_PRODUCTION` 证据，并以真实的物理差分（Token、耗时、工具匹配）和无偏盲审裁决验证假设。

调研聚焦于三个核心维度：
1. **真实探针调度与不可篡改证据持久化**（Harness²）；
2. **大模型裁判位置偏见消除与双向匿名盲审**（MT-Bench / LLM-as-a-Judge）；
3. **基于执行指标的编译期与自适应优化**（DSPy、OPRO、TextGrad、Reflexion）。

---

## 二、调研来源与 Research Ledger 清单

本轮研究严格遵循《科研与系统工程总纲》的 Reading-First 取证前置铁律，全量 6 项来源均实际完成全文/核心章节研读，真实标注为 `VERIFIED`，详见结构化资产 `39-harness5-research.ledger.json`：

1. **`SRC-STAGE5-001` (Official Code)**: `google-research/harness2`（Commit `1cfe254e`, Apache-2.0）  
   - **核心贡献**：提出了通过真实探针运行（Probe Rollout）驱动候选变异评估的递归闭环机制（PPC），并通过匿名双向对调（Position-Swap Dual Pass）消除大模型评审偏见，结合结构化证据 Ledger 进行可复现审查。
2. **`SRC-STAGE5-002` (Paper)**: `Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena` (Lianmin Zheng et al., NeurIPS 2023, arXiv:2306.05685)  
   - **核心贡献**：系统揭示了大模型作为裁判时普遍存在的位置偏见（Position Bias，先入为主倾斜达 60%+）与长度偏见（Verbosity Bias）；实证了通过对调候选顺序进行两次独立盲审（Position-Swap），仅当两次判定一致时才采纳的偏见消除机制。
3. **`SRC-STAGE5-003` (Paper)**: `DSPy: Compiling Declarative Language Model Calls into State-of-the-Art Pipelines` (Omar Khattab et al., ICLR 2024, arXiv:2310.03714)  
   - **核心贡献**：证明了将模型调用抽象为计算图并通过度量（Metrics）离线编译优化 Prompt，比手工微调具有更高鲁棒性与确定性。
4. **`SRC-STAGE5-004` (Paper)**: `Large Language Models as Optimizers (OPRO)` (Chengrun Yang et al., ICLR 2024, arXiv:2309.03409)  
   - **核心贡献**：阐明了利用历史优化轨迹（变异文本与量化得分）作为元提示词上下文，无需梯度即可生成性能更优的候选指令。
5. **`SRC-STAGE5-005` (Paper)**: `TextGrad: Automatic "Differentiation" via Text` (Mert Yuksekgonul et al., Nature Communications / NeurIPS 2024, arXiv:2406.07496)  
   - **核心贡献**：提出自然语言反向传播“文本梯度”（Textual Feedback & Critiques）优化复合 AI 系统各节点的机制。
6. **`SRC-STAGE5-006` (Paper)**: `Reflexion: Language Agents with Verbal Reinforcement Learning` (Noah Shinn et al., NeurIPS 2023, arXiv:2303.11366)  
   - **核心贡献**：证明在执行失败后由独立 Evaluator 给出语言反馈并沉淀为情景记忆，可单调提升任务成功率；强调离线反思与在线执行的安全隔离。

---

## 三、可迁移与不可迁移结论分析

### 3.1 可直接迁移结论
1. **真实探针执行与原子存证**：Harness² 的真实 Probe Rollout 思想在 Java 中由 `DefaultOfflineProbeExecutor` 落地，通过 `ATOMIC_MOVE` 原子落盘与 SHA-256 常量时间验签，确保证据的物理真实性与不可篡改性；
2. **双向匿名位置置换盲审**：吸收 MT-Bench / LLM-as-a-Judge 与 Harness² `selection.py` 的 Position-Swap 理论，对基线与候选进行 Pass 1 与 Pass 2 双向匿名对调评测，根除大模型先验位置偏见；
3. **硬指标物理短路门禁**：在盲审前增加物理指标拦截，若候选变异导致 Token 膨胀、耗时超标或工具调用不匹配，直接触发 `SHORT_CIRCUITED_REJECT`，绝不进入盲审浪费预算。

### 3.2 必须改造与坚决拒绝的结论
1. **拒绝 Python 动态热更文件系统**：Harness² 原生通过动态覆写本地磁盘 `.py` 文件实现变异热更，这在企业级 Java 容器中存在严重并发安全隐患。本项目坚决改为**内存级局部请求凭单覆盖（`ScopedVoucherLease`）**，执行完毕自动原子注销；
2. **拒绝盲目堆叠 Few-Shot 样本**：DSPy 的 Teleprompter 倾向于自动插入多条 Few-Shot 样本，这会显著破坏大模型服务端的前缀缓存（Prefix Caching）并导致 Token 成本翻倍。Hermes 变异算子严格限制在静态前缀与指示词优化（Zero Few-Shot）；
3. **拒绝在线自我反思导致延迟劣化**：Reflexion 适合离线场景，若在线实时引入自我反思将导致用户请求延迟从百毫秒激增至数秒。Hermes 仅在离线小样本 PPC 闭环中调度反思与变异。

---

## 四、阶段五真实生产轨道落地架构图

```mermaid
flowchart TD
    subgraph OfflineTestRunner ["离线受控实验套件 (Offline Test Suite)"]
        TC[5大基准用例]
        V_BASE[Baseline 凭单]
        V_CAND[Candidate 变异凭单]
    end

    subgraph ProbeExecution ["真实探针执行层 (Real-world Probe Execution)"]
        EX[DefaultOfflineProbeExecutor]
        DRV[ProductionAgentOrchestratorDriver]
        LEASE[ScopedVoucherLease 租约]
        ORCH[真实 AgentOrchestrator]
    end

    subgraph DeepSeekCloud ["外部官方服务 (DeepSeek API)"]
        DS[https://api.deepseek.com]
        FLASH[deepseek-flash 活跃模型]
    end

    subgraph EvidenceAndEval ["证据落盘与双向盲审 (Persistence & Eval)"]
        COL[HarnessEvidenceCollector]
        DISK[物理 JSON 资产 (REAL_PRODUCTION)]
        CIRCUIT[累加熔断器 BudgetCircuitBreaker]
        SWAP[PositionSwapBlindEvaluator]
    end

    TC --> EX
    V_BASE --> EX
    V_CAND --> EX
    EX --> LEASE
    LEASE -->|注册局部凭单| ORCH
    EX --> DRV
    DRV -->|真实 chat 流调用| ORCH
    ORCH -->|官方标准协议| DS
    DS --> FLASH
    FLASH -->|真实流式响应 / reasoning_content| ORCH
    ORCH -->|收集物理指标| COL
    COL -->|标记 REAL_PRODUCTION| DISK
    DISK --> CIRCUIT
    DISK --> SWAP
    SWAP -->|双向一致采纳/短路拒绝| REPORT[终验评估结论]
```

---

## 五、结论与下一阶段输入

本调研报告明确了阶段五的核心架构准则：
- 真实调用是验证假设的唯一不可替代凭据；
- 双向盲审必须与物理指标硬短路相结合；
- 成果将直接输入至阶段五 3 个独立 Agent 物理派发与辩论对齐。
