package tech.qiantong.qknow.ai.embodied.swarm.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 具身智能体集群状态帧 (Java 21 Record)
 * 封装智能体 ID、机体异构类型、三维位姿与速度、通信邻接表、系留索拉力向量与阿里千问 1536 维超球面单位向量
 */
public record SwarmAgentStateFrame(
        String agentId,
        AgentRoleType roleType,
        double[] position,
        double[] velocity,
        double[] orientationEuler,
        double[] angularVelocity,
        List<String> neighborIds,
        double[] tetherTensionVector,
        float[] embedding1536,
        long timestampEpochMs
) {

    public enum AgentRoleType {
        QUADRUPED, // 四足轮腿机器人
        BIPED,      // 双足人形机器人
        UAV         // 空中多旋翼飞行器
    }

    public SwarmAgentStateFrame {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("智能体唯一标识 agentId 不能为空");
        }
        if (roleType == null) {
            throw new IllegalArgumentException("智能体机体角色类型 roleType 不能为空");
        }
        if (position == null || position.length != 3) {
            throw new IllegalArgumentException("空间三维位置 position 维度必须严格为 3");
        }
        if (velocity == null || velocity.length != 3) {
            throw new IllegalArgumentException("空间三维线速度 velocity 维度必须严格为 3");
        }
        if (orientationEuler == null || orientationEuler.length != 3) {
            throw new IllegalArgumentException("机身欧拉角 orientationEuler 维度必须严格为 3");
        }
        if (angularVelocity == null || angularVelocity.length != 3) {
            throw new IllegalArgumentException("机身角速度 angularVelocity 维度必须严格为 3");
        }
        if (tetherTensionVector == null || tetherTensionVector.length != 3) {
            throw new IllegalArgumentException("系留线缆张力矢量 tetherTensionVector 维度必须严格为 3");
        }
        if (embedding1536 == null || embedding1536.length != 1536) {
            throw new IllegalArgumentException("阿里千问特征向量必须严格为 1536 维");
        }

        // 阿里千问 1536 维超球面单位向量几何模长强校验 (||v||_2 = 1.0 +- 1e-4)
        double normSq = 0.0;
        for (float val : embedding1536) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("阿里千问特征向量必须位于单位超球面上, 当前模长: " + norm);
        }

        // 防御性拷贝
        position = Arrays.copyOf(position, 3);
        velocity = Arrays.copyOf(velocity, 3);
        orientationEuler = Arrays.copyOf(orientationEuler, 3);
        angularVelocity = Arrays.copyOf(angularVelocity, 3);
        tetherTensionVector = Arrays.copyOf(tetherTensionVector, 3);
        embedding1536 = Arrays.copyOf(embedding1536, 1536);
        neighborIds = neighborIds == null ? Collections.emptyList() : List.copyOf(neighborIds);
    }
}
