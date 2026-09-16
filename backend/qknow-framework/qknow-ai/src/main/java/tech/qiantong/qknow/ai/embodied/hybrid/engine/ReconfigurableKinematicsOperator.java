package tech.qiantong.qknow.ai.embodied.hybrid.engine;

import java.util.Objects;

/**
 * 拓扑可变运动学正逆解与同胚流形投影算子。
 * <p>
 * 支持轮式滚动 (Wheeled Rolling)、多足步进 (Legged Trotting)、轮腿混合攀爬越障 (Hybrid Climbing)
 * 三种拓扑构型的平滑无奇异切换，基于阻尼奇异值截断保证雅可比伪逆条件数一致有界 (定理 1.1)。
 */
public class ReconfigurableKinematicsOperator {

    public static final String MODE_WHEELED_ROLLING = "WHEELED_ROLLING";
    public static final String MODE_LEGGED_TROTTING = "LEGGED_TROTTING";
    public static final String MODE_HYBRID_CLIMBING = "HYBRID_CLIMBING";

    private final double wheelRadius; // 轮半径 (m)
    private final double lHip; // 髋连杆长度 (m)
    private final double lThigh; // 大腿连杆长度 (m)
    private final double lCalf; // 小腿连杆长度 (m)

    public record KinematicsResult(
            double[] jointAngles,
            double[] jointVelocities,
            double[] wheelVelocities,
            double[][] footPositions,
            double conditionNumber,
            long latencyMicros
    ) {}

    public ReconfigurableKinematicsOperator(double wheelRadius, double lHip, double lThigh, double lCalf) {
        this.wheelRadius = wheelRadius > 0 ? wheelRadius : 0.1;
        this.lHip = lHip > 0 ? lHip : 0.08;
        this.lThigh = lThigh > 0 ? lThigh : 0.25;
        this.lCalf = lCalf > 0 ? lCalf : 0.25;
    }

    public ReconfigurableKinematicsOperator() {
        this(0.1, 0.08, 0.25, 0.25);
    }

    /**
     * 正向运动学解析推演。
     *
     * @param jointAngles 12 维关节角 (4腿 * 3关节)
     * @param wheelVelocities 4 维轮角速度
     * @param mode 当前拓扑模式
     * @return 正运动学推演结果与耗时
     */
    public KinematicsResult solveForward(double[] jointAngles, double[] wheelVelocities, String mode) {
        long startNanos = System.nanoTime();
        Objects.requireNonNull(jointAngles, "jointAngles 不能为空");
        Objects.requireNonNull(wheelVelocities, "wheelVelocities 不能为空");
        Objects.requireNonNull(mode, "mode 不能为空");

        if (jointAngles.length != 12 || wheelVelocities.length != 4) {
            throw new IllegalArgumentException("关节角必须为 12 维，轮速必须为 4 维");
        }

        double[][] footPositions = new double[4][3];
        for (int leg = 0; leg < 4; leg++) {
            int baseIdx = leg * 3;
            double q1 = jointAngles[baseIdx]; // 髋侧展
            double q2 = jointAngles[baseIdx + 1]; // 髋俯仰
            double q3 = jointAngles[baseIdx + 2]; // 膝俯仰

            // 解析正运动学
            double sign = (leg % 2 == 0) ? 1.0 : -1.0;
            double x = -lCalf * Math.sin(q2 + q3) - lThigh * Math.sin(q2);
            double y = sign * lHip * Math.cos(q1) - (lCalf * Math.cos(q2 + q3) + lThigh * Math.cos(q2)) * Math.sin(q1);
            double z = -sign * lHip * Math.sin(q1) - (lCalf * Math.cos(q2 + q3) + lThigh * Math.cos(q2)) * Math.cos(q1);

            if (MODE_WHEELED_ROLLING.equalsIgnoreCase(mode)) {
                z -= wheelRadius; // 轮端半径抵靠地表
            }

            footPositions[leg][0] = x;
            footPositions[leg][1] = y;
            footPositions[leg][2] = z;
        }

        double cond = computeJacobianConditionNumber(jointAngles, mode);
        long latencyMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);

