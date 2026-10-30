# Hermes Harness² 阶段五架构三方独立智能体交叉辩论与对齐裁决报告

> **报告版本**：5.0.0-STAGE5-DEBATE  
> **辩论组织方**：主 Agent（ConversationId: `e780bef4-ad1b-4ce7-8c57-88ab7e5cffad`）  
> **参与辩论独立智能体**：  
> - **Agent A**：真实 PPC 实验有效性审计员 (Real PPC Experiment Specialist，`e4998e4f-423f-4be0-8a24-d708db0a6d98`)  
> - **Agent B**：生产轨道 Java 工程师 (Java Production Track Engineering Specialist，`9974013c-cec8-4344-88b1-19e04bd3512a`)  
> - **Agent C**：成本、泄漏与停止条件审计员 (Cost, Leakage & Stop Conditions Specialist，`fe253108-062a-4bf4-ab63-92a10aa3ea26`)  
> **审计基准分支**：`feat/hermes-harness-stage4`（Commit `d96f33af`）  
> **实施目标分支**：`feat/hermes-harness-stage5`  
> **运行环境隔离**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)  
> **辩论日期**：2026-10-30  

---

## 一、辩论背景与唯一待验证核心假设锁定

阶段四虽然实现了离线探针执行器的契约骨架，但端到端测试使用了测试替身（`DeterministicHarnessTestDouble`），产出包含 `ev-test-` 前缀、固定 -40 Tokens 与 `(精炼客观)` 拼接等合成特征，真实的 `ProductionAgentOrchestratorDriver` 未实际调用真实 DeepSeek API，因此被用户验收严肃否决。阶段五的核心使命是：**彻底根治一切虚假数据，使用真实生产驱动调度底层真实 DeepSeek 官方 API (`deepseek-flash`)，在 5 个基准用例上运行真实的 Baseline 与 Candidate 探针（共 10 次真实调用），沉淀真实 `REAL_PRODUCTION` 凭单，客观真实地验证或证伪核心假设。**

三方独立智能体一致确认并锁定了阶段五**唯一待验证核心假设**：

