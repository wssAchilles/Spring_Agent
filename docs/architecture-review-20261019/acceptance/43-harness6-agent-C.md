# Agent C: 可复现性、成本与门禁审计报告 (Reproducibility, Cost & Gate Specialist)

> **智能体角色**：Agent C: 可复现性、成本与门禁审计员 (Reproducibility, Cost & Gate Specialist)  
> **物理 ConversationId**：`d66ad607-4d22-4bb4-ac88-f7d2c8c861cb`  
> **运行 Transcript 日志**：`file:///Users/achilles/.gemini/antigravity/brain/d66ad607-4d22-4bb4-ac88-f7d2c8c861cb/.system_generated/logs/transcript.jsonl`  
> **审计日期**：2026-10-30  
> **阶段目标**：为 Hermes Harness² 阶段六制定严苛的实验可复现性（Reproducibility）、不可变证据归档隔离（Append-Only Protection）、公网调用成本与时间预算硬熔断、零外网依赖门禁（Zero External Network Gate）以及任务书双 Hash 防混淆签名规范，确保阶段六实验科学严密、可审计、零污染。  
> **报告落盘目标路径**：`docs/architecture-review-20261019/acceptance/43-harness6-agent-C.md`  
> **准则遵循**：《AGENTS.md》科研工程总纲、Java 21 隔离环境铁律与独占简体中文规范

---

# 阶段六架构与工程风控审计报告：不可变资产保护、公网探针成本硬熔断、零外网依赖门禁与双 Hash 签名规范

**报告人**：可复现性、成本与门禁审计员 (Reproducibility, Cost & Gate Specialist)  
**审计目标**：结合阶段五验收审计暴露的核心缺陷（Prompt-Only 冒充 Full Agent、默认 `mvn test` 污染外网、测试覆盖已提交历史资产、任务书签名混淆），面向阶段六真实 RAG、真实工具回调与多轮会话历史能力的完整挂载，建立法医级资产隔离策略、公网成本与超时看门狗模型、零外网依赖的 Maven 构建门禁、正文与全文件双 Hash 提取规约以及严谨的立即停止条件（Stop Conditions）。

---

## 一、审计背景与阶段五遗留问题法医级鉴定

在阶段五真实生产轨道探针验收中，系统成功验证了底层 DeepSeek 官方 API (`deepseek-flash`) 的真实网络调度与不可变凭单生成机制，并在客观数据面前诚实得出了假设不成立（`hypothesisVerified = false`）的科学结论。然而，阶段五在工程执行与门禁防护层面遗留了四大破坏实验有效性与版本可信度的严重问题：

### 1.1 业务用例与真实能力执行脱节（Prompt-Only 冒充 Full Agent）
- **法医证据**：已提交的 `experiment/outputs/harness5/run-20261030-stage5-real/` 中，全部 10 份凭单记录均为：
  ```json
  "ragDocumentCount": 0,
  "ragTopScore": 0.0,
  "toolExecutions": []
  ```
- **根因分析**：`DefaultOfflineProbeExecutor.java` 在构造 `ChatRequest` 时，仅注入了 `question` 和系统提示词，未调用 `.addAllRagContexts()`、`.addAllToolMethodIds()` 或 `.addAllHistory()`。
- **后果**：本应测试 RAG 检索保真度与工具路由能力的用例退化为无外部上下文的纯文本问答，评估结论无法反映 Agent 真实的架构效能。

### 1.2 默认单元测试门禁 (`mvn test`) 被真实网络调用污染
- **法医证据**：`HermesHarnessStage5RealProbeTest.java` 位于常规 `src/test` 源码树下，且未打上任何 JUnit 5 `@Tag`，亦未在 Maven Surefire 中做 profile 隔离。
- **根因分析**：任何开发者在本地执行 `mvn test` 或 CI/CD 流水线无网络环境中运行时，该测试均会强行尝试解析 `HERMES_OPENAI_API_KEY` 并向公网 `api.deepseek.com` 发起网络请求，导致无网络环境直接报错挂起，或在有 Key 环境下产生不确定的外部网络耗时与账单开销。

