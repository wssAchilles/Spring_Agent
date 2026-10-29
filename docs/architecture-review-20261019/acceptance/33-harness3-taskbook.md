# Hermes Harness² 阶段三离线 Propose–Probe–Compose 实施任务书 (FROZEN TASKBOOK)

> **任务书版本**：3.0.0-STAGE3-FROZEN  
> **任务书状态**：`FROZEN`（待冻结签名写入）  
> **正文冻结 SHA-256 签名**：`ef487d8cff8848d968946e3e833040f4724e3835e397f7975181087e764bdc14`  
> **关联分支基线**：`feat/hermes-harness-stage2`（Commit `26c913e5`）  
> **实施目标分支**：`feat/hermes-harness-stage3`  
> **运行环境约束**：统一且唯一使用 SDKMAN 管理的 Java 21 隔离环境（`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`）  
> **模型与向量基线**：DeepSeek 官方最新活跃模型、阿里千问 1536 维超球面单位向量空间（$\|\mathbf{v}\|_2 = 1.0$）  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文铁律

---

## 一、阶段三唯一待验证假设与契约基准

### 1.1 阶段三唯一待验证核心假设 (Unique Falsifiable Hypothesis)

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

### 1.2 阶段二沉淀资产承接

1. **不可变物理证据凭单 (`HarnessEvidenceRecord`)**：
   - 记录执行真实耗时 `durationMs`、执行成功状态 `success`、入参摘要哈希 `inputDigest` 与出参摘要哈希 `outputDigest`；
   - 精确计算 `estimatedTokens` 与 `estimatedCostCny`（DeepSeek 输入 1 元/M，输出 2 元/M）；
   - 提供基于 SHA-256 与 `MessageDigest.isEqual()` 的常量时间验签方法 `computeDigest()` / `verifyDigest(expectedDigest)`。
2. **不可变八大契约与装配器 (`HermesHarnessVoucher` & `HermesHarnessAssembler`)**：
   - 字段对齐真实代码：`GuardrailsSpecificationRecord.maxCycleSteps / loopThreshold`、`ToolGuidancePolicyRecord.hoareVerificationEnabled / twoPhaseApprovalEnabled`；
   - 系统提示词装配严格遵守 DeepSeek 官方 1M Context Caching 规范：系统提示词（`SystemPrompt`）仅由静态角色、安全准则与工具说明拼接构成；动态上下文注入当前轮次的 `UserMessage` 前缀中。

---

## 二、三权分立隔离架构与防数据泄漏门禁

### 2.1 物理隔离三权分立机制
为彻底杜绝数据泄漏（Data Leakage），执行期强制遵循“生成侧只读隔离”、“盲审侧匿名对齐”与“判定侧独立仲裁”：
1. **生成侧只读隔离**：Probe 在执行 Baseline 和 Candidate 时，文件系统只挂载纯净任务描述、用户输入与已授权工具定义，严禁将包含 Gold Rubric、标准答案、评分判词的目录映射到 Probe 运行时的任何环境变量或内存中；
2. **盲审侧匿名对齐**：Selector 在进行二选一（Pairwise Selection）时，完全屏蔽候选真实身份标签（抹去 `base` vs `cand`），仅提供通用的输出完整性与工具合理性判据；
3. **判定侧独立仲裁**：黄金答案与真实物理指标核验仅在终验审计员沙箱中执行，判决结果作为存证记录，绝不回写给 Proposer。

### 2.2 防泄漏雷区扫描断言 (`SelectionDataLeakageTripwire`)
在离线评测上下文装配完成后，模型调用前强制执行 Java 代码级静态扫描断言：
- 扫描 9 类敏感判词标记（`deterministic_eval_summary`、`deterministic_eval_result`、`judge_scope_and_deterministic_status`、`passed_count`、`pass_rate`、`test_pass_rate`、`gold_rubric`、`ground_truth_answer`、`qrels.tsv`）；
- 扫描敏感文件通配路径（`*/qrels*`、`*/holdout/*`、`*/expected/*`、`*/rubrics/*`、`*/ground_truth/*`）；
- 一旦命中立即抛出 `IllegalStateException("CRITICAL_DATA_LEAKAGE")` 阻断执行。

---

## 三、5 个离线小样本基准任务集规范

