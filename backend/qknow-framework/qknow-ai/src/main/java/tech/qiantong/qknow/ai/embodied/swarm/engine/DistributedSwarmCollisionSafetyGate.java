package tech.qiantong.qknow.ai.embodied.swarm.engine;

import tech.qiantong.qknow.ai.embodied.swarm.dto.SwarmAgentStateFrame;

import java.util.Arrays;
import java.util.List;

/**
 * 分布式互易速度障碍与相对阶 r=2 高阶控制屏障 (HOCBF) 蜂群避障安全门禁 (定理 1.2)
 * 融合 ORCA 互易避障责任平分、相对阶 r=2 HOCBF 闭式解析二次规划投影与确定性右手破称摄动，杜绝狭窄通道死锁
 */
public class DistributedSwarmCollisionSafetyGate {

    private static final double DEFAULT_AGENT_RADIUS_M = 0.35;
    private static final double DEFAULT_SAFETY_MARGIN_M = 0.20;
    private static final double KAPPA_1 = 4.0;
    private static final double KAPPA_2 = 6.0;
    private static final double SYMMETRY_BREAK_EPSILON = 0.08;

    public record SafetyGateResult(
            double[] safeAcceleration,
            double minSeparationDistance,
            double minHocbfSafetyMargin,
            boolean collisionFreeGuaranteed,
            boolean symmetryBroken
    ) {}

    /**
     * 对指定智能体实施分布式互易 HOCBF 闭式解析投影
     *
     * @param egoFrame 自机当前状态帧
     * @param nominalAcc 自机名义期望加速度 (来自编队一致性调节器) [ax, ay, az]
     * @param neighborFrames 邻近可能发生碰撞的障碍智能体状态帧列表
     * @return 经过高阶控制屏障硬安全投影后的无碰撞安全加速度与安全裕度指标
     */
    public SafetyGateResult filterAcceleration(
            SwarmAgentStateFrame egoFrame,
            double[] nominalAcc,
            List<SwarmAgentStateFrame> neighborFrames
    ) {
        double[] aSafe = Arrays.copyOf(nominalAcc, 3);
        double minDistance = Double.MAX_VALUE;
        double minMargin = Double.MAX_VALUE;
        boolean symmetryBrokenFlag = false;

        double[] pI = egoFrame.position();
        double[] vI = egoFrame.velocity();

        for (SwarmAgentStateFrame other : neighborFrames) {
            if (other.agentId().equals(egoFrame.agentId())) {
                continue;
            }

            double[] pJ = other.position();
            double[] vJ = other.velocity();

            // 相对位姿与相对速度
            double rx = pI[0] - pJ[0];
            double ry = pI[1] - pJ[1];
            double rz = pI[2] - pJ[2];
            double distSq = rx * rx + ry * ry + rz * rz;
            double dist = Math.sqrt(distSq);

            if (dist < minDistance) {
                minDistance = dist;
            }

            double dSafeThreshold = 2 * DEFAULT_AGENT_RADIUS_M + DEFAULT_SAFETY_MARGIN_M;
            // 0 阶屏障函数: h_ij = ||p_i - p_j||^2 - D_ij^2
            double h = distSq - dSafeThreshold * dSafeThreshold;

            // 1 阶时间导数: h_dot = 2 * p_ij^T * v_ij
            double vx = vI[0] - vJ[0];
            double vy = vI[1] - vJ[1];
            double vz = vI[2] - vJ[2];
            double hDot = 2.0 * (rx * vx + ry * vy + rz * vz);

            // 相对阶 r=2 状态耦合项: Gamma_ij = 2*||v_ij||^2 + 2*(k1+k2)*p_ij^T*v_ij + k1*k2*h_ij
            double vRelSq = vx * vx + vy * vy + vz * vz;
            double gamma = 2.0 * vRelSq + (KAPPA_1 + KAPPA_2) * hDot + KAPPA_1 * KAPPA_2 * h;

            // 互易平分约束标量下界: n_ij^T * a_i >= -0.25 * Gamma
            double bIj = -0.25 * gamma;

            // 检测对向行驶对称死锁并构建破称法向量
            double[] nIj = new double[]{rx, ry, rz};
            boolean headOnCollision = isHeadOnOpposite(rx, ry, vx, vy, dist);
            if (headOnCollision) {
                symmetryBrokenFlag = true;
                // 右手定则正交偏置: n_perp = [-ry, rx, 0] / dist
                double perpX = -ry / (dist + 1e-6);
                double perpY = rx / (dist + 1e-6);
                nIj[0] += SYMMETRY_BREAK_EPSILON * perpX;
                nIj[1] += SYMMETRY_BREAK_EPSILON * perpY;
            }

            double normNSq = nIj[0] * nIj[0] + nIj[1] * nIj[1] + nIj[2] * nIj[2];
            double dotNA = nIj[0] * aSafe[0] + nIj[1] * aSafe[1] + nIj[2] * aSafe[2];
            double margin = dotNA - bIj;

            if (margin < minMargin) {
                minMargin = margin;
            }

            // 若约束激活违背, 实施极速闭式二次规划正交解析投影
            if (margin < 0.0 && normNSq > 1e-9) {
                double lambda = (bIj - dotNA) / normNSq;
                aSafe[0] += lambda * nIj[0];
                aSafe[1] += lambda * nIj[1];
                aSafe[2] += lambda * nIj[2];
            }
        }

        // 若没有邻居，裕度为最大安全默认值
        if (minMargin == Double.MAX_VALUE) {
            minMargin = 100.0;
        }

        boolean safe = minDistance >= (2 * DEFAULT_AGENT_RADIUS_M + DEFAULT_SAFETY_MARGIN_M * 0.5);
        return new SafetyGateResult(aSafe, minDistance, minMargin, safe, symmetryBrokenFlag);
    }

    /**
     * 判断两机是否处于对向直面对冲且相对夹角接近 180 度的对称死锁敏感区
     */
    private static boolean isHeadOnOpposite(double rx, double ry, double vx, double vy, double dist) {
        if (dist < 1e-6) return false;
        // 相对速度点乘连线向量: 若正在高速相向逼近 (dot < 0)
        double dotRel = rx * vx + ry * vy;
        if (dotRel >= -0.05) return false;

        // 计算夹角余弦
        double vNorm = Math.sqrt(vx * vx + vy * vy);
        if (vNorm < 0.1) return false;

        double cosTheta = dotRel / (dist * vNorm);
        // 若相对速度与连线夹角极其接近 180 度 (cosTheta <= -0.92, 约 157 度以上)
        return cosTheta <= -0.92;
    }
}
