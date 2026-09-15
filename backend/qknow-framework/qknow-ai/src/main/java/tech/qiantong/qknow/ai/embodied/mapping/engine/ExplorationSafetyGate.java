package tech.qiantong.qknow.ai.embodied.mapping.engine;

/**
 * 未知边界高阶控制屏障 (HOCBF) 安全门禁与碰撞断路器 (ExplorationSafetyGate)
 * 相对阶 r=2 物理制动约束: v <= sqrt(2 * a_max * (d_unknown - d_margin))
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class ExplorationSafetyGate {

    private final double maxDeceleration; // 最大制动减速度 a_max (m/s^2)
    private final double minSafetyMargin; // 最小安全裕度 d_margin (m)

    public record SafetyIntervention(boolean triggered, double commandedSpeed, String reason) {}

    public ExplorationSafetyGate(double maxDeceleration, double minSafetyMargin) {
        this.maxDeceleration = maxDeceleration > 0 ? maxDeceleration : 2.0;
        this.minSafetyMargin = minSafetyMargin > 0 ? minSafetyMargin : 0.05;
    }

    /**
     * 评估逼近未知边界的二阶动力学安全性
     */
    public SafetyIntervention evaluateUnknownBoundary(double[] currentPos, double[] currentVel, double distanceToUnknown) {
        double currentSpeed = Math.sqrt(currentVel[0] * currentVel[0] + currentVel[1] * currentVel[1] + currentVel[2] * currentVel[2]);

        // 有效安全距离净空
        double effectiveClearance = Math.max(0.0, distanceToUnknown - minSafetyMargin);

        // 相对阶 r=2 二阶动力学速度允许上限
        double allowableSpeed = Math.sqrt(2.0 * maxDeceleration * effectiveClearance);

        if (currentSpeed > allowableSpeed) {
            // 触发安全门禁介入，平滑降速至允许速度
            return new SafetyIntervention(
                    true,
                    allowableSpeed,
                    "Triggered HOCBF intervention: speed " + currentSpeed + " exceeds allowable " + allowableSpeed + " for unknown boundary distance " + distanceToUnknown
            );
        }

        return new SafetyIntervention(false, currentSpeed, "Safe: speed within HOCBF barrier boundary");
    }
}
