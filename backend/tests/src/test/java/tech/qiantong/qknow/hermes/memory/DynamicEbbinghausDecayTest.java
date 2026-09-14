package tech.qiantong.qknow.hermes.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicEbbinghausDecay 动态艾宾浩斯强化衰减测试 (Theorem 1.1 & 1.2)")
class DynamicEbbinghausDecayTest {

    @Test
    @DisplayName("Theorem 1.1 验证：记忆强度与半衰期随唤醒次数严格单调递增")
    void testStrengthMonotonicGrowth() {
        double currentStrength = 30.0; // 初始 30 天
        double alpha = 0.2; // 强化塑性系数

        double prevHalfLife = DynamicEbbinghausDecay.computeHalfLife(currentStrength);
        double strength = currentStrength;

        for (int k = 1; k <= 10; k++) {
            strength = DynamicEbbinghausDecay.computeNextStrength(strength, k, alpha);
            double halfLife = DynamicEbbinghausDecay.computeHalfLife(strength);

            // 增量严格正定
            assertTrue(halfLife > prevHalfLife, "第 " + k + " 次唤醒后半衰期必须严格大于前一次");
            assertTrue(strength > currentStrength, "强度必须严格递增");
            prevHalfLife = halfLife;
        }

        // 10 次唤醒后强度显著高于初始值
        assertTrue(strength > 30.0 * 2.5, "10次唤醒后强度应增长 2.5 倍以上");
    }

    @Test
    @DisplayName("Theorem 1.2 验证：高重要性记忆渐进不遗忘性 (Asymptotic Non-Forgetting)")
    void testImportanceModulationNonForgetting() {
        // 普通记忆 I = 0.5
        double sNorm = DynamicEbbinghausDecay.computeInitialStrength(0.5);
        // 高重要性记忆 I = 0.95 (如安全核心偏好)
        double sHigh = DynamicEbbinghausDecay.computeInitialStrength(0.95);

        assertTrue(sHigh > sNorm * 5.0, "高重要性记忆初始强度应远大于普通记忆");

        long now = System.currentTimeMillis();
        long sixtyDaysAgo = now - 60L * 86400000L;

        double rNorm = DynamicEbbinghausDecay.computeRetention(sixtyDaysAgo, sNorm, now);
        double rHigh = DynamicEbbinghausDecay.computeRetention(sixtyDaysAgo, sHigh, now);

        assertTrue(rHigh >= 0.85, "高重要性记忆在60天后留存率应保持在 85% 以上");
        assertTrue(rHigh > rNorm * 2.0, "高重要性留存率应远高于普通记忆");
    }

    @Test
    @DisplayName("边界值与数值鲁棒性测试：防溢出与非负约束")
    void testNumericalRobustness() {
        // 唤醒次数为 0
        double s0 = DynamicEbbinghausDecay.computeNextStrength(30.0, 0, 0.2);
        assertEquals(30.0, s0, 1e-6, "k=0 时强度不应改变");

        // 极大唤醒次数不会抛异常，且受对数抑制
        double sHuge = DynamicEbbinghausDecay.computeNextStrength(30.0, 100000, 0.2);
        assertFalse(Double.isNaN(sHuge));
        assertFalse(Double.isInfinite(sHuge));
        assertTrue(sHuge > 30.0);

        // 负数与非法输入防守
        double sInvalid = DynamicEbbinghausDecay.computeNextStrength(-10.0, -1, -0.5);
        assertTrue(sInvalid > 0.0, "非法输入应兜底为正数基线");

        // 时间在未来 (时钟偏差)
        long futureTime = System.currentTimeMillis() + 10000L;
        double rFuture = DynamicEbbinghausDecay.computeRetention(futureTime, 30.0, System.currentTimeMillis());
        assertEquals(1.0, rFuture, 1e-6, "时间戳超前时留存率应被截断为 1.0");
    }
}