### 1.3 测试执行覆写已提交的不可变历史资产（破坏可复现性）
- **法医证据**：在 `HermesHarnessStage5RealProbeTest.java` 行 61-62 中：
  ```java
  Path outputBaseDir = projectRoot.resolve("experiment/outputs/harness5").toAbsolutePath().normalize();
  String runId = "run-20261030-stage5-real";
  ```
- **根因分析**：输出目录被硬编码为已被 Git 跟踪的已提交历史目录 `experiment/outputs/harness5/run-20261030-stage5-real/`。
- **后果**：每次运行该测试，历史生成的 10 份证据凭单及 `ppc_summary.json` 均被重新写入覆盖，导致伴生校验文件 `checksums.sha256` 频繁失效，Git 工作区自动变脏（`git status` 出现大量 modified），彻底践踏了学术实验“资产只追加不覆盖（Append-Only）”的底线。

### 1.4 任务书哈希签名概念混淆
- **法医证据**：阶段五报告在多处将正文签名（`body_sha256: b4d42155...`）与全文件签名（`full_file_sha256: 9fa342de...`）混同使用，缺乏明确的提取边界与复算命令说明，给冻结审计带来歧义。

---

## 二、不可变资产保护与防覆盖策略 (Asset Immutability & Clean Workspace)

为确保科研实验的可追溯性、审计自洽性以及工程构建的纯洁性，阶段六必须实施严密的物理隔离与防覆盖策略。

### 2.1 历史资产永久归档与只读保护（Archived History Protection）
1. **历史归档冻结范围**：
   - `experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic/`
   - `experiment/outputs/harness5/run-20261030-stage5-real/`
   上述目录中的所有 JSON 凭单、汇总报告及 `checksums.sha256` 被正式标记为**永久只读历史存证**。
2. **写操作绝对禁止**：
   - 阶段六及未来所有测试用例、探针运行器（`OfflineProbeRunner`）代码中，严禁硬编码指向历史目录的写路径；
   - 探针运行器在初始化时，必须执行只读边界断言：若目标 `outputDir` 包含已存在的 `checksums.sha256` 且非显式指定的复现模式，必须拒绝写入并抛出 `IllegalStateException("ATTEMPTED_OVERWRITE_OF_ARCHIVED_ASSET")`。
3. **只读完整性验签门禁**：
   在 CI/CD 或门禁校验脚本中，强制执行历史存证校验：
   ```bash
   # 阶段五历史真实凭单只读校验
   (cd experiment/outputs/harness5/run-20261030-stage5-real && shasum -a 256 -c checksums.sha256)
   # 阶段四合成凭单只读校验
   (cd experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic && shasum -a 256 -c checksums.sha256)
   ```
   任一校验失败即表明历史证据被非法污染，构建立即中断。

### 2.2 阶段六动态隔离输出目录规划（Append-Only Live Path）
阶段六真实探针的运行输出必须与代码仓库严格解耦，推行两级隔离架构：

```text
experiment/outputs/
├── harness4-synthetic/         # [已跟踪，只读归档]
├── harness5/                   # [已跟踪，只读归档]
├── harness6-live/              # [未跟踪/被忽略，动态写入]
│   └── run-<timestamp>-stage6/ # 单次运行独立生成，以时间戳为唯一租约标识
│       ├── baseline/
│       ├── candidate/
│       ├── eval/
│       └── checksums.sha256
└── .gitignore                  # 显式忽略 experiment/outputs/*-live/
```

1. **目录命名规约**：
   - 探针输出根目录：`experiment/outputs/harness6-live/`；
   - 运行批次 ID：`runId = "run-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + "-stage6"；`
   - 每次探针执行独占一个全新的子目录，杜绝在同一路径下重复写入。
2. **Git 忽略与未跟踪策略**：
   在项目根目录 `.gitignore` 中显式追加规则：
   ```gitignore
   # === 实时探针与动态实验凭单隔离 ===
   experiment/outputs/*-live/
   experiment/outputs/**/temp*/
   target/harness*-evidence/
   ```
   使得任何探针运行产生的临时凭单均不会出现在 `git status` 中。
