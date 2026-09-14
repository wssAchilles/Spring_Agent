package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.ActionPrimitiveDTO;
import tech.qiantong.qknow.ai.embodied.dto.BoundingBox3D;
import tech.qiantong.qknow.ai.embodied.dto.SpatialEntityDO;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

import java.util.HashMap;
import java.util.Map;

/**
 * 数字孪生仿真沙盒 (Theorem 3.1)
 * 提供真实环境内存快照克隆、多步前向虚拟步进推演 (Rollout) 与碰撞/力矩越界预检
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class DigitalTwinSimulator {

    private static final Logger log = LoggerFactory.getLogger(DigitalTwinSimulator.class);

    private static final double MAX_ALLOWABLE_FORCE_NEWTON = 100.0; // 额定最大安全力矩 100N

    /**
     * 仿真判定结果对象
     */
    public static class SimulationReport {
        private boolean safe;
        private String conflictEntityId;
        private double minMarginDistance;
        private String rejectionReason;

        public SimulationReport(boolean safe, String conflictEntityId, double minMarginDistance, String rejectionReason) {
            this.safe = safe;
            this.conflictEntityId = conflictEntityId;
            this.minMarginDistance = minMarginDistance;
            this.rejectionReason = rejectionReason;
        }

        public boolean isSafe() { return safe; }
        public String getConflictEntityId() { return conflictEntityId; }
        public double getMinMarginDistance() { return minMarginDistance; }
        public String getRejectionReason() { return rejectionReason; }
    }

    private final Map<String, SpatialEntityDO> twinSnapshot = new HashMap<>();

    /**
     * 从真实环境克隆内存快照
     */
    public void cloneFromRealWorld(SpatialGridGraph realWorld) {
        twinSnapshot.clear();
        for (Map.Entry<String, SpatialEntityDO> entry : realWorld.getAllEntities().entrySet()) {
            twinSnapshot.put(entry.getKey(), entry.getValue().deepCopy());
        }
        log.info("已成功克隆数字孪生快照，纳管实体数: {}", twinSnapshot.size());
    }

    /**
     * 执行多步前向反事实推演 (Counterfactual Rollout)
     */
    public SimulationReport simulateRollout(ActionPrimitiveDTO action, int rolloutSteps) {
        if (action == null) {
            return new SimulationReport(false, null, 0.0, "动作原语为空");
        }

        // 1. 力矩边界检查
        if (action.getExpectedForceNewton() > MAX_ALLOWABLE_FORCE_NEWTON) {
            log.warn("仿真拒绝：预期力矩 {}N 超过安全硬上限 {}N",
                    action.getExpectedForceNewton(), MAX_ALLOWABLE_FORCE_NEWTON);
            return new SimulationReport(false, null, 0.0, "预期力矩超过系统安全硬上限 (100N)");
        }

        SpatialEntityDO target = twinSnapshot.get(action.getTargetEntityId());
        if (target == null) {
            return new SimulationReport(true, null, Double.MAX_VALUE, "动作未指定具体目标实体，通过");
        }

        SpatialPose3D startPose = target.getPose();
        SpatialPose3D endPose = action.getTargetPose() != null ? action.getTargetPose() : startPose;

        double minDistance = Double.MAX_VALUE;
        int steps = Math.max(1, rolloutSteps);

        // 2. 离散插值多步前向虚拟步进
        for (int i = 1; i <= steps; i++) {
            double alpha = (double) i / steps;
            double stepX = startPose.getX() + alpha * (endPose.getX() - startPose.getX());
            double stepY = startPose.getY() + alpha * (endPose.getY() - startPose.getY());
            double stepZ = startPose.getZ() + alpha * (endPose.getZ() - startPose.getZ());
            SpatialPose3D currentStepPose = new SpatialPose3D(stepX, stepY, stepZ);

            BoundingBox3D currentBox = new BoundingBox3D(currentStepPose,
                    target.getBoundingBox().getSizeX(), target.getBoundingBox().getSizeY(), target.getBoundingBox().getSizeZ());

            // 碰撞干涉检验
            for (SpatialEntityDO other : twinSnapshot.values()) {
                if (other.getEntityId().equals(target.getEntityId())) continue;

                if (currentBox.intersects(other.getBoundingBox())) {
                    log.warn("数字孪生推演第 {} 步检出碰撞干涉！目标实体 {} 与障碍物 {} 相撞",
                            i, target.getEntityId(), other.getEntityId());
                    return new SimulationReport(false, other.getEntityId(), 0.0,
                            "轨迹在第 " + i + " 步与障碍物 [" + other.getEntityId() + "] 发生碰撞");
                }
                double dist = currentBox.surfaceDistanceTo(other.getBoundingBox());
                minDistance = Math.min(minDistance, dist);
            }
        }

        log.info("数字孪生前向推演 {} 步验证全部通过！最小安全净距: {}m", steps, minDistance);
        return new SimulationReport(true, null, minDistance, "通过");
    }

    public SpatialEntityDO getTwinEntity(String entityId) {
        return twinSnapshot.get(entityId);
    }
}
