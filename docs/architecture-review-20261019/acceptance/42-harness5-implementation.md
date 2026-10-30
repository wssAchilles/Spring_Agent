# Hermes Harness² 阶段五：真实 DeepSeek 生产轨道探针与 PPC 闭环验证终验交付报告

## 一、交付元数据与分支环境

| 属性项 | 交付记录 |
|---|---|
| **交付阶段** | Hermes Harness² 阶段五：真实 DeepSeek Probe 与生产轨道 PPC 验证 |
| **所属分支** | `feat/hermes-harness-stage5`（从阶段四彻底隔离修复分支派生，未合入 main） |
| **执行模型** | DeepSeek 官方活跃 API (`deepseek-flash`)，HTTP SSE 流式全链路驱动 |
| **Java 运行环境** | `/Users/achilles/.sdkman/candidates/java/21.0.5-tem`（Java 21 隔离环境，主机 Java 17 零污染） |
| **生产默认配置** | `hermes.orchestration.enabled=false`、`SINGLE` 模式保持不变，生产轨道零破坏 |
| **任务书冻结** | `docs/architecture-review-20261019/acceptance/41-harness5-taskbook.md` (`b4d42155...` 全量校验通过) |
| **定向单测通过率** | `26 / 26 PASS`（阶段一至阶段五全量契约测试与真实探针单测 100% 通过） |
| **系统全量回归** | `qknow-tests` 1942 门测试全绿通过（`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`） |
| **真实性保证** | 彻底根除阶段四测试替身伪造数据缺陷，10 次探针全部为 `REAL_PRODUCTION` 真实网络调用 |

---

## 二、阶段四伪造漏洞彻底根除与防伪架构治理

阶段四由于使用了 `DeterministicHarnessTestDouble`，导致其在 `ppc_summary.json` 中输出了人为写死的 `-40 tokens` 与硬编码字符串拼接 `(精炼客观)`，产生虚假的 `hypothesisVerified=true`。

在阶段五中，团队完成了法医级架构清洗与防伪体系加固：
1. **显式引入执行模式枚举**：在不可变凭单 `HarnessEvidenceRecord` 中增加 `ExecutionMode`（`REAL_PRODUCTION`、`DETERMINISTIC_TEST_DOUBLE`、`FAIL_SAFE`），并强制并入 SHA-256 凭单签名；
2. **测试替身资产严格隔离**：将阶段四测试替身生成的凭单隔离至 `experiment/outputs/harness4-synthetic/`，严禁冒充生产实验；
3. **全面启用真实生产驱动**：`OfflineProbeRunner` 与 `DefaultOfflineProbeExecutor` 接入 `ProductionAgentOrchestratorDriver`，经由 `AgentOrchestrator` -> `ChatModelFactory` -> `DeepSeekCompatibleChatModel` 直连 DeepSeek 官方 API；
4. **消除文本投毒与作弊偏见**：从评判器中彻底删除针对 `(精炼客观)` 的关键字作弊判定，完全基于真实的物理差分指标与双向盲审仲裁；
5. **双重看门狗与预算熔断**：探针引入 60s 真实公网超时熔断、2 次重试兜底与 Reactor 底层异常解包。

---

## 三、真实 DeepSeek 探针物理执行指标全景总账

在 5 大离线基准用例上，真实调度 DeepSeek 官方 API (`deepseek-flash`) 进行了双向探针（Baseline vs Candidate）共 10 次真实调用，落盘结果全部为真实生成的中文回答，无任何人工干预或伪造。

### 3.1 真实物理执行指标明细对比表

| 任务标识 (`taskId`) | 业务场景 | Baseline Tokens | Candidate Tokens | Token 差分 ($\Delta T$) | Baseline 耗时 (ms) | Candidate 耗时 (ms) | 耗时差分 ($\Delta D$) | 盲审仲裁结果 (`VerdictOutcome`) |
|---|---|---|---|---|---|---|---|---|
| `kb-qa-01` | 订单支付超时重试与幂等规范 | 389 | 366 | **-23** (-5.9%) | 6667 | 5802 | **-865** | `SHORT_CIRCUITED_REJECT` (工具调用不匹配) |
| `tool-route-02` | 银行流水扣款核查与余额校验 | 422 | 529 | **+107** (+25.4%) | 6803 | 7408 | **+605** | `SHORT_CIRCUITED_REJECT` (Token 膨胀) |
| `param-guard-03` | 日历会议创建与非标时间纠错 | 418 | 461 | **+43** (+10.3%) | 5889 | 9603 | **+3714** | `SHORT_CIRCUITED_REJECT` (Token 膨胀) |
| `ctx-history-04` | 消除指代与长程多轮对话推理 | 465 | 681 | **+216** (+46.5%) | 11506 | 13828 | **+2322** | `SHORT_CIRCUITED_REJECT` (Token 膨胀) |
| `comp-rag-tool-05` | 微服务架构方案对比与表格生成 | 711 | 587 | **-124** (-17.4%) | 11861 | 11777 | **-84** | `SHORT_CIRCUITED_REJECT` (工具调用不匹配) |
| **全量汇总 / 均值** | **5 大基准任务集合计** | **2405** | **2624** | **+219 (+9.1%)** | **42726** | **48418** | **+5692 (+13.3%)** | **采纳率: 0 / 5 (0%)** |