3. **正式发版资产固化流程（Manual Promotion Protocol）**：
   只有当阶段六实验全部通过、指标达成，且经人工审计批准后，方可由管理员执行受控脚本将最终通过的批次目录（如 `run-20261030-stage6-final`）从 `harness6-live` 拷贝至已跟踪的正式目录 `experiment/outputs/harness6/`，计算 `checksums.sha256`，并一次性提交入库。

### 2.3 测试后工作区干净度零容忍门禁（Clean Workspace Gate）
为彻底杜绝测试执行后 Git 工作区变脏的问题，确立以下硬性检查判据：
- **执行判据**：在运行任何单元测试、集成测试或离线探针之后，执行：
  ```bash
  git status --porcelain
  ```
- **门禁断言**：输出必须为绝对空字符串（Exit Code 0 且零变更行）。
- **拦截逻辑**：若检测到任何属于已跟踪文件的变动（特别是 `experiment/outputs/` 下的修改），判定为违反不可变资产保护纪律，立即判定测试套件失败。

---

## 三、公网 Live 探针成本模型与多维熔断预算 (Live Probe Budget & Watchdogs)

阶段六的基准测试用例将完整挂载 RAG 上下文、多工具 Schema 与历史对话。由于输入上下文扩大，必须重新精确推导 Token 理论上界，并制定硬件级看门狗与成本熔断器。

### 3.1 5 大能力基准用例输入/输出 Token 精确测算模型

在完整挂载真实能力后，每个用例的 Token 消耗模型如下：

| 用例 ID | 挂载能力标签 (`CapabilityTag`) | 输入要素与组成细节 | Prompt Tokens 上界 | Completion Tokens 上界 | 单次调用 Tokens 上界 |
|---|---|---|---|---|---|
| `kb-qa-01` | `RAG` | 问题 (50) + 企业中间件知识库切片 (450) + 系统提示词 (100) | 650 | 400 | 1,050 |
| `tool-route-02` | `TOOL` | 问题 (50) + 3 个金融工具 JSON Schema (380) + 系统提示词 (100) | 550 | 450 (工具决策与参数) | 1,000 |
| `param-guard-03` | `TOOL` | 问题 (50) + 日历工具 Schema (200) + 错误入参 + 提示词 (100) | 400 | 350 (纠错引导与降级) | 750 |
| `ctx-history-04` | `HISTORY` | 问题 (50) + 2 轮历史问答上下文 (450) + 系统提示词 (100) | 600 | 450 (消歧长回答) | 1,050 |
| `comp-rag-tool-05`| `COMPOSITE` (`RAG`+`TOOL`)| 问题 (50) + 架构切片 (400) + 导出工具 (250) + 提示词 (100) | 850 | 600 (4 列对比矩阵长文)| 1,450 |

#### 批次 Token 上界汇总：
- **单轮 5 个用例基线 (Baseline)**：总计约 $1050 + 1000 + 750 + 1050 + 1450 = \mathbf{5,300\text{ tokens}}$；
- **单轮 5 个用例候选 (Candidate)**：假定提示词与上下文精炼节省 10%~15%，约 $\mathbf{4,700\text{ tokens}}$；
- **单次完整 PPC 循环（10 次真实调用）基准上界**：$5,300 + 4,700 = \mathbf{10,000\text{ tokens}}$；
- **考虑长思维链（Thinking 模式开启）与公网重试的绝对安全峰值**：设定为 $\mathbf{30,000\text{ tokens}}$。

### 3.2 真实探针公网调用法币成本模型测算
依据 DeepSeek 官方标准活跃模型（`deepseek-flash`）计费规约：
- **输入 Tokens 计价**：1.00 元人民币 / 1,000,000 tokens（0.000001 元/token）；
- **输出 Tokens 计价**：2.00 元人民币 / 1,000,000 tokens（0.000002 元/token）；
- **缓存命中抵扣**：假设缓存命中率为 0% 进行最严苛的悲观保底测算。

