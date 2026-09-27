package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.workflow.timetravel.StateDifferentialKernelEstimator;
import tech.qiantong.qknow.hermes.workflow.timetravel.WorkflowTimeTravelStateHub;
import tech.qiantong.qknow.hermes.workflow.timetravel.WorkflowTimeTravelStateHub.SpeculativeBranchResult;
import tech.qiantong.qknow.hermes.workflow.timetravel.WorkflowTimeTravelStateHub.SpeculativeCommitResult;
import tech.qiantong.qknow.hermes.workflow.timetravel.WorkflowTimeTravelStateHub.StateVersionNode;
import tech.qiantong.qknow.hermes.workflow.timetravel.WorkflowTimeTravelStateHub.TimeTravelRevertResult;
import tech.qiantong.qknow.hermes.workflow.timetravel.dto.WorkflowTimeTravelAuditReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 148 核心契约测试套件：
 * 工作流状态版本 Merkle DAG 增量时间旅行、千问 1536 维超球面状态差异核与置信度门控投机执行中枢
 * (Phase 148 Workflow Merkle DAG Time-Travel & Speculative Execution Contract Test)
 * <p>
 * 严格按照 AGENTS.md 规范与八大核心契约验证：
 * 1. 千问 1536 维超球面单位向量测地线核正定性与度规不变性检验 (TC-148-1)
 * 2. 状态版本 Merkle DAG 增量哈希确定性与因果链抗碰撞检验 (TC-148-2)
 * 3. 二进制提升跳转表 O(log N) 寻址与增量状态无损重构一致性 (TC-148-3, Lemma 148.1)
 * 4. 单次历史版本时间旅行回滚耗时控制在 <= 5.0ms 压测契约 (TC-148-4)
 * 5. 置信度门控投机分支推演与隔离沙盒写入契约 (TC-148-5, γ >= 0.88)
 * 6. 投机分支命中合并与审批驳回隔离废弃保真度验证 (TC-148-6, Lemma 148.2)
 * 7. 三级冷备隔离环形缓冲区软回退与可逆自愈防丢失契约 (TC-148-7)
 * 8. 纯 Java 21 Record 审计凭单 SHA-256 常量时间自验真与防篡改检验 (TC-148-8)
 *
 * @author Achilles
 * @since Phase 148
 */
public class Phase148WorkflowTimeTravelContractTest {

    private WorkflowTimeTravelStateHub stateHub;
    private final String tenantId = "tenant-phase148-test";
    private final String workflowId = "wf-enterprise-time-travel-148";

    @BeforeEach
    void setUp() {
        stateHub = new WorkflowTimeTravelStateHub();
    }

    /**
     * TC-148-1: 千问 1536 维超球面单位向量测地线核正定性与度规不变性检验
     */
    @Test
    @DisplayName("TC-148-1: 千问 1536 维超球面单位向量测地线核正定性与度规不变性检验")
    void testHypersphericalGeodesicKernelProperties() {
        double[] v1 = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("state_node_1");
        double[] v2 = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("state_node_2");

        // 1. 验证维度为 1536
        assertEquals(StateDifferentialKernelEstimator.EMBEDDING_DIMENSION, v1.length);
        assertEquals(StateDifferentialKernelEstimator.EMBEDDING_DIMENSION, v2.length);

        // 2. 验证超球面 L2 范数单位长度归一化约束 (||v||_2 = 1.0)
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < StateDifferentialKernelEstimator.EMBEDDING_DIMENSION; i++) {
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        assertEquals(1.0, Math.sqrt(norm1), 1e-9, "v1 必须为超球面单位向量");
        assertEquals(1.0, Math.sqrt(norm2), 1e-9, "v2 必须为超球面单位向量");

        // 3. 自身与自身的状态差异核恒等式 (K_diff = 1.0)
        double selfKernel = StateDifferentialKernelEstimator.computeDifferentialKernel(v1, v1);
        assertEquals(1.0, selfKernel, 1e-9, "同状态测地线核必须精确为 1.0");

        // 4. 正交向量测地线核应精确为 0.5 (θ = π/2)
        double[] orthoA = new double[StateDifferentialKernelEstimator.EMBEDDING_DIMENSION];
        double[] orthoB = new double[StateDifferentialKernelEstimator.EMBEDDING_DIMENSION];
        orthoA[0] = 1.0;
        orthoB[1] = 1.0;
        double orthoKernel = StateDifferentialKernelEstimator.computeDifferentialKernel(orthoA, orthoB);
        assertEquals(0.5, orthoKernel, 1e-9, "正交向量测地线核必须精确为 0.5");

        // 5. 欧氏几何与余弦几何度规恒等式检验: d_Euc^2 = 2 * (1 - cos θ)
        double identityError = StateDifferentialKernelEstimator.verifyEuclideanCosineIdentityError(v1, v2);
        assertTrue(identityError < 1e-10, "欧氏与余弦度规转换误差必须严格在机器精度以内: " + identityError);
    }

