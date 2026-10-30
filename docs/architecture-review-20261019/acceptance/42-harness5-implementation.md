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
| `kb-qa-01` | 订单支付超时重试与幂等规范 | 284 | 231 | **-53** (-18.7%) | 5145 | 4282 | **-863** | `SHORT_CIRCUITED_REJECT` (工具调用不匹配) |
| `tool-route-02` | 银行流水扣款核查与余额校验 | 340 | 175 | **-165** (-48.5%) | 4678 | 4684 | **+6** | `SHORT_CIRCUITED_REJECT` (耗时超出容忍上限) |
| `param-guard-03` | 日历会议创建与非标时间纠错 | 719 | 433 | **-286** (-39.8%) | 9925 | 6935 | **-2990** | `SHORT_CIRCUITED_REJECT` (工具调用不匹配) |
| `ctx-history-04` | 消除指代与长程多轮对话推理 | 301 | 678 | **+377** (+125.2%) | 4672 | 12037 | **+7365** | `SHORT_CIRCUITED_REJECT` (Token 正向膨胀) |
| `comp-rag-tool-05` | 微服务架构方案对比与表格生成 | 756 | 681 | **-75** (-9.9%) | 13442 | 16750 | **+3308** | `SHORT_CIRCUITED_REJECT` (耗时超出容忍上限) |
| **全量汇总 / 均值** | **5 大基准任务集合计** | **2400** | **2198** | **-202 (-8.4%)** | **37862** | **44688** | **+6826 (+18.0%)** | **采纳率: 0 / 5 (0%)** |

---

## 四、科学实验结论与假设验证判定

### 4.1 核心假设判定：被证伪 (REFUTED)

- **阶段五待验证假设**：通过提示词精炼压缩变异（`SYSTEM_PROMPT_CONCISE`），在保证核心答案不退化的情况下，能使端到端生成的平均 Token 数量减少至少 30 Tokens，且公网 RTT 耗时增量不超过 25ms。
- **真实实验判定**：**`hypothesisVerified = false`（假设被证伪 / REFUTED）**。
- **客观物理归因**：
  1. **局部 Token 节省确实显著**：在 `kb-qa-01`（-53 tokens）、`tool-route-02`（-165 tokens）、`param-guard-03`（-286 tokens）与 `comp-rag-tool-05`（-75 tokens）中，精炼提示词均显著减少了模型生成的 Token 消耗，总 Token 净降 202 个；
  2. **长程推理严重发散与反弹**：在多轮上下文指代消歧用例（`ctx-history-04`）中，系统提示词的过度精炼删除了关键的指代消歧指导，导致模型在推理时产生了严重的认知不确定性，触发了漫长的自我反思与推演，单任务 Token 反向暴涨 +377 tokens，耗时由 4.6s 剧增至 12.0s；
  3. **耗时预算突破上限**：由于缺少清晰格式引导，模型在 `comp-rag-tool-05` 生成对比表格时推理步骤增加，导致公网耗时延长 3.3s，违反了 $\Delta D \le 25\text{ms}$ 的严苛预算；
  4. **硬指标门禁严格执行**：双向盲审评判器忠实执行多重硬指标拦截（Token 膨胀硬拦截、耗时越限拦截与工具调用完整性拦截），采纳数为 0，系统拒绝将该变异推向生产。

### 4.2 科研工程价值

本实验给出了反直觉却极其严密的科学结论：**在生产级 Agent 系统中，“极简提示词”并非万能灵药**。过度精简系统级安全原则与格式规约，虽然能在短问答中节省少量 Token，但会导致大模型在长程推理中因信息不充分而产生昂贵的长思维链发散。这彻底印证了阶段四伪造实验数据的危害，展现了阶段五真实物理探针不可替代的工程真实性。

---

## 五、物理证据资产不可篡改清单 (`checksums.sha256`)

实验物理资产已全量落盘至 `experiment/outputs/harness5/run-20261030-stage5-real/`，所有文件通过 `shasum -a 256` 签名并 100% 校验一致：

```text
73c0ad5af36b2874a65b3b7b48db350834081f40e573b00c3cf47cb57e0d4341  baseline/evidence_comp-rag-tool-05.json
15710e3aa2e438c656319bbce52d0cc3a8f9ec203747db02f2966eabbd81df2a  baseline/evidence_ctx-history-04.json
bc4aafdfdeedd0677c96fe9ba04fb9b510177ee5f9b1cdaecae5c819a42a7ae1  baseline/evidence_kb-qa-01.json
5363c417224b04eab7df53b37e606a7bbb133d7af9124fd1653026203b1b3ea1  baseline/evidence_param-guard-03.json
20dc220ecfd29d7d7da146e554b1a2cfeefcb18e9b90726f0cf867cfae94228a  baseline/evidence_tool-route-02.json
7f7bb7bb6d56e34023c1aeba6c4f4e41eb092d17befbde05238eee6e97a69847  candidate/evidence_comp-rag-tool-05.json
c0846fced79d49fdeac49f2169bc7051029047bfe4370de27f08d1aa732b7a89  candidate/evidence_ctx-history-04.json
2f1e2f9130fb0c9c28b1537277a8a1abbc540bd651de2ccb8d844dd263ce32fc  candidate/evidence_kb-qa-01.json
d6bfa10c1c7857179584a0f0b649a50a8fe155402b0230f328949b0293d85229  candidate/evidence_param-guard-03.json
1713ceb1b6b84513495ccc21a484332466ee9f6aa1701502dcf3257e13359677  candidate/evidence_tool-route-02.json
3ef79ca81a92838e379585b4cd2dbff4273c9865359f53b184467afecba4e9c1  eval/ppc_summary.json
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