#### 法币成本理论推导：
- **10 次调用理论常规成本**：
  - 输入：$6,000\text{ tokens} \times 1.00 \times 10^{-6} = 0.006\text{ CNY}$；
  - 输出：$4,000\text{ tokens} \times 2.00 \times 10^{-6} = 0.008\text{ CNY}$；
  - 理论总成本：$\mathbf{0.014\text{ CNY}}$（不足 1.5 分钱人民币）。
- **极端重试与长思维链波动峰值成本**（按 30,000 Tokens 计算）：
  - 输入：$18,000 \times 1.00 \times 10^{-6} = 0.018\text{ CNY}$；
  - 输出：$12,000 \times 2.00 \times 10^{-6} = 0.024\text{ CNY}$；
  - 极端总成本：$\mathbf{0.042\text{ CNY}}$（约 4 分钱人民币）。

### 3.3 阶段六硬件级多维熔断器 (`OfflineBudgetCircuitBreaker`) 升级参数
阶段四引入的 `OfflineBudgetCircuitBreaker` 表现稳健。在阶段六中，必须结合上述测算收紧硬性防御红线，防止异常死循环导致的资金与资源耗尽：

```java
public final class OfflineBudgetCircuitBreaker {
    // 阶段六严苛熔断红线
    public static final long MAX_BATCH_TOKENS = 50_000L;          // 单批次累计 Token 上限收紧为 50,000 (原 80,000)
    public static final double MAX_BATCH_COST_CNY = 0.10;         // 单批次累计法币成本上限收紧为 0.10 CNY (原 0.15)
    public static final long MAX_BATCH_DURATION_MS = 180_000L;    // 批次累计总耗时上限设为 180 秒 (3 分钟)
    ...
}
```

### 3.4 单任务看门狗（Watchdog）与指数退避重试规范
为防止公网调用因网络挂起导致进程永久僵死，建立两级看门狗与防重试风暴机制：
1. **底层套接字超时（Socket Read/Write Timeout）**：
   - `WebClient` / HTTP 客户端连接超时设定为 **5 秒**，读取超时设定为 **30 秒**。
2. **单用例端到端硬看门狗（Single-Task Watchdog）**：
   - 单个基准测试用例的整个端到端执行（含所有重试、工具回调与证据落盘）硬超时设定为 **60 秒**；
   - 超时后通过 `Mono.timeout(Duration.ofSeconds(60))` 强行中断，捕获 `TimeoutException` 后合成 `FAIL_SAFE` 凭单，严禁无限等待。
3. **严格受控重试策略（Controlled Retry Policy）**：
   - **最大重试次数**：严格限制为 $\le 2$ 次（即 1 次初试 + 最多 2 次重试，共 3 次尝试）；
   - **指数退避与抖动**：初始等待 1,000ms，乘数 2.0，最大退避 4,000ms，附加 $\pm 20\%$ 的随机抖动（Full Jitter），杜绝并发重试尖峰击穿网关；
   - **错误码白名单重试**：
     - **允许重试**：网络连通性中断、HTTP 502/503/504、`SocketTimeoutException`、`ConnectException`；
     - **严禁重试**：HTTP 400（非法请求体）、401/403（密钥鉴权失败）、429（配额超限，避免加重限流）、Token 熔断异常。一旦遭遇非白名单错误，立即短路失败。

---

## 四、“零外网依赖门禁”与 CI/CD 物理隔离设计 (Zero External Network Gate)

针对阶段五真实探针污染常规单元测试的严重设计漏洞，阶段六必须建立彻底的“物理双轨隔离”。

### 4.1 核心原则：默认 `mvn test` 零外部依赖
- **科学与工程底线**：任何在没有外部网络、没有公网 DNS 解析、没有任何环境变量（无 `HERMES_OPENAI_API_KEY`）的干净隔离机器上执行的默认构建命令：
  ```bash
  mvn clean test
  ```
  必须 **100% 绿色通过**。
- **违规零容忍**：若默认 `mvn test` 在离线状态下报错、因缺少 API Key 失败、或被抓包探测到发起了任何对外部 IP/域名的套接字连接，一律判定为违规并阻断发布。