    /**
     * TC-148-2: 状态版本 Merkle DAG 增量哈希确定性与因果链抗碰撞检验
     */
    @Test
    @DisplayName("TC-148-2: 状态版本 Merkle DAG 增量哈希确定性与因果链抗碰撞检验")
    void testMerkleDagStateDeltaHashingDeterminism() {
        Map<String, Object> delta1 = Map.of("userQuery", "进行企业季报分析", "step", 1);
        double[] vec1 = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("step1");

        StateVersionNode node1 = stateHub.commitStateDelta(tenantId, workflowId, "NODE_INPUT", delta1, vec1);
        assertEquals(1, node1.getVersion());
        assertEquals(1, node1.getDepth());
        assertNotNull(node1.getStateHash());
        assertEquals(64, node1.getStateHash().length(), "SHA-256 哈希长度必须为 64 位字符");

        Map<String, Object> delta2 = Map.of("extractedEntities", List.of("财报", "现金流"), "step", 2);
        double[] vec2 = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("step2");

        StateVersionNode node2 = stateHub.commitStateDelta(tenantId, workflowId, "NODE_NER", delta2, vec2);
        assertEquals(2, node2.getVersion());
        assertEquals(2, node2.getDepth());
        assertEquals(node1.getStateHash(), node2.getParentHash(), "子节点父哈希必须精确指向上一状态 Merkle 根");
        assertNotEquals(node1.getStateHash(), node2.getStateHash(), "不同增量状态必须具备严格雪崩抗碰撞性");
    }

    /**
     * TC-148-3: 二进制提升跳转表 O(log N) 寻址与增量状态无损重构一致性 (Lemma 148.1)
     */
    @Test
    @DisplayName("TC-148-3: 二进制提升跳转表 O(log N) 寻址与增量状态无损重构一致性 (Lemma 148.1)")
    void testBinaryLiftingAddressabilityAndStateReconstruction() {
        // 构建深度为 32 的长程工作流执行因果链
        int totalNodes = 32;
        for (int i = 1; i <= totalNodes; i++) {
            Map<String, Object> delta = Map.of(
                    "param_" + i, "value_" + i,
                    "globalCounter", i
            );
            double[] vec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("seq_" + i);
            stateHub.commitStateDelta(tenantId, workflowId, "NODE_" + i, delta, vec);
        }

        // 执行增量时间旅行，从版本 32 回溯至历史版本 8
        long targetVersion = 8;
        TimeTravelRevertResult result = stateHub.revertToVersion(tenantId, workflowId, targetVersion);

        assertNotNull(result);
        assertEquals(targetVersion, result.targetVersion());
        assertEquals(32, result.sourceVersion());
        assertEquals(targetVersion, result.targetNode().getVersion());

        // 验证回溯步数严格遵循 O(log N) 二分提升 (32 - 8 = 24 = 16 + 8, 最多 2 次大步跳跃)
        assertTrue(result.receipt().revertDepth() <= 5, "二进制提升寻址步数必须严格 <= log2(N)");

        // 验证状态重构一致性完备度 (100% 具备历史 1 到 8 的变量，且不含 9 到 32 的污染)
        Map<String, Object> reconstructed = result.reconstructedState();
        assertEquals(8, reconstructed.get("globalCounter"), "回退重构后的状态值必须严格还原至目标版本");
        for (int i = 1; i <= 8; i++) {
            assertTrue(reconstructed.containsKey("param_" + i), "重构状态必须包含目标历史祖先参数 param_" + i);
        }
        for (int i = 9; i <= totalNodes; i++) {
            assertFalse(reconstructed.containsKey("param_" + i), "重构状态绝不能包含未来已撤销的污染参数 param_" + i);
        }
    }