任务集封装为不可变 Java 21 Record（`OfflineBenchmarkTestCase`），覆盖 5 个典型场景：
1. **TC-01 (知识库精确问答)**：`taskId="kb-qa-01"`，关于企业报销规范的精确抽取，考察 SystemPrompt 角色提取约束，严禁虚构数值；
2. **TC-02 (多工具依赖路由)**：`taskId="tool-route-02"`，多工具依赖推理与选择，考察 ToolGuidance 工具指引，严禁越权调用高危写工具；
3. **TC-03 (工具参数纠错与自愈)**：`taskId="param-guard-03"`，模糊日期与格式参数校验，考察参数前置约束自纠；
4. **TC-04 (多轮上下文历史与代词消解)**：`taskId="ctx-history-04"`，长程指代消除（“上一句提到的方案 A 的瓶颈”），考察 Memory 短期滑动窗口保真度；
5. **TC-05 (结构化输出与前缀缓存敏感型回答)**：`taskId="comp-rag-tool-05"`，标准 Markdown 表格与排版生成，考察静态前缀缓存友好度与输出紧凑性。

---

## 四、受控 PPC 闭环与双向盲审选择器契约

### 4.1 单变量受控变异提议器 (`OfflineHarnessProposer`)
- 严格限定在 3 个低风险组件：
  1. `InvariantSystemPromptRecord`：结构化约束强化与冗余精简（$\Delta \text{Chars} \le 0$）；
  2. `ToolGuidancePolicyRecord`：启用自动工具选择策略与拓扑指引；
  3. `MemoryContextStrategyRecord`：微调短期滑动窗口大小（有效区间 $[5, 30]$）。
- **静态前缀缓存保护校验 (`MutationSafetyValidator`)**：断言变异 System Prompt 绝不包含任何 `${...}` 或 `{{...}}` 动态占位符，确保 100% 命中 DeepSeek Context Caching。

### 4.2 探针差分提取器 (`EvidenceDiffExtractor`)
- 探针运行后计算物理差分：
  - $\Delta \text{Tokens} = \text{Tokens}_{\text{cand}} - \text{Tokens}_{\text{base}}$（门禁要求 $\le 0$）；
  - $\Delta \text{Duration} = \text{Duration}_{\text{cand}} - \text{Duration}_{\text{base}}$（门禁要求 $\le 5\text{ms}$）；
  - 工具调用匹配标记 `toolSelectionMatched` 与应答未退化标记 `nonDegradingQuality`。

### 4.3 双向交叉盲审评估器 (`DualBlindSelectionEvaluator`)
- **确定性伪随机映射**：基于 $\text{SHA-256}(\text{taskId} \parallel \text{"selection"})$ 生成打乱映射；
- **双向对调对称盲审 (Position-Swap Dual Pass)**：
  - Pass 1: 比较 $(A, B)$；
  - Pass 2: 对调比较 $(B, A)$；
  - **一致性裁决**：当且仅当两次盲审一致判定 Candidate 胜出时方判定采纳；若结论冲突（存在位置偏见）或平局，安全回退保持 Baseline。

---

## 五、量化指标体系、开销预算与 5 大熔断停止条件

### 5.1 量化指标体系
| 指标名称 | 计算公式 / 判定方法 | Baseline 期望 | Candidate 目标通过阈值 |
|---|---|---|---|
| **回答完备度得分 ($S_{\text{comp}}$)** | 关节点抽取覆盖率 ($N_{\text{hit}} / N_{\text{expected}}$) | 0.82 | $\ge 0.88$ (提升 $\ge 0.06$) |
| **工具调用准确率 ($P_{\text{tool}}$)** | 合法有效调用数 / 总调用数 ($N_{\text{valid}} / N_{\text{total}}$) | 0.85 | **100% (1.00)** (零非法、零错参) |
| **事实无幻觉率 ($F_{\text{faith}}$)** | 事实陈述与 RAG 切片完全忠实且无编造的比例 | 0.90 | **100% (1.00)** (零捏造) |
| **单次调用 Token 增量 ($\Delta \text{Tokens}$)** | $\text{Tokens}_{\text{cand}} - \text{Tokens}_{\text{base}}$ | 基线 Token 数 | **$\Delta \text{Tokens} \le 0$** (严禁膨胀) |
| **端到端执行耗时增量 ($\Delta \text{Duration}$)** | $\text{Duration}_{\text{cand}} - \text{Duration}_{\text{base}}$ | 基线耗时 | **$\Delta \text{Duration} \le 5\text{ms}$** |
| **DeepSeek 前缀缓存命中率 ($R_{\text{cache}}$)** | $\text{CachedTokens} / \text{PromptTokens}$ | 85.0% | **$\ge 85.0\%$** (保持缓存友好) |

