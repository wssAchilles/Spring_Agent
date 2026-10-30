# Hermes Harness² 阶段五真实生产轨道探针验证实施任务书 (FROZEN TASKBOOK)

> **任务书版本**：5.0.0-STAGE5-FROZEN  
> **任务书状态**：FROZEN（已冻结）  
> **正文冻结 SHA-256 签名**：`b4d42155c2041f44e1b14e770a5e08832bd1933757a42b2dd5535fb9051f958e`  
> **正文签名复算命令**：`sed -n '/## 一、阶段五唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/41-harness5-taskbook.md | shasum -a 256`  
> **全文件校验文件**：`docs/architecture-review-20261019/acceptance/41-harness5-taskbook.md.sha256`  
> **关联分支基线**：`feat/hermes-harness-stage4`（Commit `d96f33af`）  
> **实施目标分支**：`feat/hermes-harness-stage5`  
> **运行环境约束**：统一且唯一使用 SDKMAN 管理的 Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）  
> **大模型规范**：DeepSeek 官方活跃模型 `deepseek-flash`（https://api.deepseek.com 官方协议，解析 `reasoning_content`）；阿里千问 `text-embedding-v4` 1024 维超球面单位向量（$\|\mathbf{v}\|_2 = 1.0$，余弦度量）  
> **冻结日期**：2026-10-30  

---

## 一、阶段五唯一待验证核心假设与契约基准

### 1.1 唯一待验证核心假设
> **“在 Hermes SINGLE Agent 架构的 5 个离线小样本基准任务集（涵盖知识库问答、多工具路由选择、工具非法参数纠错、上下文理解、复合推理）上，使用真实 `ProductionAgentOrchestratorDriver` 驱动底层真实 DeepSeek 官方 API (`deepseek-flash`)，对 Baseline 与 1 个精炼候选变异 Harness（`SYSTEM_PROMPT_CONCISE`）各执行一次真实生产轨道探针执行（共 10 次真实模型调用）；以真实物理落盘的 `HarnessEvidenceRecord`（格式为标准 JSON，标明 `executionMode: REAL_PRODUCTION`，分别落盘于 `experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`）为客观依据：若 10 次真实调用均成功完成且各凭单真实记录 DeepSeek 输出的 tokens 与耗时，且候选 Harness 在总 Token 消耗或平均耗时上有显著的真实物理节省且工具匹配状态不退化，则假设成立 (VERIFIED)；若真实调用因网络或接口异常失败触发 FAIL_SAFE，或候选相比基线无物理节省甚至发生退化，则客观如实判定假设不成立 (REFUTED)；严禁使用任何测试替身或固定差值伪造实验结果。”**

### 1.2 阶段五验收通过充要条件（必须同时满足，缺一即不通过）
1. **真实物理探针执行与真实凭单落盘**：
   - 彻底废除任何 `DeterministicHarnessTestDouble` 生成的虚假数据，使用真实 `ProductionAgentOrchestratorDriver` 调用底层 DeepSeek 官方 API；
   - 10 份物理证据 JSON 真实落盘至 `experiment/outputs/harness5/<runId>/baseline/*.json`（5 份）与 `candidate/*.json`（5 份）；
   - 所有落盘凭单必须包含 `executionMode: REAL_PRODUCTION`、真实网络 RTT 耗时（非测试替身固定值）、真实 DeepSeek 输出，并通过全字段 SHA-256 常量时间验签。
2. **四维物理量化指标门禁达成**：
   - **任务成功率 ($SR_{\text{task}}$)**：$SR_{\text{cand}} \ge SR_{\text{base}}$（5/5，100% 成功，无未捕获异常）；
   - **工具调用合规率 ($SR_{\text{tool}}$)**：在工具测试用例上达到 100% 预期匹配，错参用例精准触发前置引导降级；
   - **Token 消耗增量 ($\Delta \text{Tokens}$)**：5 个任务累计 $\Delta \text{Tokens} = \sum (\text{Tokens}_{\text{cand}} - \text{Tokens}_{\text{base}}) \le 0$（候选变异严禁总 Token 膨胀）；
   - **执行耗时增量 ($\Delta \text{Duration}$)**：候选平均耗时在合理网络公网波动容差内（单次均值增量 $\le 1500\text{ms}$），总耗时受 120s 熔断器保护。