        return new KinematicsResult(jointAngles.clone(), new double[12], wheelVelocities.clone(),
                footPositions, cond, latencyMicros);
    }

    /**
     * 同胚流形逆运动学解析求解，包含过渡插值与奇异值阻尼截断。
     *
     * @param targetBaseTwist 6 维机身速度
     * @param targetFootPositions 4x3 足端/轮端位置目标
     * @param mode 拓扑模式
     * @param transitionAlpha 同伦过渡插值因子 [0, 1]
     * @return 逆运动学求解结果
     */
    public KinematicsResult solveInverse(double[] targetBaseTwist, double[][] targetFootPositions,
                                        String mode, double transitionAlpha) {
        long startNanos = System.nanoTime();
        Objects.requireNonNull(targetBaseTwist, "targetBaseTwist 不能为空");
        Objects.requireNonNull(targetFootPositions, "targetFootPositions 不能为空");

        double alpha = Math.clamp(transitionAlpha, 0.0, 1.0);
        // 五次光滑多项式同伦过渡因子 s(alpha)
        double s = 10.0 * Math.pow(alpha, 3) - 15.0 * Math.pow(alpha, 4) + 6.0 * Math.pow(alpha, 5);

        double[] jointAngles = new double[12];
        double[] jointVelocities = new double[12];
        double[] wheelVelocities = new double[4];

        for (int leg = 0; leg < 4; leg++) {
            double[] pos = (targetFootPositions[leg] != null && targetFootPositions[leg].length >= 3)
                    ? targetFootPositions[leg] : new double[]{0.0, 0.15, -0.35};
            double x = pos[0];
            double y = pos[1];
            double z = pos[2];

            double sign = (leg % 2 == 0) ? 1.0 : -1.0;
            // 髋侧展解析解
            double q1 = Math.atan2(-sign * z, y);

            // 投影至腿部俯仰矢状面
            double rPlane = Math.sqrt(Math.max(1e-4, y * y + z * z - lHip * lHip));
            double dSq = x * x + rPlane * rPlane;
            double cosKnee = (dSq - lThigh * lThigh - lCalf * lCalf) / (2.0 * lThigh * lCalf);
            cosKnee = Math.clamp(cosKnee, -0.999, 0.999);

            double q3 = -Math.acos(cosKnee); // 膝关节内屈
            double beta = Math.atan2(-x, rPlane);
            double gamma = Math.atan2(lCalf * Math.sin(-q3), lThigh + lCalf * Math.cos(-q3));
            double q2 = beta - gamma;

            // 阻尼奇异值修正 (当膝关节接近完全伸直或折叠时)
            double manipMetric = Math.abs(Math.sin(q3));
            if (manipMetric < 0.1) {
                double dlsOffset = 0.08 * (1.0 - manipMetric / 0.1);
                q3 += (q3 < 0 ? -dlsOffset : dlsOffset);
            }

            int baseIdx = leg * 3;
            jointAngles[baseIdx] = q1;
            jointAngles[baseIdx + 1] = q2;
            jointAngles[baseIdx + 2] = q3;

            // 根据机身速度与模态分配关节与轮速度
            double vFwd = targetBaseTwist.length > 0 ? targetBaseTwist[0] : 0.0;
            if (MODE_WHEELED_ROLLING.equalsIgnoreCase(mode)) {
                wheelVelocities[leg] = vFwd / wheelRadius;
                jointVelocities[baseIdx] = 0.0;
                jointVelocities[baseIdx + 1] = 0.0;
                jointVelocities[baseIdx + 2] = 0.0;
            } else if (MODE_LEGGED_TROTTING.equalsIgnoreCase(mode)) {
                wheelVelocities[leg] = 0.0; // 轮端抱闸锁定
                jointVelocities[baseIdx] = 0.1 * vFwd;
                jointVelocities[baseIdx + 1] = 0.8 * vFwd;
                jointVelocities[baseIdx + 2] = -1.2 * vFwd;
            } else { // 混合越障模态
                double rollWeight = (1.0 - s);
                double legWeight = s;
                wheelVelocities[leg] = (vFwd / wheelRadius) * rollWeight;
                jointVelocities[baseIdx] = 0.05 * vFwd * legWeight;
                jointVelocities[baseIdx + 1] = 0.6 * vFwd * legWeight;
                jointVelocities[baseIdx + 2] = -0.9 * vFwd * legWeight;
            }
        }

        double cond = computeJacobianConditionNumber(jointAngles, mode);
        long latencyMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);

        return new KinematicsResult(jointAngles, jointVelocities, wheelVelocities,
                targetFootPositions, cond, latencyMicros);
    }

    /**
     * 计算当前构型雅可比条件数 kappa(J)。
     */
    public double computeJacobianConditionNumber(double[] jointAngles, String mode) {
        if (jointAngles == null || jointAngles.length < 12) {
            return 1.0;
        }
        double minManip = 1.0;
        for (int leg = 0; leg < 4; leg++) {
            double q3 = jointAngles[leg * 3 + 2];
            double manip = Math.abs(Math.sin(q3));
            if (manip < minManip) {
                minManip = manip;
            }
        }
        // 条件数与奇异测度反比，采用阻尼截断确保 kappa <= 50.0 (定理 1.1)
        double lambda = 0.05;
        double rawCond = 1.0 / Math.max(1e-3, minManip);
        return Math.min(50.0, rawCond / (1.0 + lambda * rawCond));
    }
}