### 5.2 五大立即熔断停止条件
1. **越界变异熔断 (`REJECT_OUT_OF_BOUNDS_MUTATION`)**：变异提议试图修改非指定 3 大组件、修改步数阈值或触碰具身物理代码，立即熔断；
2. **前缀缓存击穿熔断 (`REJECT_CACHE_BREAK`)**：在 System Prompt 中拼接动态变量导致前缀哈希变化或缓存命中率跌破 80%，立即熔断；
3. **开销预算超标熔断 (`REJECT_BUDGET_EXCEEDED`)**：$\Delta \text{Tokens} > 0$ 或 $\Delta \text{Duration} > 5\text{ms}$，立即熔断；
4. **幻觉与安全越权熔断 (`REJECT_HALLUCINATION_OR_SECURITY`)**：出现事实捏造或非法调用高危写工具，立即熔断；
5. **凭单签名篡改熔断 (`REJECT_DIGEST_CORRUPTED`)**：`HarnessEvidenceRecord.verifyDigest()` 校验失败，立即熔断。

---

## 六、离线凭单确定性回放验证契约

- 由 `OfflineEvidenceReplayer` 负责：
  1. 读取持久化的 `HarnessEvidenceRecord` 凭单集合；
  2. 调用 `record.computeDigest()` 重新计算 SHA-256 摘要；
  3. 执行 `record.verifyDigest(recomputedDigest)` 常量时间比对；
  4. 验证整组凭单的防篡改通过率达 100%。

---

## 七、最小修改与新增文件清单

### 7.1 允许新增的代码文件（仅限测试或离线 eval 包）
位于 `backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/offline/`：
1. `OfflineBenchmarkTestCase.java`（离线小样本基准任务契约 Record 与 5 个用例定义）；
2. `OfflineHarnessProposer.java`（单变量受控变异提议器与静态前缀安全校验器）；
3. `ProbeExecutionDiff.java` & `EvidenceDiffExtractor.java`（探针执行差异凭单与提取器）；
4. `SelectionDataLeakageTripwire.java`（三权分立防泄漏雷区扫描器）；
5. `DualBlindSelectionEvaluator.java`（双向交叉盲审评估器与映射 Record）；
6. `OfflineEvidenceReplayer.java`（离线不可变凭单确定性回放验证器）；
7. `HermesHarnessStage3OfflineLoopTest.java`（7 项针对性契约与回放测试套件）。

### 7.2 严格禁止修改的边界红线
- 严禁修改 `main` 分支代码；
- 严禁修改生产环境默认配置（保持 `hermes.orchestration.enabled=false`，`SINGLE` 模式）；
- 严禁修改 MCP stdio、HITL 审批流、SWARM/DEBATE 核心；
- 严禁修改已冻结归档的具身物理代码 `tech.qiantong.qknow.ai.embodied.*`；
- 严禁修改 1536 维超球面单位向量余弦度量核心。

---

## 八、隔离运行环境、验证命令与独立授权边界

### 8.1 隔离环境编译与测试命令
```bash
# 1. 验证 Proto 独立生成无竞态
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean compile -pl backend/qknow-hermes/qknow-hermes-proto -am

# 2. 执行阶段三离线闭环针对性契约测试 (7 项契约单测)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn test -Dtest=HermesHarnessStage3OfflineLoopTest -pl backend/qknow-hermes/qknow-hermes-core

# 3. 联合全量回归测试门禁 (要求连续 2 次全绿，1942 tests 零漂移)
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem mvn clean test -f backend/pom.xml -pl tests -am
```

### 8.2 独立授权边界
- 本任务书获得用户批准后，方可创建新分支 `feat/hermes-harness-stage3` 并开始代码实施；
- 阶段三成果仅限离线闭环探索与契约回放验证，未经独立生产发布授权，严禁将离线筛选出的候选凭单覆盖线上生产默认凭单。
