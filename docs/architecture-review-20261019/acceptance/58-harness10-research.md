> **SUPERSEDED / DO NOT USE FOR IMPLEMENTATION**：本文 Stage 9 HISTORY/COMPOSITE 的 `INVALID_FIXTURE` 与“非 Agent 缺陷”表述仅为早期假设，未获反事实验证，不构成已确认归因。本文中的业务 Domain 映射也未核验；以 `59-harness10-taskbook.md` Revision 2 为准：失败记录为 `OBSERVED` + `failureObserved=true` + `attributionStatus=UNRESOLVED`，Stage 9 回放统一进入 `UNSCOPED_REPLAY` 且不参与正向检索。

# Hermes Harness² 阶段十（Stage 10）：定向学术研究报告

- **文档编号**：`58-harness10-research.md`
- **所属阶段**：Stage 10 — 阶段 2（定向研究）
- **伴生账本**：[`58-harness10-research.ledger.json`](file:///Users/achilles/Documents/许子祺/Agent/docs/architecture-review-20261019/acceptance/58-harness10-research.ledger.json)（5 个 `VERIFIED` 来源）
- **核心论题**：非参数化智能体跨任务经验积累、Per-Domain 形式化隔离、因果抽象表征与经验生命周期淘汰

---

## 一、研究背景与学术理论动机

在 Stage 9 完成真实 DeepSeek API 的受控 Propose-Probe-Compose (PPC) 实验后，我们获得了珍贵且诚实的真实负向实证：
- 过度精简提示词（`candidate-a`）导致模型输出端代偿性展开，Token 与成本显著增加；
- 收紧循环步数（`candidate-b`）降低了部分 Token，但未能提升质量；
- 同时发现了长思维链耗尽输出 Token（`ctx-history-04`）与工具参数契约未闭合（`comp-rag-tool-05`）两处关键工程夹具失效（`INVALID_FIXTURE`）。

若将这些探索结论随实验结束而抛弃，后续的每次 PPC 实验都将面临“从零试错、重复踩坑、高昂成本”的困境；反之，若盲目将所有历史教训无差别塞进生产 SystemPrompt，不仅会导致输入 Token 线性膨胀击穿预算，还会引发跨业务领域负迁移（Negative Transfer）与提示词过拟合。

因此，Stage 10 的核心学术使命是吸收 **Harness²**、**CLIN**、**ExpeL**、**Reflexion** 与 **DSPy** 的前沿成果，构建一个**非参数化、物理域隔离、具备因果结构与可信淘汰机制的 Per-Domain Experience Library**。

---

## 二、关键文献与开源源码深度解构

### 2.1 Harness² 官方开源实现 (`harness2/core/library.py`)
- **按构造隔离（Retrieval by Construction）**：
  Harness² 明确摒弃了在全局大平铺空间中做带噪检索的做法，而是通过 `domain_dir(domain)` 为每个领域创建完全独立的物理目录与 `INDEX.md`。经验检索天然锁定在同一个领域内，物理层面杜绝了跨领域干扰。
- **经验条目标准四元组**：
  Harness² 规定一条合法经验必须包含：
  - `cue`（检索触发线索）；
  - `lesson`（可迁移的结构化教训）；
  - `evidence`（支持该教训的物理轨迹或数据）；
  - `confidence`（置信度）。
- **零泄漏断言**：
  写入经验前强制调用 `context.assert_tree_has_no_grader_verdicts`，严禁任何评分器标记或黄金真值流入经验库。
- **单向只读给 Proposer**：
  经验库仅作为改进者（Improver/Proposer）生成候选时的参考上下文，绝对不注入运行时执行环境。

### 2.2 CLIN (`arXiv:2310.10134`, ICLR 2024)：因果抽象与不确定度分级
- **因果结构化表征**：
  CLIN 的实证消融表明，非结构化自由自然语言建议会带来 6.2 分的性能骤降。CLIN 强制将经验表示为明确的因果关系：
  - 正向必要因果：`X is NECESSARY to Y`；
  - 负向阻碍/无用因果：`X DOES NOT CONTRIBUTE to Y`。
- **置信度语言学分级**：
  CLIN 将经验标记为低不确定度（`should`）与高不确定度（`may`），随交互频次与任务适应度动态调整。这为 Stage 10 在 `ExperienceEntry` 中引入连续型置信度标量 $\gamma \in [0.0, 1.0]$ 提供了直接理论依据。

### 2.3 ExpeL (`arXiv:2308.10144`, AAAI 2024)：跨任务经验演化四算子
- **跨任务积累 vs 单任务自反思**：
  ExpeL 证明跨任务经验池显著优于单任务内部自反思（Reflexion），能够实现持续正向增益。
- **经验演化代数四算子**：
  - `ADD`：从新实验中提取并登记新经验；
  - `UPVOTE`：经验在后续任务中被成功验证，增加置信度权重；
  - `DOWNVOTE`：经验被后续证据反驳或失效，降低置信度权重；
  - `EDIT`：根据新证据修正教训范围。
  当置信度低于淘汰门槛时，必须将条目淘汰，防止过时记忆阻碍有效探索。

### 2.4 DSPy (`arXiv:2310.03714`, ICLR 2024)：编译优化与运行时推理隔离
- **声明式解耦原则**：
  DSPy 核心原则指出，外围提示词与配置的搜索与编译（Compilation）必须与底层模型的在线推理严格隔离。经验是提供给编译优化器（Proposer）的元先验（Meta-Prior），绝不能直接拼接为在线运行时系统提示词。

---

## 三、方案选型与适用性裁决

| 比较维度 | 方案 0：简单追加日志文件 | 方案 1：全局向量知识库检索（RAG-based Experience） | **方案 2（推荐采纳）：Per-Domain 物理隔离不可变经验库** |
| :--- | :--- | :--- | :--- |
| **理论完备性** | 低（无结构、无因果） | 中（仅依靠语义相似度，易跨域混淆） | **高（基于 Harness² + CLIN + ExpeL 完备元组）** |
| **隔离性** | 无 | 差（向量召回极易发生跨任务/跨业务域污染） | **绝对物理隔离（按 Domain Slug 物理分目录）** |
| **安全性与防泄漏** | 无检验 | 易泄漏 qrel 与测试真值 | **强（写入前强制 `assertNoQrelOrGraderLeakage` 拦截）** |
| **生产无侵入性** | 差 | 易导致运行时 SystemPrompt 恶性膨胀 | **绝对零污染（仅对离线 Proposer 暴露，运行时零感知）** |
| **生命周期管理** | 无法淘汰 | 向量覆盖易产生不可逆硬删除 | **不可变版本追加 + 软淘汰（Soft Retirement）审计保留** |
| **依赖变化** | 无 | 需引入外部向量数据库与复杂检索服务 | **零新增依赖（纯 Java 21 标准库 + 既有核心包）** |

**裁决结论**：全面采纳**方案 2**，作为 Stage 10 经验库的核心架构设计。
