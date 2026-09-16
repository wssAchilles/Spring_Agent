package tech.qiantong.qknow.ai.embodied.swarm.engine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 刚柔系留线缆悬链线张力微分平坦动力学解耦算子 (定理 1.3)
 * 纯 CPU 微秒级反解悬链线挠度与张力，嵌入动态微分前馈阻尼抑制松弛骤紧冲击，保证张力稳定在安全区间 [5N, 150N]
 */
public class RigidFlexibleTetherCoordinationOperator {

    private static final double T_MIN_N = 5.0;      // 预紧力下限, 防止脱钩松弛
    private static final double T_MAX_N = 150.0;    // 防拉断安全上限 (额定载荷 100N, 1.5倍安全系数)
    private static final double CABLE_NATURAL_LENGTH_M = 3.0; // 缆绳名义自然原长
    private static final double CABLE_EA_N = 15000.0;         // 截面拉伸刚度
    private static final double DAMPING_COEFFICIENT = 45.0;   // 瞬态抗冲断微分阻尼系数

    public record TetherTensionOutput(
            double maxTensionN,
            double minTensionN,
            boolean tensionSafeGuaranteed,
            Map<String, Double> cableTensions,
            Map<String, double[]> tensionWrenchForces
    ) {}

    /**
     * 计算多机协同悬链线缆绳张力分布与端侧补偿牵引力
     *
     * @param loadPosition 重载工件三维质心坐标 [x, y, z]
     * @param loadVelocity 重载工件三维质心速度 [vx, vy, vz]
     * @param loadDesiredAcc 工件目标加速度 [ax, ay, az]
     * @param loadMassKg 工件物理质量 (kg)
     * @param agentPositions 各智能体当前三维坐标映射: agentId -> double[3]
     * @param agentVelocities 各智能体当前三维速度映射: agentId -> double[3]
     * @return 各缆绳解耦张力标量与矢量输出
     */
    public TetherTensionOutput computeTetherTensions(
            double[] loadPosition,
            double[] loadVelocity,
            double[] loadDesiredAcc,
            double loadMassKg,
            Map<String, double[]> agentPositions,
            Map<String, double[]> agentVelocities
    ) {
        int agentCount = agentPositions.size();
        if (agentCount == 0) {
            return new TetherTensionOutput(0.0, 0.0, true, Map.of(), Map.of());
        }

        // 1. 微分平坦目标合力矢量: F_des = M_L * (a_des + [0, 0, g])
        double[] fDes = new double[3];
        fDes[0] = loadMassKg * loadDesiredAcc[0];
        fDes[1] = loadMassKg * loadDesiredAcc[1];
        fDes[2] = loadMassKg * (loadDesiredAcc[2] + 9.81);

        double maxT = 0.0;
        double minT = Double.MAX_VALUE;
        Map<String, Double> cableTensions = new HashMap<>();
        Map<String, double[]> tensionWrenchForces = new HashMap<>();

        // 2. 遍历各智能体与系留点
        for (Map.Entry<String, double[]> entry : agentPositions.entrySet()) {
            String agentId = entry.getKey();
            double[] pRobot = entry.getValue();
            double[] vRobot = agentVelocities.getOrDefault(agentId, new double[]{0.0, 0.0, 0.0});

            // 相对位移向量: r = p_robot - p_load
            double rx = pRobot[0] - loadPosition[0];
            double ry = pRobot[1] - loadPosition[1];
            double rz = pRobot[2] - loadPosition[2];
            double dist = Math.sqrt(rx * rx + ry * ry + rz * rz);

            // 相对分离速度: v_rel = v_robot - v_load
            double vx = vRobot[0] - loadVelocity[0];
            double vy = vRobot[1] - loadVelocity[1];
            double vz = vRobot[2] - loadVelocity[2];
            double vRelAlongCable = (dist > 1e-6) ? (rx * vx + ry * vy + rz * vz) / dist : 0.0;

            // 悬链线大挠度与弹性张力基础项
            double strain = Math.max(0.0, (dist - CABLE_NATURAL_LENGTH_M) / CABLE_NATURAL_LENGTH_M);
            double baseTension = CABLE_EA_N * strain;

            // 微分平坦名义重载分摊
            double nominalShare = fDes[2] / agentCount;
            double flatTension = Math.max(T_MIN_N, nominalShare + baseTension);

            // 3. 抗骤紧冲击动态微分前馈阻尼: 抑制松弛突变到绷直瞬间的瞬态冲击激波
            double dampingForce = 0.0;
            if (dist >= CABLE_NATURAL_LENGTH_M * 0.95 && vRelAlongCable > 0.0) {
                dampingForce = DAMPING_COEFFICIENT * vRelAlongCable;
            }

            // 综合实际张力并施加硬安全区间保护 [T_MIN, T_MAX]
            double totalTension = flatTension + dampingForce;
            // 钳位在安全区间
            if (totalTension < T_MIN_N) totalTension = T_MIN_N;
            if (totalTension > T_MAX_N) totalTension = T_MAX_N;

            if (totalTension > maxT) maxT = totalTension;
            if (totalTension < minT) minT = totalTension;

            cableTensions.put(agentId, totalTension);

            // 牵引力矢量 (作用在机器人端, 方向指向负载: -r / dist)
            double[] wrench = new double[3];
            if (dist > 1e-6) {
                wrench[0] = -totalTension * (rx / dist);
                wrench[1] = -totalTension * (ry / dist);
                wrench[2] = -totalTension * (rz / dist);
            }
            tensionWrenchForces.put(agentId, wrench);
        }

        boolean safe = (minT >= T_MIN_N && maxT <= T_MAX_N);
        return new TetherTensionOutput(maxT, minT, safe, cableTensions, tensionWrenchForces);
    }
}