### 4.2 Maven Surefire 插件物理隔离规约
在 `backend/qknow-hermes/qknow-hermes-core/pom.xml` 中配置 Surefire 插件，默认彻底排除所有真实公网探针测试类及打了 `@Tag("live")` 的用例：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
            <configuration>
                <!-- 默认排除 live 组与所有 Real/Live 探针测试类 -->
                <excludedGroups>live</excludedGroups>
                <excludes>
                    <exclude>**/*RealProbeTest.java</exclude>
                    <exclude>**/*LiveTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 4.3 独立 Maven Profile (`-Plive`) 激活机制
定义专属的 `live` profile，仅在显式传入 `-Plive` 参数时才激活真实探针测试：

```xml
<profiles>
    <profile>
        <id>live</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <configuration>
                        <!-- 激活 live 组并包含真实探针测试 -->
                        <excludedGroups combine.self="override"/>
                        <groups>live</groups>
                        <includes>
                            <include>**/HermesHarnessStage6RealProbeTest.java</include>
                        </includes>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 4.4 探针测试前置安全防御（Guardrail in Code）
在真实测试类 `HermesHarnessStage6RealProbeTest` 内部，使用 JUnit 5 `Assumptions` 实施防御性前置校验：

```java
@BeforeAll
static void verifyLiveEnvironmentPrerequisites() {
    String apiKey = DefaultOfflineProbeExecutor.resolveEffectiveApiKey();
    // 若无 Key 或为占位符，优雅跳过而非让测试挂死
    org.junit.jupiter.api.Assumptions.assumeTrue(
        apiKey != null && !apiKey.isBlank() && !apiKey.contains("placeholder"),
        "LIVE_PROBE_SKIPPED: 未检测到有效公网 DeepSeek API Key，安全跳过真实探针测试"
    );
}
```

---

## 五、任务书双 Hash 签名规约与防混淆操作指南 (Dual-Hash Specification)

为杜绝阶段五出现的签名表述混淆，正式确立任务书的双 Hash 规范体系。

### 5.1 双 Hash 概念剖析与科学设计动机

| 签名术语 | 英文标识 | 作用范围与计算对象 | 核心设计动机 |
|---|---|---|---|
| **正文签名** | `body_sha256` | 自章节起始行 `## 一、...` 到文件物理末尾（`EOF`）的全部内容 | **核心法律条款冻结**：锁定假设、输入输出、判定指标与消融矩阵。任务书头部的元数据（如时间戳、实施者、全文件哈希）更新不会引发自指哈希循环死锁。 |
| **整文件签名** | `full_file_sha256` | 磁盘上的完整物理文件逐字节计算（从第 1 行到最后 1 行） | **文件物理完整性存证**：作为独立伴生存证文件（`*.taskbook.md.sha256`）的标准内容，供 Git 与自动化脚本进行端到端不可篡改比对。 |

### 5.2 正文签名 (`body_sha256`) 提取与计算规范
- **正则提取锚点**：固定为任务书正文第一章的主标题：
  `## 一、阶段六唯一待验证核心假设与契约基准`
- **Shell 计算命令（跨平台兼容 macOS / Linux）**：
  ```bash
  sed -n '/## 一、阶段六唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md | shasum -a 256 | awk '{print $1}'
  ```
- **写入规范**：计算得出的 64 位十六进制散列值，必须写入任务书头部元数据区域：
  `> **正文冻结 SHA-256 签名**：<body_sha256>`
  `> **正文签名复算命令**：sed -n '/## 一、阶段六唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md | shasum -a 256`

### 5.3 整文件签名 (`full_file_sha256`) 存证与比对规范
- **存证文件命名规约**：必须与任务书同名并追加 `.sha256` 后缀：
  `docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md.sha256`
- **生成命令**：
  ```bash
  shasum -a 256 docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md > docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md.sha256
  ```
- **验证命令**：
  ```bash
  shasum -a 256 -c docs/architecture-review-20261019/acceptance/45-harness6-taskbook.md.sha256
  ```