3. **去作弊化无偏 Position-Swap 双向盲审**：
   - 彻底删除裁判对 `(精炼客观)` 字样的作弊硬编码，采用真实文本质量与语义裁决；
   - 严格执行 Pass 1 与 Pass 2 双向对调盲审，在 Quadrant 1 双向一致采纳时准予胜出；在物理退化时精准触发 `SHORT_CIRCUITED_REJECT`。
4. **硬件级累加熔断器与防数据泄漏零误伤**：
   - `OfflineBudgetCircuitBreaker` 实时统计 10 次调用，总 tokens $\le 80,000$，成本 $\le 0.15\text{ CNY}$，总耗时 $\le 120,000\text{ms}$；
   - `SelectionDataLeakageTripwire` 拦截 24 个判词与 10 个敏感路径，零泄漏。
5. **全量回归测试双绿灯验证**：
   - 阶段五定向测试（真实探针测试 + 测试替身隔离测试 + FAIL_SAFE 自愈测试）全部一次性通过；
   - `qknow-tests` 全量 1942 门测试连续两次构建通过（Double-Green，0 failure, 0 error）。

---

## 二、5 大离线基准评测用例与变异凭单规格

### 2.1 官方冻结的 5 大离线基准用例
| 任务标识 | 业务场景分类 | 用户输入 Query 核心诉求 | 预期工具匹配与行为约束 |
|---|---|---|---|
| **`kb-qa-01`** | `KNOWLEDGE_QA` | 查询订单支付系统超时重试次数与幂等键生成规则 | 匹配 `knowledgeBase_MiddleWare_Spec`，精准提取参数 |
| **`tool-route-02`** | `MULTI_TOOL_SELECTION` | 交易扣款成功但未生成凭单，核查流水并查账户余额 | 匹配 `banking_transaction_query`，首选关键流水核查 |
| **`param-guard-03`** | `TOOL_PARAM_GUARD` | 输入非标准非法时间参数 (`2026/10/29 25:99:99`) | 触发参数前置校验，系统提示词引导自纠降级 |
| **`ctx-history-04`** | `CONTEXT_HISTORY_RECALL` | 针对方案 A 最担忧瓶颈，网络数据面上有何绕过方案 | 跨轮代词消解（指代 service_mesh_latency），零工具纯文本推理 |
| **`comp-rag-tool-05`** | `COMPOSITE_REASONING` | 生成微服务架构演进评估报告，包含 4 列表格与总结 | 匹配 `knowledgeBase_Arch_Spec`，生成结构化表格并前缀缓存对齐 |

### 2.2 基线与候选变异凭单差异契约
- **基线凭单 (Baseline Voucher)**：
  - 系统提示词：`"你是一个企业知识与工具中枢智能体。请基于给定的知识库和工具严谨回答用户问题。请一步步思考，回答需客观准确，严禁臆测。"`
  - 工具策略：默认严格约束；
  - 记忆窗口：短期窗口 5 轮。
- **候选变异凭单 (Candidate Voucher - `SYSTEM_PROMPT_CONCISE`)**：
  - 变异维度：系统提示词精炼化（消除冗余模板修饰词，直接聚焦事实与指令执行）；
  - 系统提示词：`"你是企业知识与工具中枢智能体。请严格依据所给知识库和工具准确回答用户问题，保持客观精炼，杜绝臆测。"`
  - 保留全部不可变量：工具定义签名不变、Hoare 前置条件约束不变、防护栏阈值不变。

---

## 三、实施文件集合与明确禁止修改的边界

### 3.1 允许新增与重构的最小文件集合
1. **`OfflineProbeRunner.java`**（增强重构）：
   - 支持从环境或配置安全读取 DeepSeek API 凭据，接入 `ProductionAgentOrchestratorDriver`；
   - 落盘根路径指定为 `experiment/outputs/harness5/`；
   - 彻底去除盲审裁判中的硬编码关键字匹配作弊逻辑；
2. **`HermesHarnessStage5RealProbeTest.java`**（新增阶段五定向测试套件）：
   - 测试 1：`testRealProductionTrackProbeExecutionAndPersist`（真实驱动 10 次调用，验证落盘与指标真实性）；
   - 测试 2：`testDeterministicDoubleIsolationInSyntheticDir`（测试替身隔离在 synthetic 目录，互不干扰）；
   - 测试 3：`testFailSafeSynthesisOnInterruption`（验证异常时 FAIL_SAFE 兜底凭单生成与状态机熔断）；
