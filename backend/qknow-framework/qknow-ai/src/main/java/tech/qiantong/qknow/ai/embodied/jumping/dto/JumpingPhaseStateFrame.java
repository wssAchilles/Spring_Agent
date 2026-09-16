package tech.qiantong.qknow.ai.embodied.jumping.dto;

import java.util.Arrays;
import java.util.Objects;

/**
 * 具身智能体高动态弹跳全生命周期瞬时状态帧。
 * 封装起跳预压/弹道飞行/着陆缓冲各相态下的关节、车轮、浮动基位姿与阿里千问 1536 维超球面归一化特征。
 *
 * @param frameId 帧唯一标识符
 * @param sessionId 弹跳会话 ID
 * @param robotId 机器人物理唯一标识
 * @param phase 当前弹跳物理相位 (0: CROUCH_PREP, 1: BURST_LAUNCH, 2: AERIAL_FLIGHT, 3: TOUCHDOWN_DAMPING, 4: RESTORE_STABLE)
 * @param jointAngles 12 维腿部关节角度向量 (rad)
 * @param jointVelocities 12 维腿部关节角速度向量 (rad/s)
 * @param wheelVelocities 4 轮毂电机旋转角速度向量 (rad/s)
 * @param basePoseTwist 6 维机身空间位姿旋量 [x, y, z, roll, pitch, yaw]
 * @param baseVelocityTwist 6 维机身空间线速度与角速度旋量 [vx, vy, vz, wx, wy, wz]
 * @param aerialAngularMomentum 3 维空中质心角动量旋量 [Lx, Ly, Lz] (N*m*s)
 * @param groundReactionForces 4 足端/轮端地面反作用力标量 (N)
 * @param qwenEmbedding1536 阿里千问 1536 维超球面单位特征向量 (L2 norm = 1.0)
 * @param timestampMicros 微秒级高精度时间戳
 */
public record JumpingPhaseStateFrame(
        String frameId,
        String sessionId,
        String robotId,
        int phase,
        double[] jointAngles,
        double[] jointVelocities,
        double[] wheelVelocities,
        double[] basePoseTwist,
        double[] baseVelocityTwist,
        double[] aerialAngularMomentum,
        double[] groundReactionForces,
        double[] qwenEmbedding1536,
        long timestampMicros
) {
    public static final int PHASE_CROUCH_PREP = 0;
    public static final int PHASE_BURST_LAUNCH = 1;
    public static final int PHASE_AERIAL_FLIGHT = 2;
    public static final int PHASE_TOUCHDOWN_DAMPING = 3;
    public static final int PHASE_RESTORE_STABLE = 4;

    public JumpingPhaseStateFrame {
        Objects.requireNonNull(frameId, "frameId 不能为空");
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        Objects.requireNonNull(robotId, "robotId 不能为空");
        Objects.requireNonNull(jointAngles, "jointAngles 不能为空");
        Objects.requireNonNull(jointVelocities, "jointVelocities 不能为空");
        Objects.requireNonNull(wheelVelocities, "wheelVelocities 不能为空");
        Objects.requireNonNull(basePoseTwist, "basePoseTwist 不能为空");
        Objects.requireNonNull(baseVelocityTwist, "baseVelocityTwist 不能为空");
        Objects.requireNonNull(aerialAngularMomentum, "aerialAngularMomentum 不能为空");
        Objects.requireNonNull(groundReactionForces, "groundReactionForces 不能为空");
        Objects.requireNonNull(qwenEmbedding1536, "qwenEmbedding1536 不能为空");

        if (qwenEmbedding1536.length != 1536) {
            throw new IllegalArgumentException("阿里千问特征向量维度必须严格为 1536 维，当前维度: " + qwenEmbedding1536.length);
        }

        // 校验千问 1536 维向量超球面单位归一化约束 (L2 norm = 1.0 +/- 1e-4)
        double normSq = 0.0;
        for (double v : qwenEmbedding1536) {
            normSq += v * v;
        }
        double norm = Math.sqrt(normSq);
        if (Math.abs(norm - 1.0) > 1e-4) {
            throw new IllegalArgumentException("阿里千问特征向量未投影在单位超球面上，L2 范数: " + norm);
        }
    }
}