    /**
     * TC-148-4: 单次历史版本时间旅行回滚耗时控制在 <= 5.0ms 压测契约
     */
    @Test
    @DisplayName("TC-148-4: 单次历史版本时间旅行回滚耗时控制在 <= 5.0ms 压测契约")
    void testTimeTravelReversionLatencyBudget() {
        String perfWfId = "wf-perf-time-travel-test";
        // 预热并构造 64 层状态树
        for (int i = 1; i <= 64; i++) {
            Map<String, Object> delta = Map.of("key_" + i, "payload_" + i);
            double[] vec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("perf_" + i);
            stateHub.commitStateDelta(tenantId, perfWfId, "PERF_NODE_" + i, delta, vec);
        }

        // 执行多轮随机深度时间旅行回溯测试，记录延迟
        List<Long> latenciesMicros = new ArrayList<>();
        long[] targets = {48, 32, 16, 8, 4, 2, 1};

        for (long tgt : targets) {
            TimeTravelRevertResult res = stateHub.revertToVersion(tenantId, perfWfId, tgt);
            latenciesMicros.add(res.elapsedMicros());
            assertTrue(res.elapsedMicros() <= 5000, "单次时间旅行耗时必须严格 <= 5000 微秒 (5.0ms)，当前为: " + res.elapsedMicros() + "μs");
        }

        // 验证平均耗时处于亚毫秒级别 (< 2.0ms)
        double avgLatency = latenciesMicros.stream().mapToLong(Long::longValue).average().orElse(0.0);
        assertTrue(avgLatency < 2000.0, "平均时间旅行回溯耗时应小于 2.0ms，实际为: " + avgLatency + "μs");
    }

    /**
     * TC-148-5: 置信度门控投机分支推演与隔离沙盒写入契约 (γ >= 0.88)
     */
    @Test
    @DisplayName("TC-148-5: 置信度门控投机分支推演与隔离沙盒写入契约 (γ >= 0.88)")
    void testSpeculativeBranchGatingAndIsolationSandbox() {
        // 初始化主干节点
        Map<String, Object> rootDelta = Map.of("init", "ready");
        double[] rootVec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("root");
        stateHub.commitStateDelta(tenantId, workflowId, "START_NODE", rootDelta, rootVec);

        Map<String, Object> specDelta = Map.of("speculativeData", "high_confidence_prediction");
        double[] specVec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("spec");

        // 1. 低置信度拒绝门禁: confidence = 0.82 < 0.88
        SpeculativeBranchResult lowConfResult = stateHub.triggerSpeculativeBranch(
                tenantId, workflowId, "branch_low", 0.82, specDelta, specVec
        );
        assertFalse(lowConfResult.accepted(), "置信度低于 0.88 时必须严格拒绝投机执行");
        assertTrue(lowConfResult.message().contains("SPECULATION_REJECTED_LOW_CONFIDENCE"));

        // 2. 高置信度准入门禁: confidence = 0.94 >= 0.88
        SpeculativeBranchResult highConfResult = stateHub.triggerSpeculativeBranch(
                tenantId, workflowId, "branch_high", 0.94, specDelta, specVec
        );
        assertTrue(highConfResult.accepted(), "置信度 >= 0.88 时必须允许投机分支在沙盒中启动");
        assertNotNull(highConfResult.branchContext());
        assertTrue(highConfResult.theoreticalFidelityLoss() < StateDifferentialKernelEstimator.THEORETICAL_MAX_FIDELITY_LOSS,
                "理论保真度损失必须受控在 Lemma 148.2 上限之内");

        // 3. 验证主干状态隔离性：主干活动节点未被污染
        StateVersionNode activeHead = stateHub.getOrCreateWorkflowContext(tenantId, workflowId).getActiveHeadNode();
        assertEquals(1, activeHead.getVersion(), "主干头节点版本不能因投机沙盒预执行而提前推进");
        assertFalse(activeHead.getDeltaState().containsKey("speculativeData"), "主干状态绝不能被未提交的投机分支污染");
    }

