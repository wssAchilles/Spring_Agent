package tech.qiantong.qknow.hermes.flow.stategraph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.flow.stategraph.engine.StateGraphHypersphereProjector;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StateGraphHypersphereProjector 千问 1536 维超球面投影测试")
class StateGraphHypersphereProjectorTest {

    private StateGraphHypersphereProjector projector;

    @BeforeEach
    void setUp() {
        projector = new StateGraphHypersphereProjector(1536);
    }

    @Test
    @DisplayName("投影向量严格满足 L2 范数单位模长 ||v|| = 1.0 +/- 10^-4")
    void hypersphere_projection_normalizedUnitNorm() {
        double[] rawVector = new double[1536];
        for (int i = 0; i < 1536; i++) {
            rawVector[i] = ThreadLocalRandom.current().nextDouble(-10.0, 10.0);
        }

        double[] normalized = projector.projectToHypersphere(rawVector);
        assertNotNull(normalized);
        assertEquals(1536, normalized.length);

        double norm = projector.computeL2Norm(normalized);
        assertEquals(1.0, norm, 1e-4, "超球面投影模长必须在 1.0 +/- 10^-4 误差范围内");
    }

    @Test
    @DisplayName("测地距离度量严格满足度量空间公理（非负、对称与三角不等式）")
    void hypersphere_geodesicDistance_satisfiesMetric() {
        double[] u = projector.createRandomUnitVector();
        double[] v = projector.createRandomUnitVector();
        double[] w = projector.createRandomUnitVector();

        // 1. 同一性
        double dUU = projector.computeGeodesicDistance(u, u);
        assertEquals(0.0, dUU, 1e-6, "自身与自身测地距离必须为 0");

        // 2. 对称性
        double dUV = projector.computeGeodesicDistance(u, v);
        double dVU = projector.computeGeodesicDistance(v, u);
        assertEquals(dUV, dVU, 1e-6, "测地距离必须严格对称");

        // 3. 非负性与有界性
        assertTrue(dUV >= 0.0 && dUV <= 1.0, "归一化测地距离必须落在 [0, 1] 区间内");

        // 4. 三角不等式 d(u, w) <= d(u, v) + d(v, w)
        double dUW = projector.computeGeodesicDistance(u, w);
        double dVW = projector.computeGeodesicDistance(v, w);
        assertTrue(dUW <= dUV + dVW + 1e-6, "测地距离必须严格满足三角不等式公理");
    }

    @Test
    @DisplayName("全零或极小向量自适应正则化保模防崩溃")
    void hypersphere_zeroVector_regularizedSafely() {
        double[] zeroVec = new double[1536];
        double[] projected = projector.projectToHypersphere(zeroVec);

        assertNotNull(projected);
        double norm = projector.computeL2Norm(projected);
        assertEquals(1.0, norm, 1e-4, "零向量自适应规整后模长依然必须严格为 1.0");
    }
}
