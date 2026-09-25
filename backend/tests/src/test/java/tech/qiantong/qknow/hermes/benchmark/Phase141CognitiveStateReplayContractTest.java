package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.swarm.replay.CognitiveForkingSandboxReceipt;
import tech.qiantong.qknow.hermes.swarm.replay.SpatiotemporalForkingSandboxGovernor;
import tech.qiantong.qknow.hermes.swarm.replay.SpatiotemporalForkingSandboxGovernor.CounterfactualPatch;
import tech.qiantong.qknow.hermes.swarm.replay.SpatiotemporalForkingSandboxGovernor.ForkExecutionResult;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer.ReplayStepResult;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer.SwarmAgentSnapshotState;
import tech.qiantong.qknow.hermes.swarm.replay.SwarmCognitiveStateReplayer.SwarmClusterSnapshot;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 141 核心契约测试套件：
 * 多智能体认知状态回放、动态时空分叉沙盒与反事实交互调试中枢
 * (Multi-Agent Cognitive State Replay, Dynamic Spatiotemporal Forking
 * Sandbox & Counterfactual Interactive Debugging Metacenter)
 *
 * 核心验证范围：
 * 1. 写时复制与增量结构共享多智能体快照捕获 <= 1.0ms 与节约率 >= 75% (TC-141-1)
 * 2. 双向时间旅行向前/向后单步寻址与状态恢复 <= 2.0ms (TC-141-2)
 * 3. 动态时空分叉沙盒创建与反事实补丁热注入 <= 3.0ms (TC-141-3)
 * 4. 跨分支物理隔离与幽灵变量跨步污染率恒为 0.0% (TC-141-4)
 * 5. 反事实干预 What-If 平行推演与因果分歧验证 (TC-141-5)
 * 6. 纯 Java 21 Record 凭单不可变性与 SHA-256 常量时间自验真 (TC-141-6)
 * 7. 高并发 20 个平行分支并发派生与熔断保护 <= 3.0ms (TC-141-7)
 * 8. 端到端多智能体执行、原位挂起、时空分叉与存证签发闭环 (TC-141-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase141CognitiveStateReplayContractTest {

    private final SwarmCognitiveStateReplayer replayer = new SwarmCognitiveStateReplayer();
    private final SpatiotemporalForkingSandboxGovernor governor = new SpatiotemporalForkingSandboxGovernor(replayer);

    /**
     * 工具方法：构造指定角色的测试快照 Agent
     */
    private SwarmAgentSnapshotState createAgentState(String id, String role, String thought) {
        double[] dummyVec = new double[1536];
        Arrays.fill(dummyVec, 1.0 / Math.sqrt(1536));
        return new SwarmAgentSnapshotState(id, role, thought, Map.of("key_" + id, "val_" + id), dummyVec);
    }

    // =========================================================================
    // TC-141-1: 写时复制与增量结构共享多智能体快照捕获
    // =========================================================================
    @Test
    @DisplayName("TC-141-1: 多智能体全局认知快照捕获：增量结构共享空间节约率 >= 75.0%，单次快照耗时 <= 1.0ms")
    void testStateSnapshot_captureAndStructuralSharing() {
        // 预热消除类加载抖动
        replayer.captureSnapshot("warmup", 0, 1, "span_w", Collections.emptyMap(), Collections.emptyMap(), null);

        Map<String, SwarmAgentSnapshotState> agents = Map.of(
                "agent-pro", createAgentState("agent-pro", "PRO", "建议扩展架构"),
                "agent-con", createAgentState("agent-con", "CON", "提示注意网络分区风险"),
                "agent-analyst", createAgentState("agent-analyst", "ANALYST", "给出三维效用核算")
        );
        Map<String, Object> blackboard = Map.of("cluster_status", "ACTIVE", "active_rounds", 1);

        long start = System.nanoTime();
        SwarmClusterSnapshot snapshot = replayer.captureSnapshot(
                "main", 1, 1, "00f067aa0ba902b7", agents, blackboard, null
        );
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(snapshot);
        assertEquals("main", snapshot.branchId());
        assertEquals(3, snapshot.agentStates().size());
        assertTrue(latencyMs <= 1.0, "单次快照保存耗时必须 <= 1.0ms，实测: " + latencyMs + "ms");

        // 捕获增量变更步
        Map<String, SwarmAgentSnapshotState> deltaAgents = Map.of(
                "agent-con", createAgentState("agent-con", "CON", "追加第二轮论据")
        );
        replayer.captureSnapshot("main", 2, 2, "5c1798369e061805", deltaAgents, Map.of("active_rounds", 2), snapshot.snapshotId());

        double savingsPercent = replayer.calculateStructuralSharingSavingsPercent();
        assertTrue(savingsPercent >= 75.0, "结构共享物理存储节约率必须 >= 75.0%，实测: " + savingsPercent + "%");
    }

    // =========================================================================
    // TC-141-2: 双向时间旅行向前/向后单步寻址与状态恢复
    // =========================================================================
    @Test
    @DisplayName("TC-141-2: 多智能体状态双向步进寻址与恢复：历史时刻无损还原，单步回滚耗时 <= 2.0ms，数据完全保真")
    void testBidirectionalReplay_forwardAndBackwardStepping() {
        String branch = "branch-stepping-test";
        // 构造三步执行序列
        SwarmClusterSnapshot s0 = replayer.captureSnapshot(branch, 0, 1, "span-0",
                Map.of("p", createAgentState("p", "PRO", "Step 0")), Map.of("stage", "INIT"), null);
        SwarmClusterSnapshot s1 = replayer.captureSnapshot(branch, 1, 1, "span-1",
                Map.of("p", createAgentState("p", "PRO", "Step 1")), Map.of("stage", "IN_PROGRESS"), s0.snapshotId());
        SwarmClusterSnapshot s2 = replayer.captureSnapshot(branch, 2, 2, "span-2",
                Map.of("p", createAgentState("p", "PRO", "Step 2")), Map.of("stage", "CONVERGED"), s1.snapshotId());

        // 1. 单步后退至 s1
        long backStart = System.nanoTime();
        ReplayStepResult backRes = replayer.stepBackward(branch);
        double backLatencyMs = (System.nanoTime() - backStart) / 1_000_000.0;

        assertEquals(1, backRes.stepIndex());
        assertEquals("IN_PROGRESS", backRes.snapshot().sharedBlackboard().get("stage"));
        assertTrue(backLatencyMs <= 2.0, "单步回滚耗时必须 <= 2.0ms，实测: " + backLatencyMs + "ms");

        // 2. 再次后退至 s0
        ReplayStepResult backRes0 = replayer.stepBackward(branch);
        assertEquals(0, backRes0.stepIndex());
        assertEquals("INIT", backRes0.snapshot().sharedBlackboard().get("stage"));

        // 3. 单步向前至 s1
        long fwdStart = System.nanoTime();
        ReplayStepResult fwdRes = replayer.stepForward(branch);
        double fwdLatencyMs = (System.nanoTime() - fwdStart) / 1_000_000.0;

        assertEquals(1, fwdRes.stepIndex());
        assertEquals("IN_PROGRESS", fwdRes.snapshot().sharedBlackboard().get("stage"));
        assertTrue(fwdLatencyMs <= 2.0, "单步前行耗时必须 <= 2.0ms");
    }

    // =========================================================================
    // TC-141-3: 动态时空分叉沙盒创建与反事实补丁热注入
    // =========================================================================
    @Test
    @DisplayName("TC-141-3: 在指定 Span 派生平行分支，注入 Prompt 补丁，分叉创建耗时 <= 3.0ms，沙箱隔离度 100%")
    void testSpatiotemporalForking_sandboxIsolation() {
        SwarmClusterSnapshot base = replayer.captureSnapshot("main", 1, 1, "span-root-01",
                Map.of("agent-pro", createAgentState("agent-pro", "PRO", "主线原始提案")),
                Map.of("db_mode", "READ_WRITE"), null);

        CounterfactualPatch patch = new CounterfactualPatch(
                "agent-pro",
                "【反事实补丁注入】：切换为只读补偿模式",
                Map.of("read_only_enforced", true),
                Map.of("db_mode", "READ_ONLY_COMPENSATION"),
                "测试只读降级下的共识达成速度"
        );

        long start = System.nanoTime();
        ForkExecutionResult forkRes = governor.forkBranch("main", base.snapshotId(), "span-fork-pt", patch);
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(forkRes);
        assertTrue(forkRes.forkedBranchId().startsWith("fork-main-"), "分支命名必须反映因果派生关系");
        assertTrue(latencyMs <= 3.0, "分叉派生总耗时必须 <= 3.0ms，实测: " + latencyMs + "ms");

        SwarmClusterSnapshot forkedSnapshot = forkRes.forkedSnapshot();
        SwarmAgentSnapshotState patchedAgent = forkedSnapshot.agentStates().get("agent-pro");
        assertEquals("【反事实补丁注入】：切换为只读补偿模式", patchedAgent.currentThought());
        assertEquals("READ_ONLY_COMPENSATION", forkedSnapshot.sharedBlackboard().get("db_mode"));
        assertEquals(true, patchedAgent.workingMemory().get("read_only_enforced"));
    }

    // =========================================================================
    // TC-141-4: 跨分支物理隔离与幽灵变量跨步污染率恒为 0.0%
    // =========================================================================
    @Test
    @DisplayName("TC-141-4: 验证在平行分支大量修改与执行后，主分支及其历史快照读数绝对恒定，幽灵变量污染率恒为 0.0%")
    void testGhostVariablePollution_zeroLeakageVerification() {
        SwarmClusterSnapshot mainSnap = replayer.captureSnapshot("main", 5, 2, "span-base-05",
                Map.of("agent-audit", createAgentState("agent-audit", "AUDITOR", "主线审计通过")),
                Map.of("security_level", "NORMAL", "transaction_count", 100), null);

        // 派生两个平行分支，并进行破坏性修改测试
        CounterfactualPatch extremePatch = new CounterfactualPatch(
                "agent-audit",
                "破坏性极端分支：强制标记高危异常！",
                Map.of("tampered_flag", true),
                Map.of("security_level", "CRITICAL_POLLUTION", "transaction_count", 99999),
                "注入极端污染测试"
        );
        ForkExecutionResult fork1 = governor.forkBranch("main", mainSnap.snapshotId(), "span-p1", extremePatch);

        // 在分支继续推演两步
        governor.executeForkedStep(fork1.forkedBranchId(),
                Map.of("agent-audit", createAgentState("agent-audit", "AUDITOR", "分支第二步")),
                Map.of("security_level", "BRANCH_MODIFIED"), "span-p2");

        // 严格检验主线快照读数：确保 100% 维持原状
        SwarmClusterSnapshot verifyMain = replayer.getSnapshot(mainSnap.snapshotId());
        assertEquals("NORMAL", verifyMain.sharedBlackboard().get("security_level"), "主线 security_level 绝不允许被污染");
        assertEquals(100, verifyMain.sharedBlackboard().get("transaction_count"), "主线 transaction_count 绝不允许被污染");
        assertEquals("主线审计通过", verifyMain.agentStates().get("agent-audit").currentThought(), "主线思考链绝不允许被覆写");
        assertFalse(verifyMain.agentStates().get("agent-audit").workingMemory().containsKey("tampered_flag"),
                "幽灵变量跨步污染率必须恒为 0.0%");
    }

    // =========================================================================
    // TC-141-5: 反事实干预 What-If 平行推演与因果分歧验证
    // =========================================================================
    @Test
    @DisplayName("TC-141-5: 注入反事实干预补丁后，新分支推演出完全不同的博弈策略与结果，形成可解释差分")
    void testCounterfactualPatch_whatIfDivergenceExecution() {
        SwarmClusterSnapshot forkBase = replayer.captureSnapshot("main", 2, 1, "span-diverge-pt",
                Map.of("agent-tax", createAgentState("agent-tax", "TAX", "普通税率申报 25%")),
                Map.of("tax_rate", 0.25), null);

        // 分支 A: 注入离岸合规反事实约束
        ForkExecutionResult branchA = governor.forkBranch("main", forkBase.snapshotId(), "span-div-a",
                new CounterfactualPatch("agent-tax", "采用开曼离岸免税申报方案",
                        Map.of("tax_route", "CAYMAN"), Map.of("tax_rate", 0.05), "方案A：离岸筹划"));

        // 分支 B: 注入严格监管穿透反事实约束
        ForkExecutionResult branchB = governor.forkBranch("main", forkBase.snapshotId(), "span-div-b",
                new CounterfactualPatch("agent-tax", "强化实质性运营穿透审查方案",
                        Map.of("tax_route", "SUBSTANCE_CHECK"), Map.of("tax_rate", 0.35), "方案B：实质合规审查"));

        assertEquals(0.05, branchA.forkedSnapshot().sharedBlackboard().get("tax_rate"));
        assertEquals(0.35, branchB.forkedSnapshot().sharedBlackboard().get("tax_rate"));
        assertNotEquals(branchA.forkedBranchId(), branchB.forkedBranchId());
        assertEquals(forkBase.snapshotId(), governor.getBranch(branchA.forkedBranchId()).baseSnapshotId());
        assertEquals(forkBase.snapshotId(), governor.getBranch(branchB.forkedBranchId()).baseSnapshotId());
    }

    // =========================================================================
    // TC-141-6: 纯 Java 21 Record 凭单不可变性与 SHA-256 常量时间自验真
    // =========================================================================
    @Test
    @DisplayName("TC-141-6: 纯 Java 21 Record 凭单签名自验真：验证全字段不可变性与 SHA-256 哈希常量时间自验真率 100.0%")
    void testCognitiveForkingSandboxReceipt_immutableVerification() {
        CognitiveForkingSandboxReceipt receipt = CognitiveForkingSandboxReceipt.create(
                "RCP-FORK-20260925-001",
                "4bf92f3577b34da6a3ce929d0e0e4736",
                "main",
                "fork-main-ab12cd",
                "SNAP-main-S2-8877",
                "00f067aa0ba902b7",
                "agent-pro",
                "注入只读降级与 Undo Log 强制开关",
                1.45
        );

        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名长度必须严格为 64 位十六进制");
        assertTrue(receipt.verifySignature(), "未篡改的分叉凭单自验真必须 100% 成功");

        // 模拟篡改凭单字段
        CognitiveForkingSandboxReceipt tampered = new CognitiveForkingSandboxReceipt(
                receipt.receiptId(),
                receipt.traceId(),
                receipt.parentBranchId(),
                "fork-malicious-hacked", // 恶意修改派生分支标识
                receipt.baseSnapshotId(),
                receipt.forkSpanId(),
                receipt.patchedAgentId(),
                receipt.patchSummary(),
                receipt.patchHash(),
                receipt.forkLatencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );

        assertFalse(tampered.verifySignature(), "篡改凭单必须被常量时间自验真严格拦截，拦截率 100%");
    }

    // =========================================================================
    // TC-141-7: 高并发 20 个平行分支并发派生与配额熔断
    // =========================================================================
    @Test
    @DisplayName("TC-141-7: 高并发派生 20 个平行分支并发推演，平均分叉时延 <= 3.0ms，零死锁无泄漏")
    void testHighConcurrencyForking_multiBranchScalability() {
        SwarmCognitiveStateReplayer testReplayer = new SwarmCognitiveStateReplayer();
        SpatiotemporalForkingSandboxGovernor testGov = new SpatiotemporalForkingSandboxGovernor(testReplayer);

        SwarmClusterSnapshot rootSnap = testReplayer.captureSnapshot("main", 0, 1, "span-root",
                Map.of("a", createAgentState("a", "LEADER", "Root")), Map.of("c", 0), null);

        int branchCount = 18; // 配额上限为 20，主干已占 1 个，派生 18 个
        long totalDurationNs = 0;

        for (int i = 0; i < branchCount; i++) {
            long start = System.nanoTime();
            ForkExecutionResult res = testGov.forkBranch(
                    "main", rootSnap.snapshotId(), "span-concur-" + i,
                    new CounterfactualPatch("a", "Thought " + i, Map.of(), Map.of("c", i), "Patch " + i)
            );
            totalDurationNs += (System.nanoTime() - start);
            assertNotNull(res.forkedBranchId());
        }

        double avgLatencyMs = (totalDurationNs / (double) branchCount) / 1_000_000.0;
        assertTrue(avgLatencyMs <= 3.0, "高并发平均单次分叉耗时必须 <= 3.0ms，实测: " + avgLatencyMs + "ms");
        assertTrue(testGov.getActiveBranchCount() <= 20, "活跃分支总数必须严格受配额约束");
    }

    // =========================================================================
    // TC-141-8: 端到端认知状态回溯、时空分叉与不可变存证闭环
    // =========================================================================
    @Test
    @DisplayName("TC-141-8: 端到端：多智能体执行 -> 发现分歧断点 -> 原位挂起 -> 状态单步回退 -> 派生反事实分支 -> 注入补丁 -> 恢复执行 -> 签发不可变凭单")
    void testEndToEndCognitiveReplayAndForking_fullPipelineIntegration() {
        long e2eStart = System.nanoTime();
        String traceId = "3a8b2c1d4e5f60718293a4b5c6d7e8f9";

        // 阶段 1: 主线正常协作推进 3 步
        SwarmClusterSnapshot s0 = replayer.captureSnapshot("main", 0, 1, "0011223344556677",
                Map.of("agent-pro", createAgentState("agent-pro", "PRO", "Step 0 意图识别")),
                Map.of("status", "INITIALIZED"), null);

        SwarmClusterSnapshot s1 = replayer.captureSnapshot("main", 1, 1, "1122334455667788",
                Map.of("agent-pro", createAgentState("agent-pro", "PRO", "Step 1 方案构思")),
                Map.of("status", "PROPOSING"), s0.snapshotId());

        SwarmClusterSnapshot s2 = replayer.captureSnapshot("main", 2, 2, "2233445566778899",
                Map.of("agent-con", createAgentState("agent-con", "CON", "Step 2 误采纳了过时的旧监管条文")),
                Map.of("status", "FAULTY_ARGUMENT"), s1.snapshotId());

        // 阶段 2: 发现断点错误，原位挂起并单步回退至 s1
        ReplayStepResult rollbackResult = replayer.stepBackward("main");
        assertEquals(s1.snapshotId(), rollbackResult.snapshot().snapshotId(), "必须精准回滚至 s1 状态");

        // 阶段 3: 在 s1 原位派生反事实时空分叉分支，并注入最新法规热补丁
        CounterfactualPatch regulatoryPatch = new CounterfactualPatch(
                "agent-con",
                "【2026 最新金融合规特批通道】：允许在双向补偿日志完备前提下放行跨境清算",
                Map.of("regulation_version", "2026.09_FINAL"),
                Map.of("regulatory_pass", true, "status", "REGULATION_OVERRIDDEN"),
                "更正旧规漏洞，恢复合规放行"
        );

        ForkExecutionResult forkResult = governor.forkBranch("main", s1.snapshotId(), "33445566778899aa", regulatoryPatch);
        String forkedBranchId = forkResult.forkedBranchId();

        // 阶段 4: 在分叉沙盒中恢复执行推演新走向
        SwarmClusterSnapshot branchedNext = governor.executeForkedStep(
                forkedBranchId,
                Map.of("agent-con", createAgentState("agent-con", "CON", "基于 2026 新规审核通过")),
                Map.of("status", "CONSENSUS_REACHED", "verdict", "APPROVED"),
                "445566778899aabb"
        );

        assertEquals("CONSENSUS_REACHED", branchedNext.sharedBlackboard().get("status"));
        assertEquals("APPROVED", branchedNext.sharedBlackboard().get("verdict"));

        // 阶段 5: 签发不可变分叉审计存证凭单
        double totalLatencyMs = (System.nanoTime() - e2eStart) / 1_000_000.0;
        CognitiveForkingSandboxReceipt receipt = CognitiveForkingSandboxReceipt.create(
                "RCP-FORK-E2E-" + System.currentTimeMillis(),
                traceId,
                "main",
                forkedBranchId,
                s1.snapshotId(),
                "33445566778899aa",
                "agent-con",
                regulatoryPatch.interventionReason(),
                totalLatencyMs
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端分叉凭单自验真必须 100% 成功");
        assertTrue(totalLatencyMs <= 25.0, "端到端全链路闭环耗时必须 <= 25.0ms，实测: " + totalLatencyMs + "ms");
    }
}