---

## 四、科学实验结论与假设验证判定

### 4.1 核心假设判定：被证伪 (REFUTED)

- **阶段五待验证假设**：通过提示词精炼压缩变异（`SYSTEM_PROMPT_CONCISE`），在保证核心答案不退化的情况下，能使端到端生成的平均 Token 数量减少至少 30 Tokens，且公网 RTT 耗时增量不超过 25ms。
- **真实实验判定**：**`hypothesisVerified = false`（假设被证伪 / REFUTED）**。
- **客观物理归因**：
  1. **局部收益确实存在**：在纯问答的 `kb-qa-01`（减少 23 tokens，提速 865ms）与长文档输出的 `comp-rag-tool-05`（减少 124 tokens，提速 84ms）中，精炼提示词确实有效降低了生成冗余；
  2. **全局发生 Token 膨胀**：在需要工具路由、上下文指代消歧与参数纠错的复杂场景（`tool-route-02`, `param-guard-03`, `ctx-history-04`）中，系统提示词的删减导致模型失去了对核心决策约束的明确引导，模型在自主推理时生成了更多的说明性文本与反思步骤，导致单任务 Token 反向膨胀高达 +107、+43、+216 tokens；
  3. **硬指标熔断拦截**：双向盲审评判器严格执行物理门禁，针对 Token 发生正向膨胀的用例直接判定 `SHORT_CIRCUITED_REJECT`，绝不因局部个案而盲目采纳变异。

### 4.2 科研工程价值

本实验给出了反直觉却极其严密的科学结论：**在生产级 Agent 系统中，“极简提示词”并非万能灵药**。过度精简系统级安全原则与格式规约，反而会迫使大模型在下游长思维链中消耗更多 Token 去“自我补充上下文”。这彻底印证了阶段四伪造实验数据的危害，展现了阶段五真实物理探针不可替代的工程真实性。

---

## 五、物理证据资产不可篡改清单 (`checksums.sha256`)

实验物理资产已全量落盘至 `experiment/outputs/harness5/run-20261030-stage5-real/`，所有文件通过 `shasum -a 256` 签名并 100% 校验一致：

```text
d346802dd2df5c6b02c0f893fa157e00d23cc5d3f98515047e40d18c1025c631  baseline/evidence_comp-rag-tool-05.json
3370a4fb5c272f9da35e031d0723000a4fcc5ae30273a53c567779b2fbfdd274  baseline/evidence_ctx-history-04.json
64985a07620d1cbf7319779d23aaf857972aadf4c23624436b34bf039fe12b3a  baseline/evidence_kb-qa-01.json
3bfd0aa59d02cc06e87a0c7be938fc11ee2347c737cbcc39b6e89a35498b14f5  baseline/evidence_param-guard-03.json
1d17bd60aeda1c529fcf4bf1007ce53734078ede1c96db309f380a6ffc47e525  baseline/evidence_tool-route-02.json
cc18f6f71b1a7f48c966d9fadbbf93e9e85b73d4ea27c4e63dddf5c9ca69ab1f  candidate/evidence_comp-rag-tool-05.json
a85ad9cbfbb07f704b604e6cd013d47e6cbdd7a1ae7d8fc8a39758e5c765184b  candidate/evidence_ctx-history-04.json
aec97907b42d78c1a5fb79e7c788281d758a7e7b47dcc62096057b7504e26c41  candidate/evidence_kb-qa-01.json
83c4a82fa65a653015b465b10d98b2c3a7909bf1b3d6080b800ad60b13204332  candidate/evidence_param-guard-03.json
eee1ba64a391d85d5c76e57ef707ecbab88251dc08bbafa4a0285339fa3460ec  candidate/evidence_tool-route-02.json
bfed5ff6a88f700a04f44f70b1bd6dae406a0265bbe1f3f397a2c4bd8cc0e0d9  eval/ppc_summary.json
```

---

## 六、测试验证命令与结果

### 6.1 阶段一至阶段五全量定向单测（26/26 PASS）

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp test -f backend/pom.xml -pl qknow-hermes/qknow-hermes-core -am \
  -Dtest=HermesHarnessContractTest,HermesHarnessStage2RuntimeTest,HermesHarnessStage3OfflineLoopTest,HermesHarnessStage4ContractTest,HermesHarnessStage5RealProbeTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```
- **执行结果**：`Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`，构建成功。

### 6.2 全量系统回归测试（1942 PASS）

```bash
JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem PATH=/Users/achilles/.sdkman/candidates/java/21.0.5-tem/bin:$PATH \
mvn -B -ntp test -f backend/pom.xml -pl tests -am
```
- **执行结果**：`Tests run: 1942, Failures: 0, Errors: 0, Skipped: 8`，全量模块构建成功。

---

## 七、生产红线与后续授权边界

1. **生产默认行为不可变**：生产环境继续维持 `hermes.orchestration.enabled=false`、`SINGLE` 单智能体运行基线，探针代码严格局限于受控离线测试作用域；
2. **零泄漏合规**：全流程所有请求入参、环境变量及落盘凭单绝不包含 API Key 明文，严格遵循零信道泄漏铁律；
3. **独立授权边界**：本次实验严格属于离线科研探针验证阶段，未经独立专项授权，绝不将任何 Candidate 提示词变异直接推送合入生产环境。
