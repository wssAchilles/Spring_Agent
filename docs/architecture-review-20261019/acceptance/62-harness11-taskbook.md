# Hermes Harness² 阶段十一任务书：11A 契约收口与 Stage 10 稳定化

- **文档编号**：`62-harness11-taskbook.md`
- **修订状态**：`REVISION 2 - REVIEW REQUIRED`
- **实施状态**：`BLOCKED - 11A 实施等待另行授权；仅允许归档提交 61/62 文档`
- **当前分支基线**：`feat/hermes-harness-stage10` (`HEAD: 1a797bd1`)
- **Java**：`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`
- **生产保护**：不改 `main`、`AgentOrchestrator`、MCP、HITL、SWARM；不调用外部付费 API
- **正文哈希**：`945e586fa39ff692debf9d74c789a834ef3c6a2ddc54690e7d52b2f0f3546927`
- **全文件哈希**：以伴生文件 `62-harness11-taskbook.md.sha256` 为准

## 一、阶段拆分与当前状态

### 1.1 Stage 11A：契约收口与 Stage 10 稳定化

11A 的范围仅限于把 Stage 10 的交付边界、证据格式、构建门禁和经验库 API 契约写清，并在另行获准后完成最小稳定化。递归 PPC、经验感知算子加权和两轮状态机不属于 11A。

本轮授权仅覆盖文档修订、正文哈希与全文件哈希校验，以及 61/62 文档的归档提交。源码、配置、测试资源和 Stage 10 实现提交均不在本轮授权内。11A 另行获准前，不得开始源码稳定化。

### 1.2 Stage 11B：经验感知递归 PPC（延期）

11B 是独立后续阶段，至少包括经验感知提议器、PPC 递归调度、停止策略及其机制测试。它必须在 11A 契约验收后另行形成任务书并取得明确授权；不得因本任务书存在历史蓝图而推定已获准。11B 不得依赖本文件中已撤回的 VC 维、跨任务泛化保证、余弦召回或 Stage 9 因果规则。

### 1.3 不可变边界

1. `UNSCOPED_REPLAY` 只用于审计留存，不进入任何业务域正向或负向召回。
2. Stage 9 的 HISTORY、COMPOSITE 及候选差异仅为单次运行观察；归因保持 `UNRESOLVED`，不得固化成因果规则。
3. 生产在线路径不得动态读取经验库或拼接经验文本；本阶段不改变此边界。
4. 本文件是待复核修订稿；旧版 61/62 中与本版冲突的结论均已撤回，不得作为实施依据。

## 二、11A 稳定化契约

### 2.1 Stage 10 源码与测试交付

Stage 10 当前 12 个主源码文件及 2 个测试文件尚未提交。其精确清单沿用 [61-harness11-audit.md](61-harness11-audit.md)。实施获批后，应把这 14 个 Java 文件和版本受控的测试资源作为同一 Stage 10 稳定化范围审查；不得声称只提交 14 个 Java 文件就已包含回放夹具。

任何提交必须留在 `feat/hermes-harness-stage10`，不合入 `main`。是否提交以及提交内容须在实施授权后根据实际差异复核；本修订不构成提交授权。

### 2.2 Protobuf clean 构建：根因未证实

现有证据只支持：曾有 Maven 构建在编译 proto 模块时报告读取生成的 `ChatEvent.java`（或相关生成文件）失败；另一次日志显示 `javac` 获得 47 个源文件列表。该事实**不能证明**文件先完整生成后被删除，也不能证明 `clean` 竞态、Source Root 问题或任何单一根因。日志中的 Maven Compiler Plugin 版本还须与当前 POM 版本核对，不能混用不同运行记录作为同一事件的证据。

根因状态固定为 `UNPROVEN`。不得把“两步构建成功”表述为单步 clean 已修复。若要声称单步问题已修复，获批实施后必须：

1. 在同一 Java 21、Maven、依赖与代码 HEAD 下，单独连续执行 3 次 `clean test`，每次之间不改代码、配置或测试；
2. 保存每次完整日志，记录 Reactor 模块顺序、protobuf 生成文件数、失败时目标文件是否存在、编译器版本及退出码；
3. 三次均 `BUILD SUCCESS`、0 failure、0 error，才可报告“本环境重复单步验证通过”；这仍不等同于已证明历史失败根因。
4. 若单步故障不能稳定复现，结论只能是“根因未证实”。可以另行提出 CI 正式采用 proto 预构建的方案，但必须明确单步 clean 风险仍存在，不能称为修复。

