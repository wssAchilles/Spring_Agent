package tech.qiantong.qknow.ai.embodied.tactile.dto;

import java.util.Objects;

/**
 * 高动态非抓取推进与翻滚基元规划方案 (Java 21 Record)
 * <p>
 * 封装基元类型 (PUSHING, PIVOTING, TUMBLING)、推击点接触坐标、安全推击速度、
 * 瞬时旋转中心 (COR) 坐标、允许最大法向力与预期位姿跟踪容限。
 */
public record PushPrimitivePlan(
        String planId,
        PrimitiveType primitiveType,
        double contactX,
        double contactY,
        double pushVelocity,
        double pushAcceleration,
        double corX,
        double corY,
        double targetDisplacementMm,
        double maxNormalForceN,
        long createdAtNs
) {
    public enum PrimitiveType {
        PUSHING,
        PIVOTING,
        TUMBLING
    }

    public PushPrimitivePlan {
        Objects.requireNonNull(planId, "planId 不能为 null");
        Objects.requireNonNull(primitiveType, "primitiveType 不能为 null");
    }
}
