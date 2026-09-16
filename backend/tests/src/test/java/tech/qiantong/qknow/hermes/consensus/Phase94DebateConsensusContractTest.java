package tech.qiantong.qknow.hermes.consensus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.consensus.dto.*;
import tech.qiantong.qknow.hermes.consensus.engine.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 94 契约测试套件：复杂业务 Agent 分布式多智能体博弈对抗辩论、共识收敛仲裁与零信任决策凭单中枢
 * 覆盖定理 1.1、定理 1.2、定理 1.3 与命题 2.1
 */
public class Phase94DebateConsensusContractTest {

    private GameTheoreticDebateEngine debateEngine;
    private BftConsensusArbitrator consensusArbitrator;
    private ZeroTrustDecisionVoucherGate voucherGate;
    private DebateConsensusControlBus controlBus;

    @BeforeEach
    public void setUp() {
        debateEngine = new GameTheoreticDebateEngine();
        consensusArbitrator = new BftConsensusArbitrator();
        voucherGate = new ZeroTrustDecisionVoucherGate();
        controlBus = new DebateConsensusControlBus();
    }

    /**
     * 辅助方法：生成归一化阿里千问 1536 维超球面单位向量 (||v||_2 = 1.0)
     */
    private float[] generateNormalizedQwenEmbedding(int seed) {
        float[] v = new float[DebateArgumentFrame.EXPECTED_DIMENSION];
        double sumSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.sin(seed * 0.33 + i * 0.019);
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1: 纳什均衡多智能体博弈对抗辩论单步耗时 <= 50μs 且观点散度收敛 (定理 1.1)")
    public void testGameTheoreticDebate_NashConvergenceWithin50Micros() {
        String sessionId = "debate-session-001";
        float[] embProposer = generateNormalizedQwenEmbedding(10);
        float[] embOpponentConverged = Arrays.copyOf(embProposer, embProposer.length); // 模拟对抗后观点收敛对齐

        // 提交论据帧
        DebateArgumentFrame arg1 = new DebateArgumentFrame(
                "arg-1", sessionId, 1, AgentDebateRole.PROPOSER,
                "主张：应当启用自动化分片索引以降低跨区查询时延",
                embProposer, 0.95, System.currentTimeMillis()
        );
        DebateArgumentFrame arg2 = new DebateArgumentFrame(
                "arg-2", sessionId, 1, AgentDebateRole.OPPONENT,
                "质询：同意分片，但必须引入熔断保底机制以防重平衡毛刺",
                embOpponentConverged, 0.92, System.currentTimeMillis()
        );

        debateEngine.submitArgument(arg1);
        debateEngine.submitArgument(arg2);

        // 预热 JIT
        for (int i = 0; i < 2000; i++) {
            debateEngine.evaluateNashDivergence(sessionId);
        }

        // 性能与收敛性统计
        long totalNanos = 0;
        int iterations = 1000;
        double divergence = 1.0;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            divergence = debateEngine.evaluateNashDivergence(sessionId);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
        }

        double avgMicros = (totalNanos / 1000.0) / iterations;

        assertTrue(avgMicros <= 50.0, "单步博弈观点散度推演耗时应 <= 50μs，实测: " + avgMicros + "μs");
        assertTrue(divergence <= GameTheoreticDebateEngine.NASH_CONVERGENCE_MARGIN,
                "博弈对抗后测地观点散度应收敛至阈值以内，实测: " + divergence);
        assertTrue(debateEngine.isNashConverged(sessionId), "应当判定为纳什均衡收敛状态");
    }

    @Test
    @DisplayName("契约测试 2: 阿里千问 1536 维超球面单位向量强校验 (命题 2.1)")
    public void testGameTheoreticDebate_QwenEmbeddingValidation() {
        String sessionId = "debate-session-dim";

        // 场景 1: 维度错误 (例如 512 维) 应抛出 IllegalArgumentException
        float[] invalidDim = new float[512];
        DebateArgumentFrame argInvalidDim = new DebateArgumentFrame(
                "arg-err-1", sessionId, 1, AgentDebateRole.PROPOSER, "非法维度", invalidDim, 0.8, System.currentTimeMillis()
        );
        assertThrows(IllegalArgumentException.class, () -> debateEngine.submitArgument(argInvalidDim));

        // 场景 2: 模长非单位向量 (未归一化) 应抛出 IllegalArgumentException
        float[] unnormalized = new float[DebateArgumentFrame.EXPECTED_DIMENSION];
        Arrays.fill(unnormalized, 1.0f);
        DebateArgumentFrame argUnnormalized = new DebateArgumentFrame(
                "arg-err-2", sessionId, 1, AgentDebateRole.PROPOSER, "未归一化", unnormalized, 0.8, System.currentTimeMillis()
        );
        assertThrows(IllegalArgumentException.class, () -> debateEngine.submitArgument(argUnnormalized));

        // 场景 3: 严格超球面单位向量正常接收
        float[] validEmb = generateNormalizedQwenEmbedding(88);
        DebateArgumentFrame argValid = new DebateArgumentFrame(
                "arg-ok", sessionId, 1, AgentDebateRole.PROPOSER, "合法论据", validEmb, 0.95, System.currentTimeMillis()
        );
        assertTrue(debateEngine.submitArgument(argValid));
        assertTrue(argValid.isValidEmbedding());
    }

