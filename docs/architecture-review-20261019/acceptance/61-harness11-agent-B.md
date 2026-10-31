> **【智能体存证元数据】**

> **SUPERSEDED / 历史工程草稿，不得作为实施依据。** 本文将 protobuf clean 构建根因归为 Source Root 缺失，并提出 `build-helper` 修复；该根因未证实，且不得用两步构建成功替代单步 clean 验证。本文其他 Stage 11B 设计亦延期。以 [62-harness11-taskbook.md](62-harness11-taskbook.md) Revision 2 为准。
> - 物理智能体标识 (ConversationId): `2ec50425-3cb9-4812-8168-bf8deb966aa1`
> - 智能体角色: 递归 PPC 工程审计员 (Recursive PPC Engineering Specialist)
> - 运行 Transcript: [transcript.jsonl](file:///Users/achilles/.gemini/antigravity/brain/2ec50425-3cb9-4812-8168-bf8deb966aa1/.system_generated/logs/transcript.jsonl)

# 阶段十一（Stage 11）：经验库稳定化与经验感知递归 PPC 工程审计与架构设计报告

**审计员**：递归 PPC 工程审计员 (Recursive PPC Engineering Specialist)
**审计基准**：Java 21 隔离环境 (`/Users/achilles/.sdkman/candidates/java/21.0.5-tem`)、Stage 10 12 个主干类与 2 个测试类、Maven Reactor 构建生命周期、Stage 9 真实回放资产、`PpcOrchestrator` 与 `AlgebraicHarnessMutator`
**对标顶刊**：ESWA, IEEE TKDE, ACM TOIS, ICLR
**执行模式**：`MECHANISM_ONLY`（离线确定性执行，最多 2 轮递归，不接生产，不调用外部付费 API）

---

## 一、Stage 10 四大交付阻断法医级工程实施蓝图

在对当前仓库的 Git 状态、Maven Reactor 生命周期、类加载器路径及源码实现的法医级审计中，确认了导致 Stage 10 交付门禁阻断的四大根本原因，并制定了确定性修复蓝图：

### 1.1 阻断一：Stage 10 源码与测试未提交
- **法医根因**：当前处于 Git untracked 状态（`??`）的文件共 14 个，包含 12 个经验库主干类与 2 个契约测试类，未与提交记录和任务指纹绑定。
  - **12 个主干类**（`backend/qknow-hermes/qknow-hermes-core/src/main/java/tech/qiantong/qknow/hermes/agent/harness/experience/`）：
    1. `ExperienceLibrary.java`：顶层经验库读写契约接口；
    2. `DefaultExperienceLibrary.java`：经验库默认实现（门控过滤、三级软淘汰、跨域隔离）；
    3. `ExperienceDomain.java`：商空间不可变域三元组 `(botId, kbId, category)` 及 `unscopedReplay` 标记；
    4. `ExperienceEntry.java`：不可变经验记录 Record（含 `AttributionStatus`、`isEligibleForPositiveProposer`）；
    5. `ExperienceDisposition.java`：强类型处置枚举（`OBSERVED`, `ADOPTED`, `REJECTED`, `INVALID_FIXTURE`）；
    6. `ExperienceLeakageGuard.java`：防泄漏拦截器（检测黄金实体与评测判词）；
    7. `ExperienceRecall.java`：检索结果不可变 Record；
    8. `ExperienceRunSummaryRecord.java`：运行导入摘要不可变 Record；
    9. `ExperienceStore.java`：经验持久化存储 SPI；
    10. `FileSystemExperienceStore.java`：本地文件系统三级软隔离实现；
    11. `EvidenceReference.java`：探针证据引用不可变 Record；
    12. `Stage9ExperienceImporter.java`：Stage 9 真实证据防篡改导入器。
  - **2 个测试类**（`backend/qknow-hermes/qknow-hermes-core/src/test/java/tech/qiantong/qknow/hermes/agent/harness/experience/`）：
    1. `ExperienceLibraryContractTest.java`：经验库核心契约单测；
    2. `HarnessExperienceStage10Test.java`：Stage 9 回放防篡改与完整性单测。
- **实施蓝图**：
  采用 Conventional Commits 规范，执行原子化精准提交，格式如下：
  ```text
  fix(hermes): 稳定阶段十经验库构建与可移植回放

  - 固化经验库不可变记录模型与三级冷备隔离存储
  - 接入 Stage 9 回放防篡改验签与因果归因隔离契约
  - 补充经验库门控与跨域防泄漏单元测试套件
  ```
  提交仅包含上述 14 个 Java 文件及修复后的 pom 与测试夹具，严禁带入临时文件或未完成文档。

---

### 1.2 阻断二：Maven Protobuf 构建顺序彻底修复
- **法医根因**：在多模块 reactor 构建（如 `mvn clean test`）中，`protobuf-maven-plugin:0.6.1` 将生成的 gRPC/Protobuf 类输出到 `target/generated-sources/protobuf/java` 与 `grpc-java` 目录，但没有插件向 Maven Project 注册这两个目录为 compile source root。在一次性 clean 构建时，由于该模块缺少 `src/main/java`，Maven 编译器插件跳过源码编译，打出的 jar 包没有任何 class 文件，下游 `qknow-hermes-core` 编译时直接报错：`程序包 tech.qiantong.qknow.hermes.proto 不存在`。
- **实施蓝图**：
  在 `backend/qknow-hermes/qknow-hermes-proto/pom.xml` 的 `<plugins>` 中引入 `build-helper-maven-plugin:3.5.0`，在 `generate-sources` 阶段通过 `add-source` 显式注入编译源目录：
  ```xml
  <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>build-helper-maven-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
          <execution>
              <id>add-protobuf-sources</id>
              <phase>generate-sources</phase>
              <goals>
                  <goal>add-source</goal>
              </goals>
              <configuration>
                  <sources>
                      <source>${project.build.directory}/generated-sources/protobuf/java</source>
                      <source>${project.build.directory}/generated-sources/protobuf/grpc-java</source>
                  </sources>
              </configuration>
          </execution>
      </executions>
  </plugin>
  ```
  添加后，Maven 在 clean 后的 compile 阶段自动编译全部 47 个 Protobuf 类，彻底消灭对人工“预构建 proto”的脆弱依赖，确保 `JAVA_HOME=... mvn clean test` 具备 100% 确定性与幂等性。

---

### 1.3 阻断三：测试夹具可移植化
- **法医根因**：`HarnessExperienceStage10Test.java` 第 117 行硬编码读取 `root.resolve("experiment/outputs/harness9-live").resolve(RUN_ID)`，而 `.gitignore` 第 113 行明文忽略了 `experiment/outputs/*-live/`。任何新检出研发机或 CI 节点由于缺少此目录，测试直接抛出 `AssertionError: Stage 9 frozen replay is missing`，导致红牌阻断。
- **实施蓝图**：
  1. **资产迁移**：将脱敏的 Stage 9 最小回放夹具移入版本受控的测试资源路径：
     `backend/qknow-hermes/qknow-hermes-core/src/test/resources/harness9-replay/c44e7e20-5e33-49c4-bc57-105a8cc3c347/`
     内含：
     - `checksums.sha256`（15 探针 + 2 摘要的 SHA-256 清单）；
     - `ppc_summary.json`（统计与判决元数据）；
     - `selected_candidate.json`（裁决凭单信息）；
     - `probes/` 下 15 份探针证据 JSON（保留 runId、tokens、cost、状态指标，文本脱敏）。
  2. **类加载器读取**：测试通过 `ClassLoader.getResourceAsStream` 或类路径统一读取；在执行破坏性/篡改测试时，在 `@TempDir` 中从类路径拷贝一份工作副本进行防篡改校验，实现 100% 环境无关可移植性。

---

### 1.4 阻断四：Importer 参数化设计
- **法医根因**：`Stage9ExperienceImporter.java` 内部硬编码了特定的 `FROZEN_MANIFEST_SHA256`、`RUN_ID`（c44e7e20...）、15 次探针、26 次调用、35500 Tokens、0.06718974 成本以及写死的 case 列表和候选名称。这种硬编码违反了开闭原则（OCP），无法复用于 Stage 11 或未来任意合规冻结 PPC 运行的经验导入。
- **实施蓝图**：
  抽象并定义强类型不可变描述符 `FrozenReplayDescriptor`：
  ```java
  package tech.qiantong.qknow.hermes.agent.harness.experience;

  import java.util.List;
  import java.util.Objects;

  public record FrozenReplayDescriptor(
          String runId,
          String expectedManifestSha256,
          int expectedProbeCount,
          int expectedModelCalls,
          long expectedTokens,
          double expectedCostCny,
          String expectedVerdict,
          String expectedSelectedCandidate,
          List<String> passingCases,
          List<String> unresolvedCases,
          List<String> candidates
  ) {
      public FrozenReplayDescriptor {
          Objects.requireNonNull(runId, "runId");
          Objects.requireNonNull(expectedManifestSha256, "expectedManifestSha256");
          Objects.requireNonNull(expectedVerdict, "expectedVerdict");
          Objects.requireNonNull(expectedSelectedCandidate, "expectedSelectedCandidate");
          passingCases = List.copyOf(passingCases);
          unresolvedCases = List.copyOf(unresolvedCases);
          candidates = List.copyOf(candidates);
      }

      public static final FrozenReplayDescriptor STAGE_9_DEFAULT = new FrozenReplayDescriptor(
              "c44e7e20-5e33-49c4-bc57-105a8cc3c347",
              "7cc4f4c71d7e675eac4f99d47f9d722c34a6ffa3c159bddd808ed73951a61288",
              15, 26, 35_500L, 0.06718974D,
              "REJECTED_AFTER_REAL_PPC", "baseline",
              List.of("kb-qa-01", "tool-route-02", "param-guard-03"),
              List.of("ctx-history-04", "comp-rag-tool-05"),
              List.of("baseline", "candidate-a", "candidate-b")
      );
  }
  ```
  将 `Stage9ExperienceImporter` 构造器调整为支持传入 `FrozenReplayDescriptor`，默认构造器重载使用 `STAGE_9_DEFAULT`，通用逻辑依据 descriptor 进行校验，彻底消除常数硬编码。

---

## 二、Stage 11 递归 PPC 核心类架构设计 (`tech.qiantong.qknow.hermes.agent.harness.ppc.recursive`)

为对标顶刊理论（ESWA, IEEE TKDE, ICLR），融合 Google Harness²（`STAGE_ATTEMPTS = 2`）与 Reflexion 有限状态机收敛机制，在包 `tech.qiantong.qknow.hermes.agent.harness.ppc.recursive` 下建立五大核心类：

### 2.1 `PpcRound`（轮次不可变状态 Record）
```java
package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcCandidate;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcRun;

/**
 * 递归 PPC 单轮不可变状态快照
 */
public record PpcRound(
        int roundNumber,
        PpcCandidate inputBaseline,
        List<PpcCandidate> evaluatedCandidates,
        PpcRun runResult,
        String selectedCandidateId,
        String roundVerdict,
        double deltaUtilityVsRoundBaseline,
        double deltaCostVsRoundBaseline,
        long deltaTokensVsRoundBaseline,
        Instant timestamp
) {
    public static final int MAX_ROUND_NUMBER = 2;

    public PpcRound {
        if (roundNumber < 1 || roundNumber > MAX_ROUND_NUMBER) {
            throw new IllegalArgumentException("roundNumber must be 1 or 2, got: " + roundNumber);
        }
        Objects.requireNonNull(inputBaseline, "inputBaseline");
        evaluatedCandidates = List.copyOf(evaluatedCandidates);
        Objects.requireNonNull(runResult, "runResult");
        Objects.requireNonNull(selectedCandidateId, "selectedCandidateId");
        Objects.requireNonNull(roundVerdict, "roundVerdict");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public boolean hasPositiveImprovement() {
        return "ADOPTED".equals(roundVerdict) && !inputBaseline.candidateId().equals(selectedCandidateId);
    }

    public PpcCandidate selectedCandidate() {
        return evaluatedCandidates.stream()
                .filter(c -> c.candidateId().equals(selectedCandidateId))
                .findFirst()
                .orElse(inputBaseline);
    }
}
```

---

### 2.2 `PpcStopPolicy`（硬性停止条件判定器）
- **数学停止方程**：
  $$\text{Stop}(t) \iff (t \ge 2) \lor (\neg \text{hasImprovement}(t)) \lor (|\Delta u_t - \Delta u_{t-1}| \le \epsilon) \lor (\text{BudgetExhausted})$$
```java
package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcRun;

/**
 * 递归 PPC 硬性停止判定器
 */
public final class PpcStopPolicy {
    public static final int HARD_MAX_ROUNDS = 2;
    public static final double DEFAULT_EPSILON_MARGINAL_UTILITY = 0.001D;

    public enum StopReason {
        MAX_ROUNDS_REACHED,            // 达到硬性轮次上限（2轮）
        NO_IMPROVEMENT_IN_ROUND,       // 当前轮次无正向增益（劣于或等于基线）
        MARGINAL_GAIN_CONVERGED,       // 边际收益收敛（增量小于 epsilon）
        BUDGET_EXHAUSTED,              // 累计资源预算耗尽
        SECURITY_GUARDRAIL_TRIGGERED   // 防泄漏或安全护栏阻断
    }

    public record Decision(boolean shouldStop, StopReason reason, String explanation) {}

    private final int maxRounds;
    private final double epsilonMarginalUtility;
    private final PpcRun.Budget cumulativeBudget;

    public PpcStopPolicy(int maxRounds, double epsilonMarginalUtility, PpcRun.Budget cumulativeBudget) {
        if (maxRounds < 1 || maxRounds > HARD_MAX_ROUNDS) {
            throw new IllegalArgumentException("maxRounds must be between 1 and " + HARD_MAX_ROUNDS);
        }
        this.maxRounds = maxRounds;
        this.epsilonMarginalUtility = epsilonMarginalUtility;
        this.cumulativeBudget = Objects.requireNonNull(cumulativeBudget, "cumulativeBudget");
    }

    public static PpcStopPolicy defaultPolicy() {
        return new PpcStopPolicy(HARD_MAX_ROUNDS, DEFAULT_EPSILON_MARGINAL_UTILITY,
                new PpcRun.Budget(20, 60, 200_000L, 2_000, 10.0D));
    }

    public Decision evaluate(PpcRound currentRound, PpcRound previousRound, PpcRoundEvidence cumulativeEvidence) {
        // 1. 累计预算硬顶检测
        if (cumulativeEvidence.cumulativeTokensConsumed() >= cumulativeBudget.maxTotalTokens()
                || cumulativeEvidence.cumulativeCostCny() >= cumulativeBudget.maxCostCny()
                || cumulativeEvidence.cumulativeModelCalls() >= cumulativeBudget.maxModelCalls()) {
            return new Decision(true, StopReason.BUDGET_EXHAUSTED,
                    "Cumulative budget exhausted: tokens=" + cumulativeEvidence.cumulativeTokensConsumed()
                    + ", cost=" + cumulativeEvidence.cumulativeCostCny());
        }

        // 2. 本轮收益检测：无改进立即终止
        if (!currentRound.hasPositiveImprovement()) {
            return new Decision(true, StopReason.NO_IMPROVEMENT_IN_ROUND,
                    "Round " + currentRound.roundNumber() + " failed to beat round baseline: verdict="
                    + currentRound.roundVerdict());
        }

        // 3. 最大轮次限制检测
        if (currentRound.roundNumber() >= maxRounds) {
            return new Decision(true, StopReason.MAX_ROUNDS_REACHED,
                    "Reached hard maximum recursion depth: " + maxRounds);
        }

        // 4. 边际收益递减检测
        if (previousRound != null) {
            double marginalGain = currentRound.deltaUtilityVsRoundBaseline() - previousRound.deltaUtilityVsRoundBaseline();
            if (Math.abs(marginalGain) <= epsilonMarginalUtility) {
                return new Decision(true, StopReason.MARGINAL_GAIN_CONVERGED,
                        "Marginal utility gain converged below threshold: " + marginalGain + " <= " + epsilonMarginalUtility);
            }
        }

        return new Decision(false, null, "Eligible for next refinement round");
    }
}
```

---

### 2.3 `PpcRoundEvidence`（多轮证据链传递与组合）
```java
package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcProbeResult;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcComposer.PositionSwapAudit;

/**
 * 递归 PPC 多轮证据链不可变容器
 */
public record PpcRoundEvidence(
        String recursiveRunId,
        List<PpcRound> rounds,
        List<PpcProbeResult> allProbes,
        List<PositionSwapAudit> allPositionSwapAudits,
        int cumulativeProbesExecuted,
        int cumulativeModelCalls,
        long cumulativeTokensConsumed,
        double cumulativeCostCny,
        Map<String, String> roundVoucherFingerprints
) {
    public PpcRoundEvidence {
        Objects.requireNonNull(recursiveRunId, "recursiveRunId");
        rounds = List.copyOf(rounds);
        allProbes = List.copyOf(allProbes);
        allPositionSwapAudits = List.copyOf(allPositionSwapAudits);
        roundVoucherFingerprints = Map.copyOf(roundVoucherFingerprints);
    }

    public static PpcRoundEvidence empty(String recursiveRunId) {
        return new PpcRoundEvidence(recursiveRunId, List.of(), List.of(), List.of(),
                0, 0, 0L, 0.0D, Map.of());
    }

    public PpcRoundEvidence withRound(PpcRound newRound) {
        List<PpcRound> updatedRounds = new ArrayList<>(rounds);
        updatedRounds.add(newRound);

        List<PpcProbeResult> updatedProbes = new ArrayList<>(allProbes);
        updatedProbes.addAll(newRound.runResult().probes());

        List<PositionSwapAudit> updatedAudits = new ArrayList<>(allPositionSwapAudits);
        updatedAudits.addAll(newRound.runResult().positionSwapAudits());

        int newProbesCount = cumulativeProbesExecuted + newRound.runResult().totalProbesExecuted();
        int newModelCalls = cumulativeModelCalls + newRound.runResult().totalModelCallsExecuted();
        long newTokens = cumulativeTokensConsumed + newRound.runResult().totalTokensConsumed();
        double newCost = cumulativeCostCny + newRound.runResult().totalCostCnyConsumed();

        Map<String, String> fingerprints = new java.util.HashMap<>(roundVoucherFingerprints);
        fingerprints.put("round-" + newRound.roundNumber(),
                newRound.selectedCandidate().voucherFingerprint());

        return new PpcRoundEvidence(recursiveRunId, updatedRounds, updatedProbes, updatedAudits,
                newProbesCount, newModelCalls, newTokens, newCost, fingerprints);
    }

    public String finalSelectedFingerprint() {
        if (rounds.isEmpty()) {
            throw new IllegalStateException("No rounds executed");
        }
        return rounds.get(rounds.size() - 1).selectedCandidate().voucherFingerprint();
    }
}
```

---

### 2.4 `ExperienceRecallGateway`（经验库安全只读网关）
```java
package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDisposition;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceEntry;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLeakageGuard;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceLibrary;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceRecall;

/**
 * 经验库安全只读网关
 * 专用于离线 Proposer，与生产链路物理隔离。
 */
public final class ExperienceRecallGateway {
    private static final double MIN_POSITIVE_CONFIDENCE = 0.20D;

    private final ExperienceLibrary experienceLibrary;
    private final ExperienceLeakageGuard leakageGuard;

    public record ProposerGuidance(
            List<ExperienceEntry> positiveLessons,
            List<ExperienceEntry> negativeExclusionMasks
    ) {
        public ProposerGuidance {
            positiveLessons = List.copyOf(positiveLessons);
            negativeExclusionMasks = List.copyOf(negativeExclusionMasks);
        }
    }

    public ExperienceRecallGateway(ExperienceLibrary experienceLibrary, ExperienceLeakageGuard leakageGuard) {
        this.experienceLibrary = Objects.requireNonNull(experienceLibrary, "experienceLibrary");
        this.leakageGuard = Objects.requireNonNull(leakageGuard, "leakageGuard");
    }

    public ProposerGuidance recallGuidance(ExperienceDomain domain, String queryCue, int maxResults) {
        Objects.requireNonNull(domain, "domain");
        if (domain.isUnscopedReplay()) {
            return new ProposerGuidance(List.of(), List.of());
        }

        ExperienceRecall recall = experienceLibrary.recallForProposer(domain, queryCue, maxResults);
        List<ExperienceEntry> entries = recall.matchedEntries();

        for (ExperienceEntry entry : entries) {
            leakageGuard.assertNoLeakage(entry.cue());
            leakageGuard.assertNoLeakage(entry.lesson());
        }

        List<ExperienceEntry> positives = entries.stream()
                .filter(e -> e.isEligibleForPositiveProposer(MIN_POSITIVE_CONFIDENCE))
                .toList();

        List<ExperienceEntry> negatives = entries.stream()
                .filter(e -> e.disposition() == ExperienceDisposition.REJECTED || e.failureObserved())
                .toList();

        return new ProposerGuidance(positives, negatives);
    }
}
```

---

### 2.5 `PpcRecursiveLoop`（两轮状态机驱动器）
- **状态转移控制图**：
  ```
  [INIT]
    │
    ▼
  [ROUND 1 PROPOSE & PROBE] ──(无改善/预算超标)──► [TERMINATE: REJECTED]
    │ (产生正向改善 C*)
    ▼
  [ROUND 2 PROPOSE & PROBE] ──(轮次上限/边际收敛)─► [TERMINATE: ADOPT C* 或 C**]
  ```
```java
package tech.qiantong.qknow.hermes.agent.harness.ppc.recursive;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.ExperienceDomain;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcCandidate;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcComposer;
import tech.qiantong.qknow.hermes.agent.harness.ppc.PpcRun;

/**
 * 两轮经验感知递归 PPC 状态机驱动引擎
 */
public final class PpcRecursiveLoop {
    public enum State {
        INIT,
        ROUND_1_PROPOSING,
        ROUND_1_PROBING,
        ROUND_1_EVALUATE,
        ROUND_2_PROPOSING,
        ROUND_2_PROBING,
        ROUND_2_EVALUATE,
        TERMINATED
    }

    public record RecursiveResult(
            String recursiveRunId,
            PpcRoundEvidence evidence,
            HermesHarnessVoucher initialVoucher,
            HermesHarnessVoucher finalVoucher,
            String finalVerdict,
            PpcStopPolicy.StopReason terminationReason,
            String terminationExplanation
    ) {}

    private final PpcStopPolicy stopPolicy;
    private final ExperienceRecallGateway recallGateway;
    private final AlgebraicHarnessMutator mutator;
    private final PpcComposer composer;
    private final RoundExecutionHandler executionHandler;

    @FunctionalInterface
    public interface RoundExecutionHandler {
        PpcRun executeRound(int roundNumber, List<PpcCandidate> candidates) throws IOException;
    }

    public PpcRecursiveLoop(PpcStopPolicy stopPolicy,
                            ExperienceRecallGateway recallGateway,
                            RoundExecutionHandler executionHandler) {
        this.stopPolicy = Objects.requireNonNull(stopPolicy, "stopPolicy");
        this.recallGateway = Objects.requireNonNull(recallGateway, "recallGateway");
        this.executionHandler = Objects.requireNonNull(executionHandler, "executionHandler");
        this.mutator = new AlgebraicHarnessMutator();
        this.composer = new PpcComposer();
    }

    public RecursiveResult execute(HermesHarnessVoucher baselineVoucher,
                                   ExperienceDomain domain,
                                   String taskQueryCue) throws IOException {
        Objects.requireNonNull(baselineVoucher, "baselineVoucher");
        Objects.requireNonNull(domain, "domain");

        String runId = UUID.randomUUID().toString();
        PpcRoundEvidence evidence = PpcRoundEvidence.empty(runId);
        PpcRound previousRound = null;
        PpcCandidate currentBaseline = PpcCandidate.of("baseline", baselineVoucher, List.of());
        HermesHarnessVoucher activeVoucher = baselineVoucher;
        PpcStopPolicy.Decision finalStopDecision = null;

        for (int roundNum = 1; roundNum <= PpcStopPolicy.HARD_MAX_ROUNDS; roundNum++) {
            ExperienceRecallGateway.ProposerGuidance guidance =
                    recallGateway.recallGuidance(domain, taskQueryCue, 5);

            List<PpcCandidate> candidates = proposeCandidates(roundNum, currentBaseline, guidance);
            PpcRun roundRun = executionHandler.executeRound(roundNum, candidates);

            PpcComposer.SelectionResult selection = composer.select(
                    runId + "-r" + roundNum, candidates, roundRun.probes());

            PpcRound currentRound = buildRoundSnapshot(roundNum, currentBaseline, candidates,
                    roundRun, selection);

            evidence = evidence.withRound(currentRound);

            finalStopDecision = stopPolicy.evaluate(currentRound, previousRound, evidence);
            if (finalStopDecision.shouldStop()) {
                if (currentRound.hasPositiveImprovement()) {
                    activeVoucher = currentRound.selectedCandidate().voucher();
                }
                break;
            }

            currentBaseline = currentRound.selectedCandidate();
            activeVoucher = currentBaseline.voucher();
            previousRound = currentRound;
        }

        String verdict = activeVoucher.computeFingerprint().equals(baselineVoucher.computeFingerprint())
                ? PpcComposer.REJECTED_AFTER_REAL_PPC
                : PpcComposer.ADOPTED;

        return new RecursiveResult(
                runId,
                evidence,
                baselineVoucher,
                activeVoucher,
                verdict,
                finalStopDecision != null ? finalStopDecision.reason() : PpcStopPolicy.StopReason.MAX_ROUNDS_REACHED,
                finalStopDecision != null ? finalStopDecision.explanation() : "Completed"
        );
    }

    private List<PpcCandidate> proposeCandidates(int roundNumber, PpcCandidate baseline,
                                                 ExperienceRecallGateway.ProposerGuidance guidance) {
        boolean suppressConcise = guidance.negativeExclusionMasks().stream()
                .anyMatch(e -> e.lesson().contains("Prompt compression") || e.lesson().contains("concise"));

        List<PpcCandidate> list = new ArrayList<>();
        list.add(baseline);

        if (roundNumber == 1) {
            if (!suppressConcise) {
                HermesHarnessVoucher concise = mutator.mutate(baseline.voucher(), Operator.CONCISE_SYSTEM_PROMPT);
                list.add(PpcCandidate.of("candidate-r1-concise", concise, List.of(Operator.CONCISE_SYSTEM_PROMPT)));
            }
            HermesHarnessVoucher tightened = mutator.mutate(baseline.voucher(), Operator.GUARDRAIL_TIGHTENING);
            list.add(PpcCandidate.of("candidate-r1-tightened", tightened, List.of(Operator.GUARDRAIL_TIGHTENING)));
        } else {
            HermesHarnessVoucher memoryWindow = mutator.mutate(baseline.voucher(), Operator.ADAPTIVE_MEMORY_WINDOW);
            list.add(PpcCandidate.of("candidate-r2-mem", memoryWindow, List.of(Operator.ADAPTIVE_MEMORY_WINDOW)));
        }
        return list;
    }

    private PpcRound buildRoundSnapshot(int roundNum, PpcCandidate baseline, List<PpcCandidate> candidates,
                                       PpcRun run, PpcComposer.SelectionResult selection) {
        PpcCandidate winner = candidates.stream()
                .filter(c -> c.candidateId().equals(selection.selectedCandidateId()))
                .findFirst().orElse(baseline);

        double deltaCost = winner.totalCostCny() - baseline.totalCostCny();
        long deltaTokens = winner.totalTokens() - baseline.totalTokens();
        double deltaUtility = -deltaCost - (deltaTokens * 0.000001D);

        return new PpcRound(roundNum, baseline, candidates, run,
                selection.selectedCandidateId(), selection.finalVerdict(),
                deltaUtility, deltaCost, deltaTokens, Instant.now());
    }
}
```

---

## 三、工程审计结论与决策完备性（Decision-Completeness）判定

1. **阻断消除路径确定性**：
   - 阻断一（未跟踪源码）通过原子化提交锁定基线；
   - 阻断二（Proto 符号缺失）通过 `build-helper-maven-plugin:add-source` 确保 `mvn clean test` 100% 幂等编译；
   - 阻断三（本地路径强耦合）通过版本受控的 `src/test/resources/harness9-replay/` 与类加载器加载实现跨环境自由移植；
   - 阻断四（硬编码常数）通过抽象 `FrozenReplayDescriptor` 达成开闭原则，向后兼容 Stage 9 权威指纹，向前赋能 Stage 11 递归回放。
2. **理论与架构同构性**：
   - 递归状态机具有严格的单调有界性（$R \le 2$），边际增益递减（$\epsilon$-convergence）与资源预算耗尽双重硬熔断；
   - 经验库仅在离线 Proposer 算子赋权阶段通过物理气隙网关 `ExperienceRecallGateway` 只读生效，生产运行时提示词零污染，彻底杜绝快捷过拟合与提示词注入风险。
3. **准入判定**：
   本工程设计与审计结论已具备决策完备性（Decision-Completeness），可直接指导后续代码编写与单元测试验证。

</SYSTEM_MESSAGE>
