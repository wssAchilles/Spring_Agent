package tech.qiantong.qknow.hermes.agent.debate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;
import tech.qiantong.qknow.hermes.agent.debate.dto.DebateConsensusStatus;
import tech.qiantong.qknow.hermes.agent.debate.dto.DebateEventFrame;
import tech.qiantong.qknow.hermes.agent.debate.dto.MultiAgentArbitrationReceipt;
import tech.qiantong.qknow.hermes.agent.debate.engine.AdaptiveRoleEvolutionGovernor;
import tech.qiantong.qknow.hermes.agent.debate.engine.BlackboardDebateArbitrator;
import tech.qiantong.qknow.hermes.agent.debate.engine.DebateOrchestrationControlBus;
import tech.qiantong.qknow.hermes.agent.debate.engine.VcgTaskAuctionCoordinator;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 86 多智能体复杂业务协同编排与去中心化黑板争辩网络 专属契约测试套件
 * <p>
 * 验证 8 大核心严苛契约：
 * 1. 自适应角色生态位演化收敛与平滑分化
 * 2. 拓展 VCG 拍卖真实竞标激励相容与超球面门禁
 * 3. 德尔菲黑板争辩香农争议信息熵衰减与 3 轮内收敛
 * 4. 争辩僵局快速仲裁专家降级裁决软着陆
 * 5. 1000Hz 4096 槽位 Disruptor 无锁总线纳秒级吞吐与 JitterGuard 抖动监控
 * 6. 不可变仲裁存证凭单 SHA-256 密码学自签名与防篡改验真
 * 7. 阿里千问 1536 维超球面单位向量范数严格校验
 * 8. 端到端多智能体全流程协同编排闭环
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class Phase86MultiAgentDebateContractTest {

    /**
     * 工具方法：生成指定维度的归一化超球面单位向量 (||v||_2 = 1.0)
     */
    private float[] createNormalizedSphericalVector(int dim, long seed) {
        Random rand = new Random(seed);
        float[] v = new float[dim];
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (rand.nextGaussian());
            sumSq += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    @Test
    @DisplayName("契约测试 1：角色生态位演化收敛与故障转移热备平滑接管")
    void testAdaptiveRoleEvolution_ConvergenceAndNicheBalance() {
        AdaptiveRoleEvolutionGovernor governor = new AdaptiveRoleEvolutionGovernor();

        // 注册 5 个不同初始倾向的智能体
        governor.registerAgent("agent-analyst-1", new float[]{ 0.9f, 0.2f, 0.3f, 0.4f, 0.5f });
        governor.registerAgent("agent-coder-1",   new float[]{ 0.2f, 0.95f, 0.4f, 0.2f, 0.1f });
        governor.registerAgent("agent-reviewer-1",new float[]{ 0.3f, 0.4f, 0.9f, 0.3f, 0.2f });
        governor.registerAgent("agent-critic-1",  new float[]{ 0.2f, 0.2f, 0.4f, 0.92f, 0.3f });
        governor.registerAgent("agent-arbitrator-1", new float[]{ 0.4f, 0.2f, 0.3f, 0.5f, 0.96f });

        // 单步演化性能测试 (<= 50us)
        long startNs = System.nanoTime();
        Map<String, AgentRoleNicheType> roles = governor.evolvePopulation();
        long elapsedUs = (System.nanoTime() - startNs) / 1000;

        assertEquals(5, roles.size(), "所有智能体均应被成功分配生态位");
        assertTrue(elapsedUs < 1000, "批量演化耗时应极短: " + elapsedUs + "us");

        // 校验主要生态位匹配度
        assertEquals(AgentRoleNicheType.ANALYST, roles.get("agent-analyst-1"));
        assertEquals(AgentRoleNicheType.CODER, roles.get("agent-coder-1"));
        assertEquals(AgentRoleNicheType.REVIEWER, roles.get("agent-reviewer-1"));
        assertEquals(AgentRoleNicheType.CRITIC, roles.get("agent-critic-1"));
        assertEquals(AgentRoleNicheType.ARBITRATOR, roles.get("agent-arbitrator-1"));

        // 故障平滑转移测试：假设 CODER 故障下线，需要从剩余智能体中选出最具备编码能力的进行热备接管
        governor.registerAgent("agent-standby-coder", new float[]{ 0.4f, 0.85f, 0.6f, 0.3f, 0.2f });
        governor.assignNiche("agent-standby-coder");

        String substitute = governor.handleFailover("agent-coder-1", AgentRoleNicheType.CODER);
        assertNotNull(substitute, "应成功选出接管智能体");
        assertEquals(AgentRoleNicheType.CODER, governor.getAgentRole(substitute), "接管智能体角色应更新为 CODER");
    }

    @Test
    @DisplayName("契约测试 2：拓展 VCG 拍卖真实竞标激励相容与千问 1536 维超球面门禁")
    void testVcgAuction_TruthfulBiddingAndSphericalGate() {
        VcgTaskAuctionCoordinator coordinator = new VcgTaskAuctionCoordinator();

        // 构造基准任务需求向量 (1536 维超球面单位向量)
        float[] taskVector = createNormalizedSphericalVector(1536, 42L);

        // 候选智能体 1：高度契合需求 (相关性很高，s > 0.85)
        float[] matchVector1 = Arrays.copyOf(taskVector, taskVector.length);
        matchVector1[0] += 0.01f; // 微小扰动

        // 候选智能体 2：中度契合需求 (s 约 0.75)
        float[] matchVector2 = createNormalizedSphericalVector(1536, 42L);

        // 候选智能体 3：劣质智能体 (随机噪声向量，s 极低接近 0)
        float[] noiseVector = createNormalizedSphericalVector(1536, 9999L);

        List<VcgTaskAuctionCoordinator.BidOffer> bids = List.of(
                new VcgTaskAuctionCoordinator.BidOffer("agent-high-skill", matchVector1, 95.0, 15.0),
                new VcgTaskAuctionCoordinator.BidOffer("agent-med-skill", matchVector2, 85.0, 20.0),
                new VcgTaskAuctionCoordinator.BidOffer("agent-inferior", noiseVector, 100.0, 1.0) // 恶意虚报超高价值与超低成本
        );

        VcgTaskAuctionCoordinator.AuctionResult result = coordinator.conductAuction(taskVector, bids);

        // 校验门禁硬拦截
        assertTrue(result.eliminatedByGateAgents().contains("agent-inferior"),
                "劣质智能体因超球面内积不足必须被硬门禁剔除");

        // 校验胜出者应为真实福利最高者 (95 - 15 = 80 > 85 - 20 = 65)
        assertEquals("agent-high-skill", result.winnerAgentId(), "胜出者应为高质量合格智能体");

        // 校验 VCG 二阶外部性支付
        assertTrue(result.vcgPayment() > 0.0, "VCG 结算成本应大于 0");
    }

    @Test
    @DisplayName("契约测试 3：德尔菲黑板争辩香农争议信息熵衰减与 3 轮内收敛")
    void testDelphiDebate_ShannonEntropyDecayWithinThreeRounds() {
        BlackboardDebateArbitrator arbitrator = new BlackboardDebateArbitrator();

        // 构造初始争辩帧：3 个智能体提出不同见解，但其中一个主张获得更多支撑
        List<DebateEventFrame> frames = List.of(
                new DebateEventFrame("f1", "deb-001", "agent-coder-1", AgentRoleNicheType.CODER,
                        "采用流式分片管道架构", null, 0.9, 1, System.currentTimeMillis(), 1L),
                new DebateEventFrame("f2", "deb-001", "agent-reviewer-1", AgentRoleNicheType.REVIEWER,
                        "支持流式分片，补充背压限制", null, 0.9, 1, System.currentTimeMillis(), 2L),
                new DebateEventFrame("f3", "deb-001", "agent-critic-1", AgentRoleNicheType.CRITIC,
                        "关注内存与抖动风险", null, 0.9, 1, System.currentTimeMillis(), 3L)
        );

        BlackboardDebateArbitrator.DebateResolution resolution = arbitrator.resolveDebate("deb-001", frames);

        assertTrue(resolution.roundsCompleted() <= 3, "争辩必须在 <= 3 轮内结束");
        assertEquals(DebateConsensusStatus.CONSENSUS_REACHED, resolution.status(), "应成功收敛达成共识");
        assertTrue(resolution.finalEntropy() <= 0.35, "最终香农争议熵应 <= 0.35: " + resolution.finalEntropy());
        assertNotNull(resolution.chosenProposalAgentId(), "必须选定最终方案");
    }

    @Test
    @DisplayName("契约测试 4：争辩僵局快速仲裁专家降级裁决软着陆")
    void testDebateDeadlock_FastFallbackArbitration() {
        BlackboardDebateArbitrator arbitrator = new BlackboardDebateArbitrator();

        // 构造严重势均力敌、无法自然收敛的互斥主张
        List<DebateEventFrame> deadlockFrames = List.of(
                new DebateEventFrame("f1", "deb-deadlock", "agent-faction-a", AgentRoleNicheType.CODER,
                        "必须全量同步锁死", null, 1.0, 1, System.currentTimeMillis(), 1L),
                new DebateEventFrame("f2", "deb-deadlock", "agent-faction-b", AgentRoleNicheType.CRITIC,
                        "必须彻底异步无锁", null, 1.0, 1, System.currentTimeMillis(), 2L)
        );

        long startNs = System.nanoTime();
        BlackboardDebateArbitrator.DebateResolution resolution = arbitrator.resolveDebate("deb-deadlock", deadlockFrames);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertTrue(elapsedMs <= 10, "僵局判定与仲裁专家裁决耗时必须 <= 10ms: " + elapsedMs + "ms");
        assertEquals(DebateConsensusStatus.DEGRADED_ARBITRATION, resolution.status(), "应正确进入仲裁专家终审裁决");
        assertNotNull(resolution.chosenProposalAgentId(), "仲裁专家必须裁定出胜出者");
        assertTrue(resolution.roundsCompleted() <= 3, "轮次不得超过看门狗上限 3 轮");
    }

    @Test
    @DisplayName("契约测试 5：1000Hz 4096 槽位 Disruptor 无锁总线纳秒级吞吐与 JitterGuard 抖动监控")
    void testDisruptorBus_SubMicrosecondThroughputAndJitterGuard() {
        DebateOrchestrationControlBus bus = new DebateOrchestrationControlBus();

        // 1. 吞吐量与写入延迟校验 (发布 1000 帧事件)
        long startNs = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            DebateEventFrame frame = new DebateEventFrame(
                    "frame-" + i, "deb-perf", "agent-worker", AgentRoleNicheType.CODER,
                    "论据内容 " + i, null, 0.5, 1, System.currentTimeMillis(), i
            );
            bus.publishFrame(frame);
        }
        long durationNs = System.nanoTime() - startNs;
        long avgNsPerFrame = durationNs / 1000;

        assertEquals(1000, bus.getPublishedFrameCount(), "总线应完整接收 1000 帧事件");
        assertTrue(avgNsPerFrame < 5000, "单帧写入平均耗时应在微秒/纳秒级别: " + avgNsPerFrame + "ns");
        assertEquals("BUS_HEALTHY", bus.getBusStatus(), "正常发布状态下总线应健康");

        // 2. JitterGuard 时钟抖动监控测试：连续 3 帧时钟间隔超过 2ms 触发软着陆
        long currentMs = System.currentTimeMillis();
        bus.publishFrame(new DebateEventFrame("jf1", "deb-jitter", "ag", AgentRoleNicheType.CODER, "j1", null, 0.5, 1, currentMs + 5, 1001L));
        bus.publishFrame(new DebateEventFrame("jf2", "deb-jitter", "ag", AgentRoleNicheType.CODER, "j2", null, 0.5, 1, currentMs + 10, 1002L));
        bus.publishFrame(new DebateEventFrame("jf3", "deb-jitter", "ag", AgentRoleNicheType.CODER, "j3", null, 0.5, 1, currentMs + 15, 1003L));

        assertEquals("STATUS_DEGRADED_ARBITRATOR_FALLBACK", bus.getBusStatus(),
                "连续 3 帧时钟抖动超标必须触发 JitterGuard 软着陆降级");
    }

    @Test
    @DisplayName("契约测试 6：不可变仲裁存证凭单 SHA-256 密码学自签名与防篡改验真")
    void testMultiAgentArbitrationReceipt_CryptographicSelfVerification() {
        MultiAgentArbitrationReceipt receipt = MultiAgentArbitrationReceipt.create(
                "RCPT-001",
                "deb-test-01",
                "task-test-01",
                "agent-coder-1",
                DebateConsensusStatus.CONSENSUS_REACHED,
                2,
                0.28,
                15.5,
                1200L,
                "BUS_HEALTHY"
        );

        // 校验原装凭单验真应 100% 通过
        assertTrue(receipt.verifySignature(), "原生签发凭单自验必须成功");
        assertNotNull(receipt.signature());
        assertEquals(64, receipt.signature().length(), "SHA-256 十六进制签名长度必须为 64 位");

        // 篡改测试：伪造篡改胜出智能体 ID
        MultiAgentArbitrationReceipt tamperedReceipt = new MultiAgentArbitrationReceipt(
                receipt.receiptId(),
                receipt.debateId(),
                receipt.taskId(),
                "agent-hacker-malicious", // 恶意篡改
                receipt.consensusStatus(),
                receipt.finalRounds(),
                receipt.finalShannonEntropy(),
                receipt.vcgCost(),
                receipt.latencyUs(),
                receipt.busStatus(),
                receipt.signature() // 沿用旧签名
        );

        assertFalse(tamperedReceipt.verifySignature(), "篡改凭单签名验真必须失败");
    }

    @Test
    @DisplayName("契约测试 7：阿里千问 1536 维超球面单位向量范数严格校验")
    void testSphericalEmbedding_StrictNormValidation() {
        // 1. 合法超球面向量校验 (1536 维，单位模长)
        float[] validVec = createNormalizedSphericalVector(1536, 12345L);
        DebateEventFrame validFrame = new DebateEventFrame(
                "f-valid", "deb-1", "ag-1", AgentRoleNicheType.ANALYST,
                "观点论证", validVec, 0.4, 1, System.currentTimeMillis(), 1L
        );
        assertTrue(validFrame.isValidEmbedding(), "合法 1536 维单位向量必须通过校验");

        // 2. 维度非法校验 (例如 512 维)
        float[] wrongDimVec = createNormalizedSphericalVector(512, 12345L);
        DebateEventFrame wrongDimFrame = new DebateEventFrame(
                "f-wrong-dim", "deb-1", "ag-1", AgentRoleNicheType.ANALYST,
                "观点论证", wrongDimVec, 0.4, 1, System.currentTimeMillis(), 2L
        );
        assertFalse(wrongDimFrame.isValidEmbedding(), "非 1536 维向量校验必须失败");

        // 3. 模长非单位向量校验 (模长 = 2.0)
        float[] unnormalizedVec = Arrays.copyOf(validVec, validVec.length);
        for (int i = 0; i < unnormalizedVec.length; i++) {
            unnormalizedVec[i] *= 2.0f;
        }
        DebateEventFrame unnormalizedFrame = new DebateEventFrame(
                "f-unnorm", "deb-1", "ag-1", AgentRoleNicheType.ANALYST,
                "观点论证", unnormalizedVec, 0.4, 1, System.currentTimeMillis(), 3L
        );
        assertFalse(unnormalizedFrame.isValidEmbedding(), "模长偏离单位超球面必须拦截");
    }

    @Test
    @DisplayName("契约测试 8：端到端多智能体全链路协同编排闭环")
    void testEndToEndMultiAgentCollaboration_ComplexWorkflow() {
        // 1. 初始化各引擎组件
        AdaptiveRoleEvolutionGovernor roleGovernor = new AdaptiveRoleEvolutionGovernor();
        VcgTaskAuctionCoordinator auctionCoordinator = new VcgTaskAuctionCoordinator();
        BlackboardDebateArbitrator debateArbitrator = new BlackboardDebateArbitrator();
        DebateOrchestrationControlBus controlBus = new DebateOrchestrationControlBus();

        // 2. 智能体群体生态位分化
        roleGovernor.registerAgent("agent-analyst", new float[]{ 0.9f, 0.1f, 0.2f, 0.1f, 0.2f });
        roleGovernor.registerAgent("agent-coder",   new float[]{ 0.1f, 0.95f, 0.3f, 0.2f, 0.1f });
        roleGovernor.registerAgent("agent-critic",  new float[]{ 0.1f, 0.2f, 0.3f, 0.9f, 0.2f });
        roleGovernor.registerAgent("agent-arbitrator", new float[]{ 0.3f, 0.2f, 0.3f, 0.4f, 0.95f });
        roleGovernor.evolvePopulation();

        assertEquals(AgentRoleNicheType.ANALYST, roleGovernor.getAgentRole("agent-analyst"));
        assertEquals(AgentRoleNicheType.CODER, roleGovernor.getAgentRole("agent-coder"));

        // 3. 任务生成与拓展 VCG 拍卖
        float[] taskVector = createNormalizedSphericalVector(1536, 777L);
        float[] coderSkillVector = createNormalizedSphericalVector(1536, 777L); // 契合
        float[] badSkillVector = createNormalizedSphericalVector(1536, 888L);   // 离散偏离

        List<VcgTaskAuctionCoordinator.BidOffer> bids = List.of(
                new VcgTaskAuctionCoordinator.BidOffer("agent-coder", coderSkillVector, 90.0, 10.0),
                new VcgTaskAuctionCoordinator.BidOffer("agent-bad", badSkillVector, 99.0, 1.0)
        );

        VcgTaskAuctionCoordinator.AuctionResult auctionResult = auctionCoordinator.conductAuction(taskVector, bids);
        assertEquals("agent-coder", auctionResult.winnerAgentId(), "VCG 拍卖应由高契合高质量智能体胜出");

        // 4. 进入黑板争辩网络对抗反思
        float[] frameVec = createNormalizedSphericalVector(1536, 100L);
        DebateEventFrame coderFrame = new DebateEventFrame("df1", "deb-e2e", "agent-coder",
                AgentRoleNicheType.CODER, "实施方案：响应式分布式黑板事件流", frameVec, 0.8, 1, System.currentTimeMillis(), 1L);
        DebateEventFrame criticFrame = new DebateEventFrame("df2", "deb-e2e", "agent-critic",
                AgentRoleNicheType.CRITIC, "红方质询：考虑极端背压与内存泄漏边界", null, 0.8, 1, System.currentTimeMillis(), 2L);

        controlBus.publishFrame(coderFrame);
        controlBus.publishFrame(criticFrame);

        BlackboardDebateArbitrator.DebateResolution debateResolution = debateArbitrator.resolveDebate("deb-e2e", List.of(coderFrame, criticFrame));
        assertTrue(debateResolution.status().isResolved(), "争辩必须达成有效决议");

        // 5. 签发不可变仲裁存证凭单
        MultiAgentArbitrationReceipt receipt = controlBus.issueReceipt(
                "deb-e2e",
                "task-e2e-001",
                debateResolution.chosenProposalAgentId(),
                debateResolution.status(),
                debateResolution.roundsCompleted(),
                debateResolution.finalEntropy(),
                auctionResult.vcgPayment(),
                1500L
        );

        // 6. 最终密码学与业务完备性验收
        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "最终全链路签发的密码学凭单必须验真通过");
        assertEquals("deb-e2e", receipt.debateId());
        assertEquals("agent-coder", receipt.winnerAgentId());
    }
}
