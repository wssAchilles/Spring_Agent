package tech.qiantong.qknow.ai.embodied.actuation.engine;

/**
 * 相对阶 r=2 高阶控制屏障证书 (HOCBF) 与微秒级 QP 解析投影安全拦截器 (Theorem 1.2)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class HighOrderBarrierGovernor {

    private final double[] obstaclePos;
    private final double safeRadius;
    private final double k1;
    private final double k2;
    private final double maxAcc;

    public record GovernorResult(
            double[] safeAcceleration,
            boolean isIntervened,
            double barrierMargin,
            String status
    ) {}

    public HighOrderBarrierGovernor(double[] obstaclePos, double safeRadius, double k1, double k2, double maxAcc) {
        this.obstaclePos = obstaclePos != null ? obstaclePos.clone() : new double[]{0.0, 0.0, 0.0};
        this.safeRadius = safeRadius > 0 ? safeRadius : 0.2;
        this.k1 = k1 > 0 ? k1 : 2.0;
        this.k2 = k2 > 0 ? k2 : 2.0;
        this.maxAcc = maxAcc > 0 ? maxAcc : 5.0;
    }

    /**
     * 针对相对阶 r=2 的高阶安全屏障二次规划 (QP) 求解
     */
    public GovernorResult filter(double[] pos, double[] vel, double[] nominalAcc) {
        if (pos == null || vel == null || nominalAcc == null) {
            return new GovernorResult(new double[]{0, 0, 0}, false, 0.0, "INVALID_INPUT");
        }

        double dx = pos[0] - obstaclePos[0];
        double dy = pos[1] - obstaclePos[1];
        double dz = pos[2] - obstaclePos[2];
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // 零阶屏障 h(x) = ||p - p_obs||^2 - R_safe^2
        double h = dist * dist - safeRadius * safeRadius;

        // 一阶导数 h_dot = 2 (p - p_obs)^T v
        double hDot = 2.0 * (dx * vel[0] + dy * vel[1] + dz * vel[2]);

        // 一阶屏障 psi_1 = h_dot + k1 * h
        double psi1 = hDot + k1 * h;

        // 二阶屏障不等式: A_cbf^T u >= b_cbf
        // A_cbf = 2 (p - p_obs)
        // b_cbf = -2 ||v||^2 - (k1 + k2) * h_dot - k1 * k2 * h
        double velSq = vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2];
        double[] aCbf = new double[]{2.0 * dx, 2.0 * dy, 2.0 * dz};
        double bCbf = -2.0 * velSq - (k1 + k2) * hDot - k1 * k2 * h;

        double aDotNom = aCbf[0] * nominalAcc[0] + aCbf[1] * nominalAcc[1] + aCbf[2] * nominalAcc[2];

        // 1. 若标称加速度满足二阶屏障约束，且非紧急逼近，直接放行
        if (aDotNom >= bCbf && (h > 0.05 || hDot >= 0)) {
            return new GovernorResult(nominalAcc.clone(), false, h, "SAFE_NOMINAL");
        }

        // 2. 违反屏障约束或朝向障碍物高速逼近，执行解析正交 QP 投影
        double aNormSq = aCbf[0] * aCbf[0] + aCbf[1] * aCbf[1] + aCbf[2] * aCbf[2];
        if (aNormSq < 1e-9) {
            return emergencyBraking(vel, h);
        }

        double lambda = Math.max(0.0, (bCbf - aDotNom) / aNormSq);
        double[] projectedAcc = new double[3];
        for (int i = 0; i < 3; i++) {
            projectedAcc[i] = nominalAcc[i] + lambda * aCbf[i];
            // 幅值裁剪
            projectedAcc[i] = Math.max(-maxAcc, Math.min(maxAcc, projectedAcc[i]));
        }

        // 3. 紧急防护：若距离已接近安全半径且速度仍冲向障碍物，强化最大反向制动
        if (hDot < 0 && (dist - safeRadius < 0.15)) {
            double velNorm = Math.sqrt(velSq);
            if (velNorm > 1e-6) {
                for (int i = 0; i < 3; i++) {
                    projectedAcc[i] = -maxAcc * (vel[i] / velNorm);
                }
            }
            return new GovernorResult(projectedAcc, true, h, "EMERGENCY_BRAKED");
        }

        return new GovernorResult(projectedAcc, true, h, "BARRIER_PROJECTED");
    }

    private GovernorResult emergencyBraking(double[] vel, double h) {
        double velNorm = Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]);
        double[] brakeAcc = new double[3];
        if (velNorm > 1e-6) {
            for (int i = 0; i < 3; i++) {
                brakeAcc[i] = -maxAcc * (vel[i] / velNorm);
            }
        }
        return new GovernorResult(brakeAcc, true, h, "EMERGENCY_BRAKED");
    }
}
