# Hermes Harness² 阶段三架构只读审计与假设锁定报告

> **报告版本**：3.0.0-STAGE3-AUDIT  
> **关联分支**：`feat/hermes-harness-stage2`（阶段三开发前夕）  
> **审计基准**：
> - 阶段二实施代码：Commit `26c913e5`（包含真实时序、成本、RAG 分数与 Proto 构建修复）
> - Harness² 核心源码：`/Users/achilles/Documents/优秀开源项目/harness2/harness2/core/`（`proposer.py`、`recursion.py`、`context.py`、`selection.py`、`library.py`、`ledger.py`、`runmark.py`）
> - 本项目评测基线：Set-A / Set-B 知识库基线、RAG 检索流水线与 SINGLE 执行路径
> **环境基线**：SDKMAN Java 21 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)、DeepSeek 官方活跃模型、阿里千问 1024 维超球面向量空间  
> **准则遵循**：《AGENTS.md》科研门禁与独占简体中文规范

---

## 一、阶段二修复与基准状态复核

在进入阶段三之前，阶段二遗留的 6 项核心阻断已通过 Commit `26c913e5` 完成针对性闭环：
1. **文献标题与 Research Ledger 纠正**：
   - 官方论文标题已修正为 `Harness²: Recursive Agent Harnessing for an Open World` (arXiv:2610.14224)，8 源作者、年份、license 及固定 commit/tag 完全如实对齐；
2. **任务书字段与冻结签名一致性**：
   - 字段统一为代码真实名称：`GuardrailsSpecificationRecord.maxCycleSteps / loopThreshold`、`ToolGuidancePolicyRecord.hoareVerificationEnabled / twoPhaseApprovalEnabled`；
   - 纠正为“轻量执行摘要证据”；
   - 任务书正文签名 `e10b7eb1a95b9470cf7730741c1baf70cb7be42f7a738ed8063ef7f8986e7dd4` 与独立校验文件 `29-harness-stage2-taskbook.md.sha256` 经 `shasum -c` 验证 100% 吻合；
3. **证据采集器真实性提升**：
   - 工具执行通过 `wrapWithCycleGuard` 的 `finally` 逻辑精准采集真实执行耗时 `durationMs`、入参摘要哈希与出参摘要哈希；
   - 基于 DeepSeek 官方标准单价（输入 1 元/M tokens，输出 2 元/M tokens）真实计算 `estimatedCostCny`，废除硬编码 0.0；
   - 明确标注 `ragTopScore` 为检索置信度评测分数；
4. **构建门禁与 Proto 竞态消除**：
   - 验证 `qknow-hermes-proto` 单独 clean compile 稳定生成 47 个源文件；
   - 固化预构建前置保证，全量测试 1942 tests 全绿构建通过。

---

## 二、Harness² Propose–Probe–Compose (PPC) 算法法医审计

通过深度研读 `harness2/core/proposer.py`、`recursion.py`、`context.py`、`selection.py`，提炼其核心可移植算法精要：

### 1. 任务级递归流水线 (Task-Level Recursion Pipeline)
- **Base Execution**：执行基准 Harness，获取基准物理执行痕迹 $\tau_0$ 与交付物 $\mathcal{D}_0$；
- **Propose (变异提议)**：
  Proposer 模型读取任务目标与基线输出，在低风险正交维度生成变异：
  - **Invariant (INV)**：修改全局提示词与执行策略模板；
  - **Domain (DOM)**：注入领域专业格式与工具参数约束；
- **Probe (隔离探针执行)**：
  将候选 Harness 置于隔离沙盒中对相同任务进行执行，收集物理差分 $\Delta \tau = (\text{exit\_code}, \text{stderr}, \text{output\_diff})$；
- **Compose (差分驱动合成)**：
  基于 Probe 产生的对比信号，合成出综合最优的候选 Harness；
- **Selection (匿名盲审选择器)**：
  - 核心设计：`selection.py` 通过随机种子打乱候选项（Label A..Z），隐藏真实配置来源；
  - 评选完全基于产出物质量与执行证据，**坚决不向 Proposer/Selector 透露真实 Grader/Rubric 评分细则**，彻底杜绝数据泄漏（Data Leakage）。

### 2. 对比 Hermes：哪些可迁移、哪些必须坚决拒绝
| Harness² 机制 | Hermes 适用性判断 | 阶段三裁决 | 架构理由 |
|---|---|---|---|
| **离线 Propose–Probe–Compose 闭环** | 高度适用 | **深度吸纳** | 在离线小样本集上探索最优组件配置，不干扰线上生产。 |
| **匿名盲审选择器 (Selection)** | 高度适用 | **深度吸纳** | 消除大模型位置偏置，杜绝将测试标准/qrel 泄漏给生成侧。 |
| **真实物理证据回放 (`HarnessEvidenceRecord`)** | 高度适用 | **深度吸纳** | 将阶段二沉淀的真实证据作为 Probe 差分依据，提升合成依据真实性。 |
| **在线生产流量自动变异 Prompt** | 极度危险 | **坚决拒绝** | 破坏 DeepSeek 1M Context Caching，首字延迟增加 300%，破坏确定性。 |
| **大模型动态生成 Python 脚本在 Host 执行** | 严重安全风险 | **坚决拒绝** | 违背企业零信任原则，严禁开放动态代码编译与执行权限。 |
| **跨进程 MCP stdio 动态启停与 SWARM 变异** | 系统过载风险 | **坚决拒绝** | 严控单变量实验范围，锁定在 SINGLE 模式低风险组件。 |

---

## 三、阶段三唯一待验证假设 (Unique Falsifiable Hypothesis)

基于上述只读审计与边界约束，阶段三唯一可证伪假设锁定为：

> **“在 Hermes SINGLE Agent 架构的离线小样本基准任务集（包含知识库检索问答、多工具选择与上下文历史理解的 5 个代表性真实用例）上，以阶段二沉淀的不可变凭单 `HarnessEvidenceRecord` 作为物理证据，执行 $k=1$ 的离线受控 Propose–Probe–Compose 最小闭环，对 System Prompt（静态角色约束）、Tool Guidance（工具选择与参数指引）、Memory（短期记忆滑动窗口）三个低风险组件进行候选变异与匿名盲审比较（Selection）；在 100% 保持生产默认配置与静态前缀缓存友好的前提下，能够在不增加单次调用 Token 消耗与延迟预算（$\Delta \text{Tokens} \le 0, \Delta \text{Duration} \le 5\text{ms}$）且零数据泄漏的严格门禁下，筛选出在任务回答完备度或工具调用准确率上显著优于 Baseline 的候选 Harness，并完成离线凭单确定性回放验证。”**

---

## 四、审计准入裁定

- [x] 阶段二所有验收阻断均已完成物理修复与提交留痕；
- [x] Harness² 核心源码与算法逻辑已完成逐行法医解构；
- [x] 阶段三边界已被 100% 锁定在“离线受控实验”，坚决排除任何在线自我修改；
- [x] 阶段三唯一假设具备清晰因果关系、物理指标与明确的证伪条件。

**准入结论**：只读审计通过，准予进入【阶段 2：定向论文与代码检索】。