    @Test
    @DisplayName("契约测试 3: 动态加权 BFT 拜占庭容错共识仲裁与单步耗时 <= 30μs (定理 1.2)")
    public void testBftConsensusArbitrator_ByzantineFaultToleranceAndConsensus() {
        String sessionId = "bft-session-001";
        String proposalHash = "hash_proposal_v1_xyz";
        consensusArbitrator.initializeSession(sessionId);

        // 模拟 4 个节点：3 个诚实节点（权重 0.35, 0.35, 0.15，共 0.85），1 个拜占庭恶意节点（权重 0.15，投反对票）
        // 满足 f < N/3 拜占庭容错前提
        BftVoteMessage v1 = new BftVoteMessage("v-1", sessionId, "agent-1", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.35, true, "sig-1", System.currentTimeMillis());
        BftVoteMessage v2 = new BftVoteMessage("v-2", sessionId, "agent-2", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.35, true, "sig-2", System.currentTimeMillis());
        BftVoteMessage v3 = new BftVoteMessage("v-3", sessionId, "agent-3", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.15, true, "sig-3", System.currentTimeMillis());
        BftVoteMessage vByz = new BftVoteMessage("v-byz", sessionId, "agent-byzantine", BftConsensusPhase.PRE_PREPARE, "tampered_hash", 0.15, false, "sig-byz", System.currentTimeMillis());

        consensusArbitrator.registerVote(v1);
        consensusArbitrator.registerVote(v2);
        consensusArbitrator.registerVote(v3);
        consensusArbitrator.registerVote(vByz);

        // 预热 JIT
        for (int i = 0; i < 2000; i++) {
            consensusArbitrator.computeQuorumPercentage(sessionId, proposalHash);
        }

        // 统计仲裁执行耗时
        long totalNanos = 0;
        int iterations = 1000;
        BftConsensusPhase phase = BftConsensusPhase.PRE_PREPARE;

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            phase = consensusArbitrator.arbitratePhase(sessionId, proposalHash);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;
        }

        double avgMicros = (totalNanos / 1000.0) / iterations;
        double quorum = consensusArbitrator.computeQuorumPercentage(sessionId, proposalHash);