### 5.4 任务书编写与冻结四步闭环操作流水线 (Standard Operating Procedure)
1. **第一步（起草正文）**：撰写 `45-harness6-taskbook.md` 的正文条款（从 `## 一、...` 至文末），明确假设、判据与消融矩阵；
2. **第二步（正文锁定）**：运行 `sed -n` 提取命令计算 `body_sha256`，将散列值和提取命令准确填入任务书头部元数据；
3. **第三步（全量存证）**：保存任务书，运行 `shasum -a 256 <file> > <file>.sha256` 生成伴生校验文件；
4. **第四步（双重自检）**：运行比对脚本，验证 `shasum -c` 成功输出 `OK`，验证 `body_sha256` 与头部标注绝对一致；完成 Git 提交后该文件永久锁定。

---

## 六、阶段六强制 BLOCKED 停止条件矩阵 (Stop Conditions & Circuit Breakers)

依据《AGENTS.md》门禁要求，制定阶段六执行过程中必须立即输出 `RESEARCH_GATE_BLOCKED` 或触发硬熔断退出的 6 大核心阻断场景：

| 阻断场景标识 | 触发条件定义 | 致命危害与拦截理由 | 立即执行动作与处理机制 |
|---|---|---|---|
| `BLOCKED: RAG_DEFICIENCY` | 标记为 `RAG` 的用例，执行结果中 `ragDocumentCount == 0` | 知识库未真实注入，退化为 Prompt-Only 假冒评测，实验无效 | 立即中断测试，判定用例执行非法，严禁计入采纳统计 |
| `BLOCKED: TOOL_DEFICIENCY` | 标记为 `TOOL` 的用例，执行结果中未挂载有效 `ToolCallback` | 工具回调缺失，模型无法生成有效工具调用决策 | 立即中断测试，判定用例执行非法，严禁伪造工具匹配 |
| `BLOCKED: HISTORY_DEFICIENCY` | 标记为 `HISTORY` 的用例，历史对话轮次未真实压入上下文 | 认知上下文缺失，多轮指代消歧能力无法验证 | 立即中断测试，断言失败退出 |
| `BLOCKED: DEFAULT_GATE_NETWORK_LEAK` | 在默认构建（无 `-Plive`）下检测到任何外网连接或 API Key 强依赖 | 破坏离线构建可复现性，CI/CD 挂死或污染公网账单 | Surefire 门禁硬失败，退出构建并报警 |
| `BLOCKED: ASSET_OVERWRITE_ATTEMPT` | 测试写入已归档的 `harness5/` 目录或测试后 `git status --porcelain` 非空 | 破坏历史凭单不可变性，版本库变脏 | 抛出 `SecurityException`，门禁终止，恢复历史快照 |
| `BLOCKED: BUDGET_BREAKER_TRIGGERED` | 单批次 Token > 50,000，或成本 > 0.10 元，或耗时 > 180s | 防止长思维链死循环、网络重试风暴与账单失控 | 硬件熔断器抛出 `IllegalStateException`，生成 `FAIL_SAFE` 凭单 |
| `BLOCKED: API_CONSECUTIVE_FAILURE` | 公网连续 2 次探针调用返回 5xx 服务端错误或网关超时 | 外部服务出现故障，避免无意义的重试等待与雪崩 | 终止当前批次，将剩余任务标记为 `FAIL_SAFE`，保留既有结果 |
| `BLOCKED: TASKBOOK_HASH_MISMATCH` | 任务书 `body_sha256` 或 `.sha256` 文件校验不一致 | 任务书遭到未经授权的篡改，破坏冻结契约法律效力 | 拒绝进入阶段六实施阶段，强制回滚至冻结版本 |

