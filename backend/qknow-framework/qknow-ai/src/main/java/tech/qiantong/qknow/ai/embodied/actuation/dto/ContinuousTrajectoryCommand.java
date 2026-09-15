package tech.qiantong.qknow.ai.embodied.actuation.dto;

import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;
import java.util.List;

/**
 * 连续运动轨迹控制指令
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class ContinuousTrajectoryCommand {

    private final String commandId;
    private final String targetEntityId;
    private final List<SpatialPose3D> waypoints;
    private final double durationSeconds;
    private final double maxVelocity;
    private final double maxAcceleration;
    private final double maxJerk;
    private final float[] targetEmbedding1536;

    public ContinuousTrajectoryCommand(String commandId,
                                       String targetEntityId,
                                       List<SpatialPose3D> waypoints,
                                       double durationSeconds,
                                       double maxVelocity,
                                       double maxAcceleration,
                                       double maxJerk,
                                       float[] targetEmbedding1536) {
        this.commandId = commandId != null ? commandId : "CMD-" + System.currentTimeMillis();
        this.targetEntityId = targetEntityId;
        this.waypoints = waypoints != null ? List.copyOf(waypoints) : List.of();
        this.durationSeconds = durationSeconds > 0 ? durationSeconds : 1.0;
        this.maxVelocity = maxVelocity > 0 ? maxVelocity : 1.0;
        this.maxAcceleration = maxAcceleration > 0 ? maxAcceleration : 3.0;
        this.maxJerk = maxJerk > 0 ? maxJerk : 50.0;
        this.targetEmbedding1536 = validateAndNormalize(targetEmbedding1536);
    }

    private float[] validateAndNormalize(float[] raw) {
        float[] norm = new float[1536];
        if (raw == null || raw.length != 1536) {
            norm[0] = 1.0f;
            return norm;
        }
        double sumSq = 0.0;
        for (float v : raw) {
            sumSq += v * v;
        }
        double normVal = Math.sqrt(sumSq);
        if (normVal < 1e-9) {
            norm[0] = 1.0f;
            return norm;
        }
        for (int i = 0; i < 1536; i++) {
            norm[i] = (float) (raw[i] / normVal);
        }
        return norm;
    }

    public String getCommandId() { return commandId; }
    public String getTargetEntityId() { return targetEntityId; }
    public List<SpatialPose3D> getWaypoints() { return waypoints; }
    public double getDurationSeconds() { return durationSeconds; }
    public double getMaxVelocity() { return maxVelocity; }
    public double getMaxAcceleration() { return maxAcceleration; }
    public double getMaxJerk() { return maxJerk; }
    public float[] getTargetEmbedding1536() { return targetEmbedding1536; }
}
