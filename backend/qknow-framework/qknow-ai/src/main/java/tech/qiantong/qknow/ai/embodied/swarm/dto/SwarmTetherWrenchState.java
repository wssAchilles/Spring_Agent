package tech.qiantong.qknow.ai.embodied.swarm.dto;

import java.util.Arrays;

/**
 * 具身集群系留与避障力学综合状态 (Java 21 Record)
 * 封装编队几何残差、代数连通度、最小避障间距、最大线缆张力、HOCBF 裕度与执行加速度
 */
public record SwarmTetherWrenchState(
        double formationErrorNorm,
        double algebraicConnectivityLambda2,
        double minInterAgentDistance,
        double maxTetherTensionN,
        double hocbfSafetyMargin,
        boolean symmetryBroken,
        boolean topologyCompensated,
        double[] desiredAcceleration
) {

    public SwarmTetherWrenchState {
        if (desiredAcceleration == null || desiredAcceleration.length != 3) {
            throw new IllegalArgumentException("期望执行加速度 desiredAcceleration 维度必须严格为 3");
        }
        desiredAcceleration = Arrays.copyOf(desiredAcceleration, 3);
    }
}