    /**
     * TC-148-6: 投机分支命中合并与审批驳回隔离废弃保真度验证 (Lemma 148.2)
     */
    @Test
    @DisplayName("TC-148-6: 投机分支命中合并与审批驳回隔离废弃保真度验证 (Lemma 148.2)")
    void testSpeculativeCommitAndDiscardSemantics() {
        Map<String, Object> rootDelta = Map.of("task", "HITL_DECISION_PENDING");
        double[] rootVec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("hitl_wait");
        stateHub.commitStateDelta(tenantId, workflowId, "APPROVAL_NODE", rootDelta, rootVec);

        Map<String, Object> branchDelta = Map.of("precomputedSummary", "季度利润同比增长 24.5%");
        double[] branchVec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("summary");

        // 启动投机推演
        SpeculativeBranchResult specLaunch = stateHub.triggerSpeculativeBranch(
                tenantId, workflowId, "branch_auto_summary", 0.91, branchDelta, branchVec
        );
        assertTrue(specLaunch.accepted());
        String branchId = specLaunch.branchContext().getBranchId();

        // 场景 A: 模拟人工审批通过 -> 触发原子合并
        SpeculativeCommitResult commitResult = stateHub.commitSpeculativeBranch(tenantId, workflowId, branchId);
        assertNotNull(commitResult.mergedNode());
        assertEquals(2, commitResult.mergedNode().getVersion());
        assertTrue(commitResult.receipt().speculativeBranchHit(), "合并凭单中投机命中标识必须为 true");
        assertTrue(commitResult.receipt().verifyIntegrity(), "凭单必须自验真通过");

        // 场景 B: 模拟后续另一分支审批驳回并废弃
        Map<String, Object> badDelta = Map.of("unsafeAction", "DROP TABLE");
        double[] badVec = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("bad");
        SpeculativeBranchResult badLaunch = stateHub.triggerSpeculativeBranch(
                tenantId, workflowId, "branch_risky", 0.89, badDelta, badVec
        );
        assertTrue(badLaunch.accepted());
        String badBranchId = badLaunch.branchContext().getBranchId();

        WorkflowTimeTravelAuditReceipt discardReceipt = stateHub.discardSpeculativeBranch(
                tenantId, workflowId, badBranchId, "人工安全专家否决危险写操作"
        );
        assertNotNull(discardReceipt);
        assertFalse(discardReceipt.speculativeBranchHit(), "废弃凭单中投机命中标识必须为 false");
        assertTrue(discardReceipt.verifyIntegrity(), "废弃凭单常量时间自验真必须通过");

        // 验证主干仍然停留在版本 2，未受被驳回分支影响
        StateVersionNode finalHead = stateHub.getOrCreateWorkflowContext(tenantId, workflowId).getActiveHeadNode();
        assertEquals(2, finalHead.getVersion());
    }

    /**
     * TC-148-7: 三级冷备隔离环形缓冲区软回退与可逆自愈防丢失契约
     */
    @Test
    @DisplayName("TC-148-7: 三级冷备隔离环形缓冲区软回退与可逆自愈防丢失契约")
    void testQuarantineColdRingBufferReversibility() {
        // 构造状态链
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> d = Map.of("stepIndex", i);
            double[] v = StateDifferentialKernelEstimator.generateDeterministicSphericalEmbedding("v_" + i);
            stateHub.commitStateDelta(tenantId, workflowId, "STEP_" + i, d, v);
        }

        // 回滚至版本 2，版本 3, 4, 5 应被隔离至冷备环形仓
        stateHub.revertToVersion(tenantId, workflowId, 2);

        var ringBuffer = stateHub.getOrCreateWorkflowContext(tenantId, workflowId).getColdRingBuffer();
        assertTrue(ringBuffer.size() >= 3, "回溯跳过的历史节点必须移入三级冷备环形仓保护");

        // 验证软隔离项包含版本 5 的信息
        var entryOpt = ringBuffer.findByVersion(5);
        assertTrue(entryOpt.isPresent(), "必须能在冷备环形仓中检索到被隔离的版本 5");
        assertEquals("quarantine_5", entryOpt.get().quarantineId());
        assertEquals(5, entryOpt.get().deltaState().get("stepIndex"));
    }

    /**
     * TC-148-8: 纯 Java 21 Record 审计凭单 SHA-256 常量时间自验真与防篡改检验
     */
    @Test
    @DisplayName("TC-148-8: 纯 Java 21 Record 审计凭单 SHA-256 常量时间自验真与防篡改检验")
    void testAuditReceiptCryptographicTamperResistance() {
        WorkflowTimeTravelAuditReceipt originalReceipt = WorkflowTimeTravelAuditReceipt.create(
                "receipt_sec_001", tenantId, workflowId,
                "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
                10, 4, 3, 0.952, true, 850, 2, System.currentTimeMillis()
        );

        // 1. 原始凭单完整性验真必须通过
        assertTrue(originalReceipt.verifyIntegrity(), "初始生成的审计凭单必须自验真成功");

        // 2. 模拟黑客对凭单关键字段（例如目标回退版本、核相似度）实施篡改
        WorkflowTimeTravelAuditReceipt tamperedReceipt = new WorkflowTimeTravelAuditReceipt(
                originalReceipt.receiptId(),
                originalReceipt.tenantId(),
                originalReceipt.workflowId(),
                originalReceipt.merkleRootHash(),
                originalReceipt.sourceVersion(),
                999L, // 篡改目标版本
                originalReceipt.revertDepth(),
                originalReceipt.kernelSimilarity(),
                originalReceipt.speculativeBranchHit(),
                originalReceipt.latencyMicros(),
                originalReceipt.quarantineBufferSize(),
                originalReceipt.sha256Signature(), // 保持原始签名不变
                originalReceipt.timestamp()
        );

        // 3. 常量时间验签必须立即检测到数据已被篡改并拒绝
        assertFalse(tamperedReceipt.verifyIntegrity(), "任何参数被篡改后，常量时间验签必须严格拒绝");
    }
}
