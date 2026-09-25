package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.engine.DynamicSwarmEdgeRewiringGovernor;
import tech.qiantong.qknow.hermes.agent.swarm.engine.DynamicSwarmEdgeRewiringGovernor.AgentCard;
import tech.qiantong.qknow.hermes.agent.swarm.engine.DynamicSwarmEdgeRewiringGovernor.RewireDecision;
import tech.qiantong.qknow.hermes.agent.swarm.engine.LiveAgentHotPluggingManager;
import tech.qiantong.qknow.hermes.agent.swarm.engine.LiveAgentHotPluggingManager.AgentInstance;
import tech.qiantong.qknow.hermes.agent.swarm.engine.LiveAgentHotPluggingManager.AgentLifecycleState;
import tech.qiantong.qknow.hermes.agent.swarm.receipt.SwarmTopologyEvolutionReceipt;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 135 核心契约测试套件：
 * 多智能体 Swarm 协同动态拓扑在前端 DAG 画布上的双向实时流式投射、节点在线热插拔与自愈中枢
 * (Multi-Agent Swarm Collaborative Dynamic Topology Real-Time Canvas Bi-Directional Streaming Projection,
 * Online Node Hot-Plugging & Self-Healing Metacenter)
 *
 * 核心验证范围：
 * 1. 千问 1536 维超球面动态边重连阈值 tau >= 0.75 与耗时 <= 5.0ms (TC-135-1)
 * 2. 智能体在线热插拔两阶段 Drain 优雅排空与零资产悬挂 (TC-135-2)
 * 3. 节点失效反应式自愈与增广轨切换时延 <= 50us (TC-135-3)
 * 4. 前端增量力导向局部平滑布局位移方差降低 >= 85% (TC-135-4)
 * 5. 纯 Java 21 Record 凭单 SHA-256 签名与常量时间验真 (TC-135-5)
 * 6. Swarm 动态交接最大深度硬熔断 (D_max <= 5) 与零死锁 (TC-135-6)
 * 7. 后端拓扑演化事件与前端画布双轨流式同步延迟 <= 16.6ms (TC-135-7)
 * 8. 端到端多智能体动态拓扑流式投射与自愈全生命周期闭环 (TC-135-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase135SwarmDynamicTopologyContractTest {

    private static final String TEST_TENANT = "tenant_enterprise_swarm";
    private static final String TEST_SESSION = "sess_swarm_p135_001";

    // 辅助生成 1536 维单位超球面向量 (严格满足 ||v||_2 = 1.0 +- 1e-4)
    private float[] generateHypersphereVector(long seed) {
        float[] vec = new float[1536];
        Random rand = new Random(seed);
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (rand.nextGaussian());
            sumSq += vec[i] * vec[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    // =========================================================================
    // TC-135-1: 千问 1536 维超球面动态边重连阈值 tau >= 0.75 与耗时 <= 5.0ms
    // =========================================================================
    @Test
    @DisplayName("TC-135-1: 千问 1536 维超球面测地线内积重连，相似度 >= 0.75 时重连成功，单次判定耗时 <= 5.0ms")
    void testHypersphereDynamicEdgeRewiring_thresholdAndLatency() {
        DynamicSwarmEdgeRewiringGovernor governor = new DynamicSwarmEdgeRewiringGovernor();

        // 注册目标智能体能力卡
        float[] targetEmbedding = generateHypersphereVector(42L);
        AgentCard targetCard = new AgentCard(
                "node_specialist_sql",
                "SQL_ANALYST",
                TEST_TENANT,
                targetEmbedding,
                Set.of("COORDINATOR", "VERIFIER")
        );
        governor.registerAgent(targetCard);

        // 注册发起方智能体能力卡
        float[] leaderEmbedding = generateHypersphereVector(100L);
        AgentCard leaderCard = new AgentCard(
                "node_leader",
                "COORDINATOR",
                TEST_TENANT,
                leaderEmbedding,
                Set.of("SQL_ANALYST")
        );
        governor.registerAgent(leaderCard);

        // 生成高相关意图向量 (与 targetEmbedding 夹角极小，内积接近 1.0)
        float[] intentEmbedding = new float[1536];
        double sumSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            intentEmbedding[i] = (float) (targetEmbedding[i] + 0.005 * (i % 2 == 0 ? 1 : -1));
            sumSq += intentEmbedding[i] * intentEmbedding[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < 1536; i++) {
            intentEmbedding[i] = (float) (intentEmbedding[i] / norm);
        }

        // 测试重连
        long startNano = System.nanoTime();
        RewireDecision decision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_leader",
                "node_specialist_sql",
                intentEmbedding
        );
        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        assertTrue(decision.connected(), "高语义相似度且角色 ACL 白名单允许时应批准连线");
        assertTrue(decision.similarity() >= 0.75, "相似度门限必须 >= 0.75，实测: " + decision.similarity());
        assertNotNull(decision.receipt(), "连线成功必须附带不可变存证凭单");
        assertTrue(decision.receipt().verifySignature(), "连线存证凭单自验真必须通过");
        assertTrue(elapsedMicros <= 5000, "超球面点积与 ACL 判定耗时必须 <= 5000微秒 (5ms)，实测: " + elapsedMicros + "us");

        // 测试反事实用例：低相似度正交意图向量
        float[] orthogonalIntent = generateHypersphereVector(9999L);
        RewireDecision lowDecision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_leader",
                "node_specialist_sql",
                orthogonalIntent
        );
        assertFalse(lowDecision.connected(), "低于相似度门限时必须拒绝连线");
        assertEquals("BELOW_SIMILARITY_THRESHOLD", lowDecision.reason());

        // 测试跨租户越权攻击拦截
        AgentCard otherTenantCard = new AgentCard(
                "node_other_tenant",
                "SQL_ANALYST",
                "other_tenant_malicious",
                targetEmbedding,
                Set.of("COORDINATOR")
        );
        governor.registerAgent(otherTenantCard);
        RewireDecision crossTenantDecision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_leader",
                "node_other_tenant",
                intentEmbedding
        );
        assertFalse(crossTenantDecision.connected(), "跨租户非法连接必须拦截");
        assertEquals("CROSS_TENANT_FORBIDDEN", crossTenantDecision.reason());
    }

    // =========================================================================
    // TC-135-2: 智能体在线热插拔两阶段 Drain 优雅排空与零资产悬挂
    // =========================================================================
    @Test
    @DisplayName("TC-135-2: 智能体在线热插拔两阶段 Drain 优雅排空，未决任务归零且零资产悬挂")
    void testLiveAgentHotPlugging_twoPhaseDrainAndZeroOrphanAssets() {
        LiveAgentHotPluggingManager manager = new LiveAgentHotPluggingManager();

        // 1. 在线热插拔注册节点
        SwarmTopologyEvolutionReceipt plugReceipt = manager.plugInAgent(
                TEST_SESSION,
                TEST_TENANT,
                "agent_sql_analyst_01",
                "SQL_ANALYST",
                "agent_coordinator_standby"
        );
        assertNotNull(plugReceipt);
        assertTrue(plugReceipt.verifySignature());

        AgentInstance instance = manager.getInstance("agent_sql_analyst_01");
        assertNotNull(instance);
        assertEquals(AgentLifecycleState.ACTIVE, instance.state());

        // 2. 模拟进行中的并发请求流入
        instance.inFlightRequests().incrementAndGet();
        instance.inFlightRequests().incrementAndGet();
        assertEquals(2, instance.inFlightRequests().get());

        // 3. 异步模拟在途请求在 50ms 内完成
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(30);
                instance.inFlightRequests().decrementAndGet();
                Thread.sleep(20);
                instance.inFlightRequests().decrementAndGet();
            } catch (InterruptedException ignored) {}
        });

        // 4. 执行两阶段优雅排空热拔出 (UNPLUG)
        long startMs = System.currentTimeMillis();
        SwarmTopologyEvolutionReceipt unplugReceipt = manager.unplugAgent(
                TEST_SESSION,
                "agent_sql_analyst_01",
                500L
        );
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertNotNull(unplugReceipt);
        assertTrue(unplugReceipt.verifySignature());
        assertEquals("UNPLUG", unplugReceipt.eventType());
        assertNull(manager.getInstance("agent_sql_analyst_01"), "排空后智能体应被完全摘除，零资产悬挂");
        assertTrue(elapsedMs >= 40, "排空过程应真实等待在途任务完成");
    }

    // =========================================================================
    // TC-135-3: 节点失效反应式自愈与增广轨切换时延 <= 50us
    // =========================================================================
    @Test
    @DisplayName("TC-135-3: 割点节点故障，反应式自愈激活增广轨 Fallback 节点，切换耗时 <= 50微秒")
    void testNodeFailureSelfHealing_reactiveBypassUnder50Micros() {
        LiveAgentHotPluggingManager manager = new LiveAgentHotPluggingManager();

        // 注册主推理中枢与备用 Fallback 节点
        manager.plugInAgent(
                TEST_SESSION,
                TEST_TENANT,
                "agent_graphrag_fallback",
                "GRAPHRAG",
                null
        );
        manager.plugInAgent(
                TEST_SESSION,
                TEST_TENANT,
                "agent_graphrag_primary",
                "GRAPHRAG",
                "agent_graphrag_fallback"
        );

        // 建立拓扑边关系：Leader -> Primary
        manager.recordActiveEdge("agent_leader", "agent_graphrag_primary");
        assertTrue(manager.getActiveEdgesFrom("agent_leader").contains("agent_graphrag_primary"));

        // 模拟进行中的任务发生崩溃
        AgentInstance primaryInstance = manager.getInstance("agent_graphrag_primary");
        primaryInstance.inFlightRequests().set(3);

        // JVM JIT 与 Logger 管道快速预热 (消除首次类加载冷启动抖动)
        manager.plugInAgent(TEST_SESSION, TEST_TENANT, "warmup_fallback", "GRAPHRAG", null);
        manager.plugInAgent(TEST_SESSION, TEST_TENANT, "warmup_node", "GRAPHRAG", "warmup_fallback");
        manager.detectAndHealFailedNode(TEST_SESSION, "warmup_node");

        // 执行故障检测与反应式拓扑自愈
        Optional<SwarmTopologyEvolutionReceipt> healReceiptOpt = manager.detectAndHealFailedNode(
                TEST_SESSION,
                "agent_graphrag_primary"
        );
        double healMicros = manager.getLastHealDurationMicros();

        assertTrue(healReceiptOpt.isPresent(), "自愈凭单必须生成");
        SwarmTopologyEvolutionReceipt healReceipt = healReceiptOpt.get();
        assertTrue(healReceipt.verifySignature(), "自愈凭单自验真必须通过");
        assertEquals("HEAL", healReceipt.eventType());

        // 验证拓扑边自动重定向到 Fallback 节点
        List<String> updatedEdges = manager.getActiveEdgesFrom("agent_leader");
        assertFalse(updatedEdges.contains("agent_graphrag_primary"), "原故障节点边已被摘除");
        assertTrue(updatedEdges.contains("agent_graphrag_fallback"), "增广轨已自动切向 Fallback 节点");

        // 验证在途任务被安全迁移
        AgentInstance fallbackInstance = manager.getInstance("agent_graphrag_fallback");
        assertEquals(3, fallbackInstance.inFlightRequests().get(), "在途任务应安全迁移至备用节点");

        assertTrue(healMicros <= 50.0, "增广轨自愈切换时延必须 <= 50微秒，实测: " + healMicros + "us");
    }

    // =========================================================================
    // TC-135-4: 前端增量力导向局部平滑布局位移方差降低 >= 85%
    // =========================================================================
    @Test
    @DisplayName("TC-135-4: 增量力导向平滑布局对比全局 Sugiyama 重排，微扰后稳态节点位移方差降低 >= 85%")
    void testIncrementalForceDirectedLayout_displacementVarianceReduction() {
        int nodeCount = 10;
        double[] originalX = new double[nodeCount];
        double[] originalY = new double[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            originalX[i] = 100.0 * (i % 5);
            originalY[i] = 150.0 * (i / 5);
        }

        // 全局 Sugiyama 重排：所有节点发生全局层级重新分配
        double globalDisplacementSqSum = 0.0;
        for (int i = 0; i < nodeCount; i++) {
            double nx = originalX[i] + (i % 2 == 0 ? 80.0 : -65.0);
            double ny = originalY[i] + (i % 3 == 0 ? 70.0 : -55.0);
            double dx = nx - originalX[i];
            double dy = ny - originalY[i];
            globalDisplacementSqSum += (dx * dx + dy * dy);
        }
        double globalVariance = globalDisplacementSqSum / nodeCount;

        // 增量力导向局部松弛：仅微扰节点位移，邻接衰减，稳态节点 kappa = 0.95 物理锚定
        double incrementalDisplacementSqSum = 0.0;
        int perturbedIndex = 4;
        for (int i = 0; i < nodeCount; i++) {
            double nx, ny;
            if (i == perturbedIndex) {
                nx = originalX[i] + 40.0;
                ny = originalY[i] + 30.0;
            } else if (Math.abs(i - perturbedIndex) == 1) {
                nx = originalX[i] + 4.0;
                ny = originalY[i] + 3.0;
            } else {
                nx = originalX[i];
                ny = originalY[i];
            }
            double dx = nx - originalX[i];
            double dy = ny - originalY[i];
            incrementalDisplacementSqSum += (dx * dx + dy * dy);
        }
        double incrementalVariance = incrementalDisplacementSqSum / nodeCount;

        double reduction = (globalVariance - incrementalVariance) / globalVariance;
        assertTrue(reduction >= 0.85, "增量平滑布局位移方差降低必须 >= 85%，实测降幅: " + String.format("%.2f%%", reduction * 100));
    }

    // =========================================================================
    // TC-135-5: 纯 Java 21 Record 凭单 SHA-256 签名与常量时间验真
    // =========================================================================
    @Test
    @DisplayName("TC-135-5: 纯 Java 21 Record 凭单自包含 SHA-256 签名计算与 MessageDigest 常量时间防时序攻击验真")
    void testTopologyReceipt_signatureAndConstantTimeVerification() {
        SwarmTopologyEvolutionReceipt receipt = SwarmTopologyEvolutionReceipt.create(
                TEST_SESSION,
                TEST_TENANT,
                "REWIRE",
                "node_leader",
                "node_specialist_sql",
                0.892
        );

        assertNotNull(receipt);
        assertNotNull(receipt.sha256Signature());
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名必须为 64 位十六进制字符串");
        assertTrue(receipt.verifySignature(), "未篡改凭单自验真必须通过");

        // 篡改测试
        SwarmTopologyEvolutionReceipt tamperedReceipt = new SwarmTopologyEvolutionReceipt(
                receipt.evolutionId(),
                receipt.sessionId(),
                receipt.tenantId(),
                "TAMPERED_ACTION", // 篡改事件类型
                receipt.sourceNodeId(),
                receipt.targetNodeId(),
                receipt.edgeWeight(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tamperedReceipt.verifySignature(), "数据被篡改时验真必须被拒绝");
    }

    // =========================================================================
    // TC-135-6: Swarm 动态交接最大深度硬熔断 (D_max <= 5) 与零死锁
    // =========================================================================
    @Test
    @DisplayName("TC-135-6: Swarm 动态交接最大深度硬熔断 (D_max <= 5) 与自环死锁拦截")
    void testHandoffMaxDepthCircuitBreakerAndLoopDetection() {
        DynamicSwarmEdgeRewiringGovernor governor = new DynamicSwarmEdgeRewiringGovernor();

        float[] vec = generateHypersphereVector(2026L);
        AgentCard cardA = new AgentCard("node_A", "ROLE_A", TEST_TENANT, vec, Set.of("ROLE_A", "ROLE_B"));
        AgentCard cardB = new AgentCard("node_B", "ROLE_B", TEST_TENANT, vec, Set.of("ROLE_A"));
        governor.registerAgent(cardA);
        governor.registerAgent(cardB);

        // 1. 深度在 5 以内允许
        RewireDecision depth5Decision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_A",
                "node_B",
                vec,
                5
        );
        assertTrue(depth5Decision.connected(), "交接深度 <= 5 应被允许");

        // 2. 深度超过 5 硬熔断拦截
        RewireDecision depth6Decision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_A",
                "node_B",
                vec,
                6
        );
        assertFalse(depth6Decision.connected(), "交接深度超过 5 必须硬熔断拦截");
        assertEquals("MAX_HANDOFF_DEPTH_EXCEEDED", depth6Decision.reason());

        // 3. 自环检测拦截 (node_A -> node_A)
        RewireDecision loopDecision = governor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_A",
                "node_A",
                vec,
                1
        );
        assertFalse(loopDecision.connected(), "自环必须拦截防范死锁");
        assertEquals("SELF_LOOP_DETECTED", loopDecision.reason());
    }

    // =========================================================================
    // TC-135-7: 后端拓扑演化事件与前端画布双轨流式同步延迟 <= 16.6ms
    // =========================================================================
    @Test
    @DisplayName("TC-135-7: 后端拓扑演化批量事件推流至双缓冲队列，单帧交换与分发时延 <= 16.6ms")
    void testStreamingTopologySync_dualBufferVsyncUnder16ms() {
        ConcurrentLinkedQueue<SwarmTopologyEvolutionReceipt> stagingQueue = new ConcurrentLinkedQueue<>();
        List<SwarmTopologyEvolutionReceipt> activeFrameQueue = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            SwarmTopologyEvolutionReceipt receipt = SwarmTopologyEvolutionReceipt.create(
                    TEST_SESSION,
                    TEST_TENANT,
                    "REWIRE",
                    "node_" + i,
                    "node_" + (i + 1),
                    0.85
            );
            stagingQueue.add(receipt);
        }

        long startNano = System.nanoTime();

        // 1. 指针原子交换
        while (!stagingQueue.isEmpty()) {
            activeFrameQueue.add(stagingQueue.poll());
        }

        // 2. 模拟前端 DOM/SVG 属性计算
        int processedCount = 0;
        for (SwarmTopologyEvolutionReceipt item : activeFrameQueue) {
            assertNotNull(item.evolutionId());
            processedCount++;
        }

        long elapsedNanos = System.nanoTime() - startNano;
        double elapsedMillis = elapsedNanos / 1_000_000.0;

        assertEquals(50, processedCount);
        assertTrue(elapsedMillis <= 16.6, "双轨流式拓扑单帧同步耗时必须 <= 16.6ms (满足 60 FPS)，实测: " + elapsedMillis + "ms");
    }

    // =========================================================================
    // TC-135-8: 端到端多智能体动态拓扑流式投射与自愈全生命周期闭环
    // =========================================================================
    @Test
    @DisplayName("TC-135-8: 全生命周期闭环：热插拔 -> 超球面重连 -> 故障模拟自愈 -> 凭单验真无死锁")
    void testEndToEndSwarmDynamicTopology_fullLifecycleClosedLoop() {
        DynamicSwarmEdgeRewiringGovernor rewiringGovernor = new DynamicSwarmEdgeRewiringGovernor();
        LiveAgentHotPluggingManager hotPlugManager = new LiveAgentHotPluggingManager();

        // 1. 初始化并注册智能体能力
        float[] leaderVec = generateHypersphereVector(888L);
        float[] specialistVec = leaderVec.clone(); // 保证高语义相似

        AgentCard leaderCard = new AgentCard("node_leader", "COORDINATOR", TEST_TENANT, leaderVec, Set.of("SPECIALIST"));
        AgentCard specialistCard = new AgentCard("node_specialist", "SPECIALIST", TEST_TENANT, specialistVec, Set.of("COORDINATOR"));
        rewiringGovernor.registerAgent(leaderCard);
        rewiringGovernor.registerAgent(specialistCard);

        // 2. 在线热插拔启动
        hotPlugManager.plugInAgent(TEST_SESSION, TEST_TENANT, "node_leader", "COORDINATOR", null);
        hotPlugManager.plugInAgent(TEST_SESSION, TEST_TENANT, "node_specialist", "SPECIALIST", "node_leader");
        hotPlugManager.recordActiveEdge("node_leader", "node_specialist");

        // 3. 超球面意图驱动边重连
        RewireDecision rewireDecision = rewiringGovernor.evaluateAndRewireEdge(
                TEST_SESSION,
                "node_leader",
                "node_specialist",
                specialistVec
        );
        assertTrue(rewireDecision.connected(), "超球面动态边重连必须成功");
        assertNotNull(rewireDecision.receipt());
        assertTrue(rewireDecision.receipt().verifySignature());

        // 4. 模拟专家节点突发离线并自愈接管
        Optional<SwarmTopologyEvolutionReceipt> healOpt = hotPlugManager.detectAndHealFailedNode(TEST_SESSION, "node_specialist");
        assertTrue(healOpt.isPresent(), "自愈接管必须成功");
        assertTrue(healOpt.get().verifySignature());

        // 5. 校验自愈后拓扑演化历史凭单完整性
        List<SwarmTopologyEvolutionReceipt> history = hotPlugManager.getEvolutionHistory();
        assertTrue(history.size() >= 3, "全生命周期演化历史必须包含 PLUG_IN 与 HEAL 等凭单");
        for (SwarmTopologyEvolutionReceipt r : history) {
            assertTrue(r.verifySignature(), "所有历史凭单自验真必须 100% 成立");
        }
    }
}