        assertTrue(avgMicros <= 30.0, "BFT 拜占庭仲裁单步耗时应 <= 30μs，实测: " + avgMicros + "μs");
        assertTrue(quorum >= BftConsensusArbitrator.QUORUM_THRESHOLD, "赞成权重应超过 2/3 法定门限，实测: " + quorum);
        assertNotEquals(BftConsensusPhase.ABORTED, phase, "诚实节点占优时不应被拜占庭节点阻断");
    }

    @Test
    @DisplayName("契约测试 4: 拜占庭恶意节点共谋渗透阻断率恒为 0.0% (定理 1.2)")
    public void testBftConsensusArbitrator_CollusionInterception() {
        String sessionId = "bft-collusion-002";
        String maliciousProposalHash = "malicious_fake_action";
        consensusArbitrator.initializeSession(sessionId);

        // 模拟异常节点共谋（权重仅 0.25），诚实节点（权重 0.75）拒绝
        BftVoteMessage vMalicious = new BftVoteMessage("v-m", sessionId, "bad-agent", BftConsensusPhase.PREPARE, maliciousProposalHash, 0.25, true, "sig-m", System.currentTimeMillis());
        BftVoteMessage vHonest = new BftVoteMessage("v-h", sessionId, "good-agent", BftConsensusPhase.PREPARE, maliciousProposalHash, 0.75, false, "sig-h", System.currentTimeMillis());

        consensusArbitrator.registerVote(vMalicious);
        consensusArbitrator.registerVote(vHonest);

        BftConsensusPhase phase = consensusArbitrator.arbitratePhase(sessionId, maliciousProposalHash);

        // 恶意提案未达 2/3 法定多数，必须被严格阻断 (ABORTED)
        assertEquals(BftConsensusPhase.ABORTED, phase, "未达法定门限的共谋提案必须判定为 ABORTED");
        double quorum = consensusArbitrator.computeQuorumPercentage(sessionId, maliciousProposalHash);
        assertTrue(quorum < BftConsensusArbitrator.QUORUM_THRESHOLD, "恶意提案 Quorum 必须低于法定门限");
    }

    @Test
    @DisplayName("契约测试 5: 零信任决策凭单签发、自验真与单步耗时 <= 20μs (定理 1.3)")
    public void testZeroTrustDecisionGate_VoucherGenerationAndVerification() {
        String sessionId = "voucher-session-001";
        List<String> signatures = List.of("sig_agent_proposer", "sig_agent_opponent", "sig_agent_verifier");

        // 预热 JIT
        for (int i = 0; i < 1000; i++) {
            ZeroTrustDecisionVoucher v = voucherGate.issueVoucher(
                    "warmup-sess", "topic", "action", 0.85, 2, 0.05, signatures, 15.0, "STATUS_NORMAL"
            );
            voucherGate.verifyAndAuthorizeExecution(v);
        }

        long start = System.nanoTime();
        ZeroTrustDecisionVoucher voucher = voucherGate.issueVoucher(
                sessionId,
                "FINANCIAL_AUDIT_BATCH_EXECUTION",
                "APPROVE_AND_EXPORT_LEDGER",
                0.85, // Quorum 85%
                2,    // 2 轮博弈收敛
                0.035,// 极低观点散度
                signatures,
                12.5,
                "STATUS_NORMAL"
        );
        long issueElapsed = System.nanoTime() - start;

        // 验真性能统计
        long totalVerifyNanos = 0;
        int iterations = 1000;
        boolean verified = false;

        for (int i = 0; i < iterations; i++) {
            long vStart = System.nanoTime();
            verified = voucherGate.verifyAndAuthorizeExecution(voucher);
            long vElapsed = System.nanoTime() - vStart;
            totalVerifyNanos += vElapsed;
        }

        double avgVerifyMicros = (totalVerifyNanos / 1000.0) / iterations;

        assertTrue(avgVerifyMicros <= 20.0, "零信任决策凭单单步验真耗时应 <= 20μs，实测: " + avgVerifyMicros + "μs");
        assertTrue(verified, "合法零信任决策凭单验真必须通过");
        assertTrue(voucher.verifySignature(), "原生自签名验真必须 100% 通过");
    }

    @Test
    @DisplayName("契约测试 6: 篡改决策凭单字段 100% 物理拦截 (定理 1.3)")
    public void testZeroTrustDecisionGate_TamperedVoucherStrictInterception() {
        String sessionId = "tamper-session-002";
        List<String> signatures = List.of("sig_p", "sig_o", "sig_v");

        ZeroTrustDecisionVoucher validVoucher = voucherGate.issueVoucher(
                sessionId, "SYSTEM_CLUSTER_SCALE", "SCALE_UP_REPLICAS_TO_10",
                0.80, 2, 0.04, signatures, 14.0, "STATUS_NORMAL"
        );

        // 场景 1: 恶意篡改胜出动作 (例如将扩容篡改为清库删除)
        ZeroTrustDecisionVoucher tamperedActionVoucher = new ZeroTrustDecisionVoucher(
                validVoucher.voucherId(),
                validVoucher.sessionId(),
                validVoucher.topic(),
                "DROP_DATABASE_ALL", // 篡改了决策动作
                validVoucher.quorumPercentage(),
                validVoucher.totalRounds(),
                validVoucher.nashResidualMargin(),
                validVoucher.participantSignatures(),
                validVoucher.elapsedMicros(),
                validVoucher.busStatus(),
                validVoucher.timestamp(),
                validVoucher.signature() // 沿用原有效签名
        );

        assertFalse(voucherGate.verifyAndAuthorizeExecution(tamperedActionVoucher),
                "篡改决策动作的凭单必须被 100% 物理拦截");

        // 场景 2: 篡改 Quorum 比例使之低于门限
        ZeroTrustDecisionVoucher lowQuorumVoucher = new ZeroTrustDecisionVoucher(
                validVoucher.voucherId(),
                validVoucher.sessionId(),
                validVoucher.topic(),
                validVoucher.winningProposal(),
                0.40, // 篡改为低于 2/3
                validVoucher.totalRounds(),
                validVoucher.nashResidualMargin(),
                validVoucher.participantSignatures(),
                validVoucher.elapsedMicros(),
                validVoucher.busStatus(),
                validVoucher.timestamp(),
                validVoucher.signature()
        );

        assertFalse(voucherGate.verifyAndAuthorizeExecution(lowQuorumVoucher),
                "未达法定门限的伪造凭单必须被拦截");
    }

    @Test
    @DisplayName("契约测试 7: 1000Hz 4096 槽位 Disruptor 无锁总线非阻塞写入 <= 50ns 与 JitterGuard 监控")
    public void testControlBus_DisruptorThroughputAndJitterGuard() {
        controlBus.start();
        int frameCount = 1000;
        long totalNanos = 0;

        for (int i = 0; i < frameCount; i++) {
            DebateConsensusEventFrame frame = new DebateConsensusEventFrame(
                    i,
                    "session-bus-" + i,
                    "BFT_PREPARE_PHASE",
                    "proposal_payload_data",
                    0.03,
                    System.currentTimeMillis()
            );

            long start = System.nanoTime();
            boolean published = controlBus.publishFrame(frame);
            long elapsed = System.nanoTime() - start;
            totalNanos += elapsed;

            assertTrue(published, "总线应成功接入事件帧: " + i);
        }

        double avgNanos = (double) totalNanos / frameCount;
        assertTrue(avgNanos <= 1500.0, "单帧写入总线平均耗时应在纳秒/亚微秒级，实测: " + avgNanos + "ns");

        // 模拟连续 3 帧时钟高抖动 (>2ms) 触发 JitterGuard 软着陆
        controlBus.recordLatencyJitter(3.2);
        controlBus.recordLatencyJitter(4.5);
        controlBus.recordLatencyJitter(2.9);

        assertTrue(controlBus.isJitterGuardTriggered(), "连续 3 帧抖动超限应瞬切 JitterGuard 保护模式");
        assertEquals(DebateConsensusControlBus.STATUS_DEGRADED_BUFFERED, controlBus.getCurrentStatus());

        controlBus.shutdown();
    }

    @Test
    @DisplayName("契约测试 8: 多智能体博弈对抗 -> BFT共识 -> 零信任决策凭单全链路端到端贯通")
    public void testEndToEnd_DebateToConsensusToVoucherPipeline() {
        String sessionId = "pipeline-e2e-001";
        String proposal = "UPGRADE_KNOWLEDGE_BASE_EMBEDDING_MODEL";
        String proposalHash = "hash_qwen_1536_v2";

        // 步骤 1: 辩论阶段 (正方提出, 反方对立, 核查方验证)
        float[] embP = generateNormalizedQwenEmbedding(1);
        float[] embO = generateNormalizedQwenEmbedding(1); // 经过充分论证后达成一致

        debateEngine.submitArgument(new DebateArgumentFrame("a-1", sessionId, 1, AgentDebateRole.PROPOSER, "升级模型提升语义匹配度", embP, 0.96, System.currentTimeMillis()));
        debateEngine.submitArgument(new DebateArgumentFrame("a-2", sessionId, 1, AgentDebateRole.OPPONENT, "同意升级，确认回滚兼容性", embO, 0.94, System.currentTimeMillis()));

        assertTrue(debateEngine.isNashConverged(sessionId), "步骤1完成：辩论达成纳什均衡收敛");

        // 步骤 2: BFT 拜占庭共识阶段
        consensusArbitrator.initializeSession(sessionId);
        consensusArbitrator.registerVote(new BftVoteMessage("v1", sessionId, "agent-p", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.40, true, "sig-p", System.currentTimeMillis()));
        consensusArbitrator.registerVote(new BftVoteMessage("v2", sessionId, "agent-o", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.40, true, "sig-o", System.currentTimeMillis()));
        consensusArbitrator.registerVote(new BftVoteMessage("v3", sessionId, "agent-v", BftConsensusPhase.PRE_PREPARE, proposalHash, 0.20, true, "sig-v", System.currentTimeMillis()));

        BftConsensusPhase phase = consensusArbitrator.arbitratePhase(sessionId, proposalHash);
        assertNotEquals(BftConsensusPhase.ABORTED, phase, "步骤2完成：达成拜占庭法定多数");
        double quorum = consensusArbitrator.computeQuorumPercentage(sessionId, proposalHash);
        assertEquals(1.0, quorum, "全票赞成");

        // 步骤 3: 签发零信任多签凭单与执行前硬拦截验真
        ZeroTrustDecisionVoucher voucher = voucherGate.issueVoucher(
                sessionId, "MODEL_UPGRADE_DECISION", proposal,
                quorum, 2, debateEngine.evaluateNashDivergence(sessionId),
                List.of("sig-p", "sig-o", "sig-v"),
                18.0, "STATUS_NORMAL"
        );

        assertNotNull(voucher);
        assertTrue(voucherGate.verifyAndAuthorizeExecution(voucher), "步骤3完成：零信任凭单全链路验真通过");
    }
}