3. **交付文档与资产**：
   - 真实物理落盘资产：`experiment/outputs/harness5/<runId>/baseline/*.json` 与 `candidate/*.json`、`ppc_summary.json`、`checksums.sha256`；
   - 终验交付报告：`docs/architecture-review-20261019/acceptance/42-harness5-implementation.md`。

### 3.2 明确禁止修改的系统边界与红线
1. **绝对禁止修改 `main` 分支**；
2. **绝对禁止在线修改生产配置**：`hermes.orchestration.enabled` 生产默认值必须保持 `false`；
3. **绝对禁止在阶段五受控实验中冒充真实结果**：落盘凭单的 `executionMode` 必须为 `REAL_PRODUCTION`（或异常时的 `FAIL_SAFE`）；
4. **绝对禁止在代码、日志、报告或 JSON 中打印明文 API 密钥**；
5. **绝对禁止修改既有 `tests` 模块已有测试的预期值以掩盖失败**。

---

## 四、四维物理指标、Position-Swap 双向盲审与风控门禁

### 4.1 四维量化指标定义
1. **任务成功率 ($SR_{\text{task}}$)**：
   $$SR = \frac{\text{成功完成用例数}}{\text{总用例数 (5)}} = 100\%$$
2. **工具调用合规率 ($SR_{\text{tool}}$)**：
   $$SR_{\text{tool}} = \frac{\text{工具选择与预期严格匹配用例数}}{\text{涉及工具用例数}} = 100\%$$
3. **Token 消耗增量 ($\Delta \text{Tokens}$)**：
   $$\Delta \text{Tokens} = \sum_{i=1}^5 \text{Tokens}_{\text{cand}, i} - \sum_{i=1}^5 \text{Tokens}_{\text{base}, i} \le 0$$
4. **执行耗时增量 ($\Delta \text{Duration}$)**：
   $$\Delta \text{Duration} = \frac{1}{5} \sum_{i=1}^5 (\text{Duration}_{\text{cand}, i} - \text{Duration}_{\text{base}, i}) \le 1500\text{ms}$$

### 4.2 去作弊化 Position-Swap 双向盲审规则
1. **物理差分短路层**：若 $\Delta \text{Tokens} > 0$ 或工具调用失配，直接输出 `SHORT_CIRCUITED_REJECT`；
2. **置换与盲审**：基于任务安全盐值，对调基线与候选顺序分别执行 Pass 1 与 Pass 2；
3. **裁判原则**：在回答正确完整的前提下，优先选择信息密度高、语言精炼客观、无格式套话的一方；
4. **采纳决策**：仅在 Quadrant 1（Pass 1 与 Pass 2 均判候选更优）时输出 `ROBUST_WIN_ADOPT`；若两轮冲突输出 `BIAS_CONFLICT_REJECT` 并安全回退基线。

---

## 五、复现验证命令与精确测试计数

### 5.1 阶段五定向测试验证命令
在隔离环境 Java 21 下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=HermesHarnessContractTest,HermesHarnessStage2RuntimeTest,HermesHarnessStage3OfflineLoopTest,HermesHarnessStage4ContractTest,HermesHarnessStage5RealProbeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
- **预期计数**：阶段一 (6) + 阶段二 (4) + 阶段三 (7) + 阶段四 (6) + 阶段五 (3) = **26 / 26 PASS**。

### 5.2 全量连续两次回归测试命令 (Double-Green)
在隔离环境 Java 21 下执行：
```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn test -f backend/pom.xml -pl tests -am
```
- **预期计数**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，连续两次 **BUILD SUCCESS**。

---

## 六、四大立即停止条件 (Fail-Fast) 与客观证伪红线

一旦发生以下任一情况，实验必须立即无条件终止并如实出具诊断报告：
1. **外部 API 连通性中断连续 2 次**（HTTP 5xx、401、429 重试耗尽或超时中断）；
2. **实时累加熔断器触发**（Tokens > 80,000，成本 > 0.15 CNY，耗时 > 120s）；
3. **数据泄漏拦截器触发**（抛出 `CRITICAL_DATA_LEAKAGE`）；
4. **物理指标出现严重退化**（总 Token 消耗显著膨胀或工具调用失配）。

**客观证伪红线**：实验若未能达成 Token 节省或判定候选未胜出，必须如实记录并在报告中判定假设不成立（`REFUTED`），将真实物理凭单作为消融证据完整归档，严禁任何形式的数据造假。