准入命令必须统一为单步命令，不得以 `clean && test` 替代：

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem \
mvn -B -ntp -f backend/pom.xml -pl tests -am clean test
```

### 2.3 Stage 9 原始证据与派生测试夹具

原始 Stage 9 资产留在被 Git 忽略的 `experiment/outputs/harness9-live/c44e7e20-5e33-49c4-bc57-105a8cc3c347/`，只作本地只读证据；不得将原始会话文本或原始 live 目录加入版本库。原始清单 SHA-256 为 `7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288`，它只锚定原始 manifest，不是派生夹具的哈希。

获批实施后，派生夹具应放入 `qknow-hermes-core/src/test/resources/harness9-replay/<runId>/`，且必须覆盖三类资产：

1. **probes**：对 `userInputSummary`、`finalOutputSummary` 及其他自由文本作明确脱敏或固定占位；保留导入所需的 case/candidate ID、质量标记、计量字段、状态、时间和 provenance 字段。不得保留原始用户问题、模型答复、Prompt 或敏感业务实体。
2. **ppc_summary.json**：同样脱敏所有候选 voucher 中的 Prompt/自由文本；嵌入的 `probes` 必须与脱敏后 probe 文件逐对象一致；`checksumsSha256` 必须反映脱敏后 probe 字节。
3. **selected_candidate.json**：脱敏 voucher 中的自由文本，并验证 selected candidate、verdict、reason 及 voucher fingerprint 与 summary/candidate 之间定义的语义关系。不得因脱敏后指纹变化而沿用原始 fingerprint；需保留原始与派生指纹的离线映射记录。

固定生成与校验顺序：

```text
原始冻结文件只读核对
→ 对 probes、summary、selected 执行确定性 allowlist 投影/脱敏
→ 重算 summary.checksumsSha256
→ 将 summary.probes 与独立 probe 文件逐对象比对
→ 生成 test-checksums.sha256（列出派生文件字节哈希）
→ 计算派生 manifest 哈希并写入测试专用 descriptor
→ 用负例验证单文件篡改、数据与 manifest 同时重算、summary/selected 语义不一致均被拒绝
```

派生夹具应记录每个原始相对路径/哈希到派生相对路径/哈希的映射，以及脱敏规则版本；映射中不得复制原始敏感内容。派生数据与原始数据的只读对照只验证约定的语义投影字段（case/candidate 集、qualityPass、token/cost 聚合、verdict/selection、未决归因状态）。测试通过只证明**派生夹具的契约一致性及投影字段相符**，不证明独立来源真实性，也不替代原始 Stage 9 证据审计。

### 2.4 Importer 与经验接口边界

Stage 10 的 importer 是针对冻结 Stage 9 资产的导入器。除非未来有多个已验证且结构兼容的资产，不要求抽象“任意 PPC run”通用框架；descriptor 如保留，只描述已列明的 Stage 9 原始/派生两种资产，不宣称通用化。

现有 `ExperienceLibrary.recallForProposer` 是同域 cue 文本匹配，并在返回前排除 `REJECTED`、`failureObserved`、软淘汰及 `UNSCOPED_REPLAY` 条目。当前 API 不提供负经验召回，也没有 embedding/1024 维余弦召回。

11A 保持该既有正向召回契约，不新增 `recallForAudit`、负掩码或余弦机制。Stage 9 未决及拒绝记录只供隔离审计，不转写为全局静态规则，也不跨域给业务 Proposer 使用。若 11B 将来需要其他读取语义，必须先单独定义接口、威胁边界与测试，不得从当前接口推断。

## 三、理论与证据使用边界

旧版 Agent A 报告中的“提示词空间 VC 维无穷”“VC 维不超过 4.32 即推出开放域泛化界”“经验风险截断定理”“1024 维余弦 0.65 语义门槛”均撤回，不作为 11A/11B 的理论事实或实现前提。

有限算子集合只说明候选生成操作数量受限，不单独确定 induced hypothesis class 的 VC dimension。有限词表与有限 prompt 长度产生的是有限字符串集合；若要使用 Hoeffding/union bound，必须先定义固定假说类、固定损失、独立同分布抽样、损失有界、选择过程与样本隔离，并证明这些假设适用于实验。没有这些前提时只能称为工程设计假设或待检验启发式。

Stage 9 单次 5 题回放的观察不足以证明跨域因果规律。其拒绝候选及 HISTORY/COMPOSITE 失败一律保持观察状态和未决归因。Stage 11B 若继续研究统计泛化，应另立预注册实验与理论契约，不在本任务书中预先承诺证明结果。

## 四、11A 验收门禁

以下是用户复核并另行授权后的实施验收要求；当前尚未授权执行：

1. Stage 10 源码、测试与派生夹具范围明确，原始 live 资产未入库；
2. Stage 9 脱敏与哈希链按 2.3 执行，包含一致性、敏感字段及篡改负测；
3. `HarnessExperienceStage10Test` 仅从 classpath 测试资源读取，干净检出环境不依赖 Git 忽略目录；
4. Stage 9 导入结果仍为 7 条 `UNSCOPED_REPLAY` 审计记录：baseline 通过项 `OBSERVED`，candidate-a/b `REJECTED`，HISTORY/COMPOSITE `OBSERVED + failureObserved=true + attributionStatus=UNRESOLVED`；`ADOPTED=0`、`INVALID_FIXTURE=0`，正向召回为空；
5. 本节 2.2 的单步 clean 门禁按冻结次数执行；若故障未复现，报告 `UNPROVEN`，不得改写为已修复；
6. 定向测试与全仓默认测试均报告精确执行数、失败/错误/跳过数量及 JDK/Maven 版本；测试默认零外网。

## 五、Stage 11B 延后范围

待 11A 验收并经用户另行批准后，再制定独立任务书。届时可评估受控 proposer、递归轮次、停止策略和合成 domain 测试；不得预设其统计保证、余弦阈值或 Stage 9 负经验规则。不得以本修订、旧版辩论共识或子 Agent 自述作为 11B 实施授权。

## 六、当前授权与解锁条件

当前状态为 `BLOCKED`。解除本状态至少需要：本修订双哈希校验通过、用户复核通过、再明确授权 11A 实施。未满足前不得改源码/测试/配置、暂存或提交 Stage 10 实现、创建 Stage 11 分支、运行付费 API。

---
本文件修订后须重新计算 body/full SHA-256。哈希一致仅证明文件完整性，不代表任务书已获实施批准。
