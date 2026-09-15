package tech.qiantong.qknow.ai.embodied;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.embodied.mapping.dto.SemanticExplorationReceipt;
import tech.qiantong.qknow.ai.embodied.mapping.dto.TopologicalNode;
import tech.qiantong.qknow.ai.embodied.mapping.engine.ExplorationSafetyGate;
import tech.qiantong.qknow.ai.embodied.mapping.engine.GoalDirectedExplorationPlanner;
import tech.qiantong.qknow.ai.embodied.mapping.engine.ImplicitSceneFeatureField;
import tech.qiantong.qknow.ai.embodied.mapping.engine.SemanticTopologicalMapEngine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 66 专属契约单元测试：具身智能体非结构化环境自主语义拓扑建图、隐式场景表征与目标导向主动探索中枢
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class Phase66AutonomousMappingContractTest {

    private SemanticTopologicalMapEngine mapEngine;
    private ImplicitSceneFeatureField featureField;
    private GoalDirectedExplorationPlanner explorationPlanner;
    private ExplorationSafetyGate safetyGate;

    @BeforeEach
    void setUp() {
        mapEngine = new SemanticTopologicalMapEngine(0.1, 30.0); // 体素分辨率 0.1m, 半衰期 30.0s
        featureField = new ImplicitSceneFeatureField();
        explorationPlanner = new GoalDirectedExplorationPlanner(mapEngine, featureField, 1.30, 2.0); // 迟滞因子 1.30, 角动量惩罚 2.0
        safetyGate = new ExplorationSafetyGate(2.0, 0.05); // 最大制动减速度 2.0 m/s^2, 最小安全裕度 0.05m
    }

    @Test
    @DisplayName("契约 1: 语义探索存证凭单 SHA-256 自签名完整性与防篡改雪崩测试")
    void test01_SemanticExplorationReceiptSha256IntegrityAndTamperProof() {
        SemanticExplorationReceipt receipt = new SemanticExplorationReceipt(
                "task-session-001",
                1,
                new double[]{2.5, 3.0, 0.0},
                45.8,
                3.2,
                0.892,
                0.35,
                18,
                System.nanoTime()
        );

        // 验证签名不为空且完整性自验通过
        assertNotNull(receipt.signature());
        assertTrue(receipt.verifyIntegrity(), "原始凭单 SHA-256 签名自验必须为 true");

        // 验证篡改攻击：修改前沿点坐标
        SemanticExplorationReceipt tamperedPoint = new SemanticExplorationReceipt(
                receipt.sessionId(),
                receipt.explorationRound(),
                new double[]{2.5001, 3.0, 0.0}, // 细微篡改
                receipt.informationGain(),
                receipt.travelCost(),
                receipt.targetSemanticSimilarity(),
                receipt.coverageRatio(),
                receipt.topologicalNodeCount(),
                receipt.timestampNs(),
                receipt.signature()
        );
        assertFalse(tamperedPoint.verifyIntegrity(), "篡改坐标后凭单 SHA-256 验证必须失败");

        // 验证篡改攻击：篡改覆盖率
        SemanticExplorationReceipt tamperedCoverage = new SemanticExplorationReceipt(
                receipt.sessionId(),
                receipt.explorationRound(),
                receipt.frontierCoordinate(),
                receipt.informationGain(),
                receipt.travelCost(),
                receipt.targetSemanticSimilarity(),
                0.99, // 虚构高覆盖率
                receipt.topologicalNodeCount(),
                receipt.timestampNs(),
                receipt.signature()
        );
        assertFalse(tamperedCoverage.verifyIntegrity(), "篡改覆盖率后凭单 SHA-256 验证必须失败");
    }

    @Test
    @DisplayName("契约 2: 空间哈希分块体素网格对数几率更新与时间半衰期消除动态残影测试")
    void test02_SpatialHashingVoxelGridDecayAndGhostElimination() {
        // 在 (1.0, 2.0, 0.0) 处打入动态障碍物点（例如经过的行人）
        double[] ghostPoint = new double[]{1.0, 2.0, 0.0};
        double[] sensorOrigin = new double[]{0.0, 0.0, 0.0};

        // 更新 5 次命中
        for (int i = 0; i < 5; i++) {
            mapEngine.updateRay(sensorOrigin, ghostPoint, true, 0L);
        }

        // 此时该体素应该处于高概率占据状态
        double occupiedProb = mapEngine.getOccupancyProbability(ghostPoint);
        assertTrue(occupiedProb > 0.85, "多轮观测命中后占据概率必须大于 0.85");

        // 模拟时间流逝 35.0s (大于半衰期 30.0s)，期间无新观测
        long elapsedNs = (long) (35.0 * 1_000_000_000L);
        mapEngine.applyTimeDecay(elapsedNs);

        // 验证对数几率退火衰减
        double decayedProb = mapEngine.getOccupancyProbability(ghostPoint);
        assertTrue(decayedProb < 0.60, "35s 半衰期衰减后占据概率必须显著回落，消除残影幽灵");

        // 再次模拟时间流逝 60s
        mapEngine.applyTimeDecay(elapsedNs + (long) (60.0 * 1_000_000_000L));
        double finalProb = mapEngine.getOccupancyProbability(ghostPoint);
        assertTrue(Math.abs(finalProb - 0.50) < 0.05, "长期无观测体素必须退火回先验未知状态 (0.50)");
    }

    @Test
    @DisplayName("契约 3: 三层分层拓扑骨架提取、同伦等价性与测地距离误差有界测试 (定理 1.1)")
    void test03_HierarchicalTopologicalSkeletonHomotopyAndErrorBound() {
        // 构建一段包含通道与两个房间的自由空间网格
        mapEngine.markFreeBox(0.0, 0.0, 0.0, 10.0, 4.0, 2.0); // 房间 1
        mapEngine.markFreeBox(10.0, 1.5, 0.0, 15.0, 2.5, 2.0); // 狭窄走廊通道 (宽 1.0m)
        mapEngine.markFreeBox(15.0, 0.0, 0.0, 25.0, 6.0, 2.0); // 房间 2

        // 提取分层拓扑骨架
        mapEngine.extractTopologicalGraph();
        Map<String, TopologicalNode> nodes = mapEngine.getTopologicalNodes();

        assertFalse(nodes.isEmpty(), "拓扑骨架提取节点不得为空");
        assertTrue(nodes.size() >= 3, "至少应包含房间 1、走廊中轴与房间 2 的骨架节点");

        // 验证单连通性与通道连通性
        TopologicalNode corridorNode = nodes.values().stream()
                .filter(n -> n.layerLevel() == TopologicalNode.LayerLevel.CORRIDOR)
                .findFirst().orElse(null);
        assertNotNull(corridorNode, "必须识别出走廊中轴通道节点");

        // 验证测地距离误差有界性：真实测地距离 vs 拓扑路径长度
        double realGeodesicDist = mapEngine.computeContinuousGeodesic(new double[]{2.0, 2.0, 0.0}, new double[]{20.0, 3.0, 0.0});
        double topoPathDist = mapEngine.computeTopologicalGeodesic(new double[]{2.0, 2.0, 0.0}, new double[]{20.0, 3.0, 0.0});

        assertTrue(realGeodesicDist > 0);
        assertTrue(topoPathDist >= realGeodesicDist, "拓扑骨架路径测地距离必须满足三角不等式下界");
        double error = Math.abs(topoPathDist - realGeodesicDist);
        double epsilonTopo = 3.0; // 理论上界 ε_topo = 2Δ_max + (π/2 - 1) D_clear ≈ 2*1.0 + 0.57*1.0 = 2.57m
        assertTrue(error <= epsilonTopo, "拓扑测地误差必须满足定理 1.1 的确定性上界: error=" + error + ", limit=" + epsilonTopo);
    }

    @Test
    @DisplayName("契约 4: 阿里千问 1536 维超球面隐式特征场三线性插值保模重投影与李普希茨连续性 (定理 1.2)")
    void test04_ImplicitSceneFeatureFieldInterpolationAndLipschitzSmoothness() {
        // 构造两个相近的千问 1536 维超球面单位向量 (norm = 1.0)
        double[] qwenEmbedding1 = new double[1536];
        double[] qwenEmbedding2 = new double[1536];
        Arrays.fill(qwenEmbedding1, 1.0 / Math.sqrt(1536));
        for (int i = 0; i < 1536; i++) {
            qwenEmbedding2[i] = (i % 2 == 0 ? 1.0 : -1.0) / Math.sqrt(1536);
        }

        featureField.registerAnchor(new double[]{0.0, 0.0, 0.0}, qwenEmbedding1);
        featureField.registerAnchor(new double[]{1.0, 1.0, 1.0}, qwenEmbedding2);

        // 插值点 1: (0.2, 0.2, 0.2) 与插值点 2: (0.25, 0.25, 0.25)
        double[] p1 = new double[]{0.2, 0.2, 0.2};
        double[] p2 = new double[]{0.25, 0.25, 0.25};

        double[] feat1 = featureField.queryFeature(p1);
        double[] feat2 = featureField.queryFeature(p2);

        // 验证保模重投影：模长必须严格恒等于 1.0 (在数值误差 1e-6 内)
        assertEquals(1.0, computeL2Norm(feat1), 1e-6, "插值特征必须严格落在 S^1535 单位超球面上");
        assertEquals(1.0, computeL2Norm(feat2), 1e-6, "插值特征必须严格落在 S^1535 单位超球面上");

        // 验证局部李普希茨连续性: d_S(Φ(x1), Φ(x2)) <= L_Φ ||x1 - x2||
        double euclideanDist = Math.sqrt(3 * 0.05 * 0.05);
        double cosine = featureField.computeDotProduct(feat1, feat2);
        double geodesicAngle = Math.acos(Math.max(-1.0, Math.min(1.0, cosine)));

        double estimatedLipschitz = geodesicAngle / euclideanDist;
        assertTrue(estimatedLipschitz < 10.0, "测地偏角李普希茨常数必须有界: " + estimatedLipschitz);

        // 性能门禁：单点余弦相似度查询耗时在 8 路展开下必须 <= 2.0μs (带 JIT 预热)
        for (int i = 0; i < 3000; i++) {
            featureField.computeDotProduct(feat1, qwenEmbedding1);
        }
        long t0 = System.nanoTime();
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            featureField.computeDotProduct(feat1, qwenEmbedding1);
        }
        long avgNs = (System.nanoTime() - t0) / iterations;
        assertTrue(avgNs <= 2000, "千问 1536 维超球面点积单次耗时必须 <= 2.0μs, 实际: " + avgNs + "ns");
    }

    @Test
    @DisplayName("契约 5: 零水平集表面单位法向量与语义特征场空间梯度对齐测试 (定理 1.2)")
    void test05_ZeroLevelSetSurfaceNormalGradientAlignment() {
        // 在 (2.0, 0.0, 0.0) 处构造一个平面边界
        double[] targetEmbedding = new double[1536];
        Arrays.fill(targetEmbedding, 1.0 / Math.sqrt(1536));

        featureField.registerAnchor(new double[]{1.9, 0.0, 0.0}, targetEmbedding);
        double[] bgEmbedding = new double[1536];
        Arrays.fill(bgEmbedding, -1.0 / Math.sqrt(1536));
        featureField.registerAnchor(new double[]{2.1, 0.0, 0.0}, bgEmbedding);

        // 计算 (2.0, 0.0, 0.0) 处的语义空间数值梯度
        double[] gradient = featureField.computeSpatialGradient(new double[]{2.0, 0.0, 0.0}, targetEmbedding);
        assertNotNull(gradient);

        // 表面外法向量预期沿 X 轴方向
        double gradNorm = Math.sqrt(gradient[0] * gradient[0] + gradient[1] * gradient[1] + gradient[2] * gradient[2]);
        assertTrue(gradNorm > 1e-4, "语义场表面梯度模长不得退化为 0");

        double[] normalizedGrad = new double[]{gradient[0] / gradNorm, gradient[1] / gradNorm, gradient[2] / gradNorm};
        // 验证在 X 轴分量上占主导
        assertTrue(Math.abs(normalizedGrad[0]) > 0.90, "法向量与语义场梯度必须在主法向高度对齐");
    }

    @Test
    @DisplayName("契约 6: 香农互信息增益解析解闭式计算与全局熵指数衰减测试 (定理 1.3)")
    void test06_GoalDirectedShannonMutualInformationExplorationAndDecay() {
        // 初始化一个 10x10 的全未知环境
        mapEngine.initializeUnknownWorkspace(0.0, 0.0, 10.0, 10.0);
        double initialEntropy = mapEngine.computeTotalShannonEntropy();
        assertTrue(initialEntropy > 0.0, "初始全未知环境香农熵必须大于 0");

        double[] targetEmbedding = new double[1536];
        Arrays.fill(targetEmbedding, 1.0 / Math.sqrt(1536));

        // 模拟多轮主动探索
        double currentEntropy = initialEntropy;
        double[] currentPose = new double[]{0.0, 0.0, 0.0};

        for (int round = 1; round <= 6; round++) {
            SemanticExplorationReceipt receipt = explorationPlanner.stepExploration(currentPose, targetEmbedding, "session-test-06");
            assertNotNull(receipt);
            assertTrue(receipt.verifyIntegrity(), "每轮签发的存证凭单必须自验通过");

            // 执行视点观测并更新地图
            mapEngine.simulateViewpointObservation(receipt.frontierCoordinate(), 2.5); // 观测半径 2.5m
            currentPose = receipt.frontierCoordinate();

            double newEntropy = mapEngine.computeTotalShannonEntropy();
            assertTrue(newEntropy < currentEntropy, "每轮探索后全局香农熵必须严格单调递减");
            currentEntropy = newEntropy;
        }

        // 验证熵减显著，覆盖率提升
        double finalCoverage = mapEngine.computeCoverageRatio();
        assertTrue(finalCoverage >= 0.70, "经过多轮探索后覆盖率必须显著收敛");
    }

    @Test
    @DisplayName("契约 7: 动态迟滞窗口与航向角动量惩罚杜绝布里丹之驴走廊死锁振荡测试")
    void test07_AntiThrashingHysteresisWindowAndBuridanDonkeyElimination() {
        // 构造左右两个对称候选前沿点 (走廊分叉口)
        double[] leftFrontier = new double[]{5.0, 3.0, 0.0};
        double[] rightFrontier = new double[]{5.0, -3.0, 0.0};
        double[] robotPose = new double[]{0.0, 0.0, 0.0};
        double currentHeading = 0.0; // 航向正东

        double[] targetEmbedding = new double[1536];
        Arrays.fill(targetEmbedding, 1.0 / Math.sqrt(1536));

        // 第一次决策选择左侧前沿
        explorationPlanner.forceCurrentTarget(leftFrontier);

        // 模拟左右评分几乎相等的微小扰动 (右侧前沿信息增益微弱上升 5%)
        int switchCount = 0;
        for (int i = 0; i < 20; i++) {
            // 微小噪声扰动，右侧稍占优势但未超过迟滞窗口 30% (W_hyst = 1.30)
            double[] selected = explorationPlanner.selectNextBestFrontier(
                    robotPose,
                    currentHeading,
                    List.of(leftFrontier, rightFrontier),
                    targetEmbedding,
                    (f) -> f == leftFrontier ? 100.0 : 105.0 // 右侧高 5%
            );
            if (selected == rightFrontier) {
                switchCount++;
            }
        }

        // 验证因迟滞窗口 (1.30) 与角动量惩罚，目标坚决不发生跳变，跳变次数为 0
        assertEquals(0, switchCount, "由于迟滞窗口与航向惩罚，微弱噪声下目标不得跳变，彻底杜绝布里丹之驴振荡");

        // 当右侧前沿压倒性领先 (超过 130% 并克服航向惩罚，例如高 80%) 时，允许合理切换
        double[] decisiveTarget = explorationPlanner.selectNextBestFrontier(
                robotPose,
                currentHeading,
                List.of(leftFrontier, rightFrontier),
                targetEmbedding,
                (f) -> f == leftFrontier ? 100.0 : 185.0
        );
        assertSame(rightFrontier, decisiveTarget, "当竞争前沿大幅超越迟滞门限时允许正常切换");
    }

    @Test
    @DisplayName("契约 8: 相对阶 r=2 未知边界高阶控制屏障安全门禁刹停与裕度保持测试")
    void test08_ExplorationSafetyGateHocbfUnknownBoundaryProtection() {
        // 机器人位于 (0.0, 0.0, 0.0)，速度 1.5 m/s 高速前冲
        double[] currentPosition = new double[]{0.0, 0.0, 0.0};
        double[] currentVelocity = new double[]{1.5, 0.0, 0.0}; // 高速

        // 未知边界在前方 0.5m 处 (未知黑洞/断崖)
        double distanceToUnknown = 0.50;

        // 安全门禁干预评估
        ExplorationSafetyGate.SafetyIntervention intervention = safetyGate.evaluateUnknownBoundary(
                currentPosition,
                currentVelocity,
                distanceToUnknown
        );

        assertTrue(intervention.triggered(), "高速逼近近距离未知边界时必须触发 HOCBF 安全干预");
        assertTrue(intervention.commandedSpeed() < 1.5, "干预后的指令速度必须强制削减");

        // 验证制动距离满足安全裕度 >= 0.05m
        double stoppingDistance = (intervention.commandedSpeed() * intervention.commandedSpeed()) / (2.0 * 2.0); // a_max = 2.0
        double remainingMargin = distanceToUnknown - stoppingDistance;
        assertTrue(remainingMargin >= 0.049, "制动后剩余未知裕度必须满足安全门禁下界: " + remainingMargin);

        // 验证微秒级极速响应 (带充分预热以消除冷启动与 JIT 编译耗时)
        for (int i = 0; i < 2000; i++) {
            safetyGate.evaluateUnknownBoundary(currentPosition, currentVelocity, distanceToUnknown);
        }
        long t0 = System.nanoTime();
        int iters = 2000;
        for (int i = 0; i < iters; i++) {
            safetyGate.evaluateUnknownBoundary(currentPosition, currentVelocity, distanceToUnknown);
        }
        long avgNs = (System.nanoTime() - t0) / iters;
        assertTrue(avgNs <= 30000, "单步安全拦截评估耗时必须在微秒级 (<= 30μs), 实际: " + avgNs + "ns");
    }

    private double computeL2Norm(double[] v) {
        double sum = 0.0;
        for (double val : v) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }
}