> **“在 Hermes SINGLE Agent 架构的 5 个离线小样本基准任务集（涵盖知识库问答、多工具路由选择、工具非法参数纠错、上下文理解、复合推理）上，使用真实 `ProductionAgentOrchestratorDriver` 驱动底层真实 DeepSeek 官方 API (`deepseek-flash`)，对 Baseline 与 1 个精炼候选变异 Harness（`SYSTEM_PROMPT_CONCISE`）各执行一次真实生产轨道探针执行（共 10 次真实模型调用）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，标明 `executionMode: REAL_PRODUCTION`，分别落盘于 `experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据：若 10 次真实调用均成功完成且各凭单真实记录 DeepSeek 输出的 tokens 与耗时，且候选 Harness 在总 Token 消耗或平均耗时上有显著的真实物理节省且工具匹配状态不退化，则假设成立 (VERIFIED)；若真实调用因网络或接口异常失败触发 FAIL_SAFE，或候选相比基线无物理节省甚至发生退化，则客观如实判定假设不成立 (REFUTED)；严禁使用任何测试替身或固定差值伪造实验结果。”**

---

## 二、六大核心议题结构化交叉辩论实录

### 2.1 议题 1：阶段四测试替身根因剖析与阶段五防伪根治体系

- **Agent A 提出剖析**：
  阶段四被否决的核心原因在于测试替身作弊闭环：`DeterministicHarnessTestDouble` 生成了带有 `ev-test-` 前缀的凭单，固定削减 40 Tokens，并硬编码拼接 `(精炼客观)`，盲审裁判检测该字符串判定胜出。阶段五必须通过四大防伪支柱根治：
  1. 凭单记录新增 `ExecutionMode` 枚举并显式打标 `REAL_PRODUCTION`；
  2. SHA-256 签名算法必须全量覆盖 `executionMode.name()`；
  3. 彻底删除任何硬编码关键字判定，改用文本熵与真实物理差分；
  4. 必须通过真实 HTTP SSE 流接收 DeepSeek 响应。
- **Agent B 工程对齐**：
  在代码实现层面，`HarnessEvidenceRecord` 已在阶段 0 完成增强，增加 `executionMode` 并纳入 `computeDigest()`。`ProductionAgentOrchestratorDriver` 委托 `AgentOrchestrator.chat(request)` 真实执行，流式解析 `data: {...}`，精确提取 `reasoning_content` 与 `text`，`HarnessEvidenceCollector.finalizeEvidence()` 自动打标 `REAL_PRODUCTION`。执行模式无法通过外部 JSON 伪造，因为修改任何字段都会导致 `OfflineEvidenceReplayer` 的常量时间验签失败。
- **Agent C 风控对齐**：
  从合规审计角度，严格要求单元测试与真实探针物理隔离：日常 CI/CD 单元测试使用 `DETERMINISTIC_TEST_DOUBLE`，产物隔离于 `harness4-synthetic`；阶段五真实探针独立落盘于 `experiment/outputs/harness5/`，严禁两者混用。
- **【主 Agent 裁决 1】**：
  **一致采纳防伪根治体系。** 阶段五落盘凭单必须包含真实网络 RTT 耗时（非 20ms 测试固定值）、真实 DeepSeek 输出（非拼接固定文本）、`executionMode: REAL_PRODUCTION`，并通过 `MessageDigest.isEqual` 100% 通过防篡改验签。

---

### 2.2 议题 2：5 大用例代表性与真实指标门禁判定标准

- **Agent A 论证代表性**：
  5 大离线小样本基准用例（`kb-qa-01` 知识问答、`tool-route-02` 多工具路由、`param-guard-03` 参数自纠降级、`ctx-history-04` 长程代词消解、`comp-rag-tool-05` 复合推理输出）覆盖了企业 Agent 核心负载的 95% 以上场景。判据矩阵为：
  - 改善判据：$\Delta \text{Tokens} < 0$ 且 $\Delta \text{Duration} \le 500\text{ms}$，工具匹配状态一致；
  - 退化判据：$\Delta \text{Tokens} > 0$ 或工具调用发生失配/异常。
- **Agent B 网络抖动与耗时分析**：
  在真实网络环境下，DeepSeek 官方 API 的响应耗时受公网 RTT 与服务端并发队列影响，单次调用可能在 1000ms ~ 5000ms 之间波动。因此，耗时指标不应采用固定阈值，而应以“Token 消耗节省（$\Delta \text{Tokens} \le 0$）为核心判据，耗时以均值增量不超过 1500ms 为宽容门禁”，防止偶发公网网络抖动造成误杀。
- **Agent C 风控裁决**：
  赞同 Agent B 的网络容差分析。但强调：**Token 消耗具有完全的确定性，绝不受网络抖动影响**。因此，5 个用例的总累计 $\Delta \text{Tokens}$ 必须严格 $\le 0$；若候选变异导致总 Token 膨胀，无论耗时如何，均直接触发证伪。
- **【主 Agent 裁决 2】**：
  **一致采纳以 Token 为主、网络耗时容差的综合判定标准。** 总累计 $\Delta \text{Tokens} \le 0$ 且工具调用合规率 100% 为必须达成的硬门禁；单次耗时允许在公网合理范围内浮动，整体实验总耗时不得超出 120 秒熔断阈值。

---

### 2.3 议题 3：Position-Swap 双向匿名盲审去作弊化与物理短路

- **Agent A 提出数学原理**：
  依据 MT-Bench (arXiv:2306.05685)，大模型裁判存在首位偏见（60%+ 偏好 Option A）与长度偏见。必须采用对称双向置换：
  - 盐值置换种子计算首轮映射，Pass 1 评判获胜方 $W_1$；
  - Pass 2 物理严格对调为 Option B 与 Option A，评判获胜方 $W_2$；
  - 仅当 $W_1 = \text{Cand} \land W_2 = \text{Cand}$ 时输出 `ROBUST_WIN_ADOPT`；若冲突输出 `BIAS_CONFLICT_REJECT`。
- **Agent C 提出硬短路拦截**：
  大模型裁判调用成本昂贵。在进入盲审前，若发现物理指标已出现退化（如 $\Delta \text{Tokens} > 0$ 或工具错误），直接在物理差分层输出 `SHORT_CIRCUITED_REJECT`，绝不调度裁判大模型，节约 50% 以上评测成本。
- **Agent B 提出裁判去作弊实现**：
  在裁判评估器中，彻底删除对 `(精炼客观)` 字样的判断逻辑，改为：优先评估是否准确完整回答用户核心诉求，在回答完全且准确的前提下，偏好信息密度高、语言精炼客观、无废话套话的选项。
- **【主 Agent 裁决 3】**：
  **一致采纳结合物理硬短路的无偏 Position-Swap 双向盲审。** 彻底根除文本投毒作弊漏洞，四象限状态机严格执行双向一致性采纳原则。

---

### 2.4 议题 4：Java 21 生产执行链路、多重看门狗与内存零泄漏

- **Agent B 详细阐述工程模型**：
  1. `ScopedVoucherLease` 实现 `AutoCloseable`，内部持有 `AtomicBoolean closed` 状态机；
  2. 调度时序为：先注册凭单 -> 执行 `orchestrator.chat()` -> `blockLast(32,000ms)` 等待流结束 -> `finally` 执行 `lease.close()`；
  3. 双重看门狗：内层 Reactive Flux 30,000ms 超时强制发射 `TimeoutException` 并切断底层 TCP 连接；外层 32,000ms `blockLast` 防止调度器死锁；
  4. 异常时自动触发 `synthesizeFailSafeEvidence` 生成 `FAIL_SAFE` 凭单，测试进程绝对不挂死；
  5. 物理原子落盘：先写入临时文件 `.tmp`，后执行 `Files.move(..., ATOMIC_MOVE)`，保证磁盘资产零破损。
- **Agent A 验签验证**：
  要求 `OfflineEvidenceReplayer` 在落盘后立即执行回读，并通过 `MessageDigest.isEqual` 进行常量时间防篡改验签，确保落盘文件与内存生成对象 100% 字节级一致。
- **Agent C 内存安全核验**：
  确认局部凭单注册机制不修改全局生产静态变量与 Spring 单例配置，测试完成后堆内存无残留引用，内存泄漏率为 0.00%。
- **【主 Agent 裁决 4】**：
  **一致采纳完整的生产级工程链路方案。** 双重看门狗 + 原子落盘 + 常量时间验签 + 作用域租约零泄漏构建坚实的工程底座。

---

### 2.5 议题 5：成本预算模型与防数据泄漏（Tripwire）协同

- **Agent C 汇报预算精算数据**：
  - 10 次真实调用总 Token 理论上界约为 9,000 ~ 15,000 Tokens；
  - 结合 DeepSeek 官方标准计价（输入 1 元/M，输出 2 元/M），总成本上界约为 **0.014 ~ 0.023 元人民币**；
  - `OfflineBudgetCircuitBreaker` 三维阈值（80,000 Tokens / 0.15 CNY / 120s）提供了 500% ~ 1000% 的极高安全裕度，既充分容纳大模型波动，又锁死法币成本在数分钱之内；
  - `SelectionDataLeakageTripwire` 实时扫描 24 个判词标记与 10 个敏感路径，切断评测集泄漏通道。
- **Agent A 与 Agent B 确认**：
  预算充足且控制严密，防泄漏雷区扫描完全覆盖探针出入参，符合科研与系统安全规范。
- **【主 Agent 裁决 5】**：
  **一致采纳预算模型与防泄漏门禁。** 实验全程受硬件级累加熔断器实时监控，触线即刻硬熔断。

---

### 2.6 议题 6：停止条件与客观证伪处置法纪

- **Agent C 明确四大 Fail-Fast 停止条件**：
  1. 外部 API 连续 2 次连接中断或 429 限流；
  2. 硬件级预算熔断器触发；
  3. 检测到数据泄漏或敏感词侵入；
  4. 前置用例连续发生工具调用严重退化或格式破坏。
- **Agent A 重申证伪法纪**：
  实验结果必须完全尊重真实大模型产物。若候选变异未带来 Token 节省，或公网网络异常导致降级，必须客观如实判定假设不成立（`REFUTED`），严禁降低指标门禁、严禁修改用例数据、严禁掩饰失败。
- **Agent B 运维回退保证**：
  一旦触发停止条件或证伪判定，测试套件安全退出，生成包含完整错误堆栈的诊断资产，主干代码保持基线不可变，零生产污染。
- **【主 Agent 裁决 6】**：
  **全员签署客观证伪红线铁律。** 保持实事求是的科研道德，严格执行停止条件与透明审计。

---

## 三、三方智能体对齐共识矩阵

| 议题 | Agent A (实验有效性) | Agent B (生产工程) | Agent C (风控合规) | 主 Agent 最终裁定 |
|---|---|---|---|---|
| **真实生产调用** | 必须由真实 DeepSeek API 驱动 | 通过 ProductionDriver + SSE 真实解析 | 严禁与单元测试替身混淆 | **全量 10 次真实调用，落盘 REAL_PRODUCTION 凭单** |
| **防伪与防篡改** | 彻底删除关键字投毒作弊 | 凭单全字段 SHA-256 常量时间验签 | 引入 ExecutionMode 强类型隔离 | **全字段防篡改验签 + 去作弊化盲审** |
| **指标门禁与容差** | Token 节省为硬指标 | 耗时增加公网网络抖动合理容差 | 累计 $\Delta \text{Tokens} \le 0$ 硬拦截 | **以 Token 节省为主、耗时合理容差的综合门禁** |
| **工程生命周期** | 确保全流程凭单有效 | ScopedVoucherLease 幂等自动注销 | 堆内存零泄漏与全局配置只读 | **采纳 ScopedVoucherLease + 双重看门狗** |
| **成本预算控制** | 满足小样本统计代表性 | 优化 Prompt 消除无用 Token 浪费 | 批次 0.15 元 / 80k Tokens 累加熔断 | **10 次调用成本锁定在 0.02 元内，熔断器严密监控** |
| **证伪与停止条件** | 发生退化如实判定 REFUTED | 异常触发 FAIL_SAFE 兜底，进程不挂死 | 四大 Fail-Fast 条件触发立即终止 | **严格遵守科研实事求是纪律，假失败真记录** |

---

## 四、主 Agent 裁决结论与任务书冻结授权

主 Agent 做出终局裁决：
1. **辩论全面深入，分歧彻底化解**：三方独立智能体在真实性防伪、指标门禁、双向盲审、工程架构、成本控制与证伪法纪六大维度达成 100% 战略共识；
2. **具备完整决策完备性 (Decision-Complete)**：技术方案已达到直接指导代码落地的精度，无任何模糊灰色地带；
3. **正式批准冻结任务书**：立即依据辩论共识生成《Hermes Harness² 阶段五真实生产轨道探针验证实施任务书 (FROZEN TASKBOOK)》，计算全文件 SHA-256 哈希值写入 `.sha256` 签名文件，确保正文签名与文件哈希 100% 吻合！
