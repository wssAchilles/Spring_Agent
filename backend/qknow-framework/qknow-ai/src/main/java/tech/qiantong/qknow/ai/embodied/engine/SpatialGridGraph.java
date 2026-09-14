package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.BoundingBox3D;
import tech.qiantong.qknow.ai.embodied.dto.SpatialEntityDO;
import tech.qiantong.qknow.ai.embodied.dto.SpatialPose3D;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3D 度量语义拓扑栅格图 (Theorem 1.1)
 * 管理空间实体拓扑分布、欧式几何距离与相对空间方位语义解析
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class SpatialGridGraph {

    private static final Logger log = LoggerFactory.getLogger(SpatialGridGraph.class);

    /**
     * 相对空间方位枚举
     */
    public enum RelativeOrientation {
        FRONT, BACK, LEFT, RIGHT, ABOVE, BELOW, COINCIDENT
    }

    private final Map<String, SpatialEntityDO> entityRegistry = new ConcurrentHashMap<>();

    public void registerEntity(SpatialEntityDO entity) {
        if (entity != null && entity.getEntityId() != null) {
            entityRegistry.put(entity.getEntityId(), entity);
            log.info("已注册三维空间实体: id={}, name={}, pose=({}, {}, {})",
                    entity.getEntityId(), entity.getSemanticName(),
                    entity.getPose().getX(), entity.getPose().getY(), entity.getPose().getZ());
        }
    }

    public SpatialEntityDO getEntity(String entityId) {
        return entityRegistry.get(entityId);
    }

    /**
     * 计算两实体表面最近测地净距 (Theorem 1.1)
     */
    public double calculateSurfaceDistance(String entityIdA, String entityIdB) {
        SpatialEntityDO a = entityRegistry.get(entityIdA);
        SpatialEntityDO b = entityRegistry.get(entityIdB);
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        return a.getBoundingBox().surfaceDistanceTo(b.getBoundingBox());
    }

    /**
     * 检测两实体是否相交碰撞
     */
    public boolean checkCollision(String entityIdA, String entityIdB) {
        SpatialEntityDO a = entityRegistry.get(entityIdA);
        SpatialEntityDO b = entityRegistry.get(entityIdB);
        if (a == null || b == null) {
            return false;
        }
        return a.getBoundingBox().intersects(b.getBoundingBox());
    }

    /**
     * 计算目标实体相对于观察者实体的空间方位语义
     */
    public RelativeOrientation getRelativeOrientation(String observerId, String targetId) {
        SpatialEntityDO observer = entityRegistry.get(observerId);
        SpatialEntityDO target = entityRegistry.get(targetId);
        if (observer == null || target == null) {
            return RelativeOrientation.COINCIDENT;
        }

        double dx = target.getPose().getX() - observer.getPose().getX();
        double dy = target.getPose().getY() - observer.getPose().getY();
        double dz = target.getPose().getZ() - observer.getPose().getZ();

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absZ > absX && absZ > absY && absZ > 0.05) {
            return dz > 0 ? RelativeOrientation.ABOVE : RelativeOrientation.BELOW;
        }

        if (absX > absY) {
            return dx > 0 ? RelativeOrientation.FRONT : RelativeOrientation.BACK;
        } else if (absY > 0.05) {
            return dy > 0 ? RelativeOrientation.RIGHT : RelativeOrientation.LEFT;
        }
        return RelativeOrientation.COINCIDENT;
    }

    public Map<String, SpatialEntityDO> getAllEntities() {
        return Collections.unmodifiableMap(entityRegistry);
    }
}