### 6.1 `FAIL_SAFE` 容灾凭单合成与回滚语义
当遭遇不可抗力（如公网网络瞬时抖动超过 2 次重试、或 DeepSeek API 网关返回 503）时：
- 系统严禁让整个测试进程崩溃抛出未受控异常，而是统一合成符合 `HarnessEvidenceRecord` 规范的 `FAIL_SAFE` 凭单；
- `FAIL_SAFE` 凭单中 `executionMode` 明确标注为 `FAIL_SAFE`，`finalOutputSummary` 记录详细错误码（如 `FAIL_SAFE [NET_503]: Gateway Timeout`）；
- 该凭单作为真实客观失败记录落盘，在盲审与统计中被诚实视为“未达成预期目标”，保障主干流水线的连续性与可观测性。

### 6.2 生产线上环境零污染与独立授权边界红线
- 阶段六产出的任何 PPC 结论、参数优化或提示词改进，仅代表在离线基准测试用例上的受控实验表现；
- **绝对隔离**：全局生产开关 `hermes.orchestration.enabled` 在任何离线测试过程中必须始终保持为默认的关闭或隔离状态；
- **独立授权红线**：未经用户的独立明确批准，严禁自动将阶段六的实验结果同步或部署至生产线上配置、数据库或微服务生产端口。

---

## 七、可直接执行的验证命令清单 (Executable Shell Verification Commands)

本章节提供供审计员与自动化流水线直接复制执行的完整 Shell 验证命令，覆盖阶段六的全生命周期门禁：

### 7.1 历史资产完整性复算验证命令
```bash
# 验证阶段五真实凭单只读未被篡改
cd /Users/achilles/Documents/许子祺/Agent/experiment/outputs/harness5/run-20261030-stage5-real && shasum -a 256 -c checksums.sha256

# 验证阶段四合成凭单只读未被篡改
cd /Users/achilles/Documents/许子祺/Agent/experiment/outputs/harness4-synthetic/run-20261030-stage4-synthetic && shasum -a 256 -c checksums.sha256
```

### 7.2 默认零外网依赖构建验证命令
```bash
# 切换至 Java 21 隔离环境，断开网络模拟离线状态并执行默认单元测试
cd /Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core
export JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem
unset HERMES_OPENAI_API_KEY
mvn clean test
```
*预期结果*：全部普通单测 100% 绿色通过，且无任何 RealProbe 测试被触发。

### 7.3 显式激活公网 Live 探针执行命令
```bash
# 显式激活 live profile 执行阶段六真实探针测试
cd /Users/achilles/Documents/许子祺/Agent/backend/qknow-hermes/qknow-hermes-core
export JAVA_HOME=/Users/achilles/.sdkman/candidates/java/21.0.5-tem
mvn test -Plive -Dtest=HermesHarnessStage6RealProbeTest
```
*预期结果*：探针完成 10 次真实调用，凭单输出至未跟踪目录 `experiment/outputs/harness6-live/`。

### 7.4 测试后 Git 工作区干净度检查命令
```bash
# 检查测试执行后是否有已跟踪文件被修改
cd /Users/achilles/Documents/许子祺/Agent
git status --porcelain
```
*预期结果*：输出严格为空字符串，没有任何已跟踪文件被修改或覆盖。

### 7.5 任务书双 Hash 自动化校验命令
```bash
# 1. 验证阶段五任务书整文件哈希
shasum -a 256 -c docs/architecture-review-20261019/acceptance/41-harness5-taskbook.md.sha256

# 2. 验证阶段五任务书正文哈希（预期为 b4d42155c2041f44e1b14e770a5e08832bd1933757a42b2dd5535fb9051f958e）
sed -n '/## 一、阶段五唯一待验证核心假设与契约基准/,$p' docs/architecture-review-20261019/acceptance/41-harness5-taskbook.md | shasum -a 256
```

---

## 八、审计员结论与推进建议

1. **准入判定**：阶段六针对阶段五遗留的 4 大缺陷给出了完备、量化、可直接执行的防御方案，工程风控与学术严密性门禁已完全就绪，准入条件达成；
2. **实施建议**：
   - 在任务书制定时严格贯彻双 Hash 规范；
   - 在 `qknow-hermes-core` 的 `pom.xml` 中迅速落地 Surefire 排除规则与 `-Plive` 专属 profile；
   - 将动态探针输出重定向至 `experiment/outputs/harness6-live/` 并在 `.gitignore` 中完成登记。
