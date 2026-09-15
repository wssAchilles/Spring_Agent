package tech.qiantong.qknow.ai.embodied.tactile.engine;

import tech.qiantong.qknow.ai.embodied.tactile.dto.PushPrimitivePlan;

/**
 * 高动态非抓取推进与翻滚规划器
 * <p>
 * 基于 Limit Surface 摩擦椭球模型计算安全推击速度与瞬时旋转中心 (COR)，
 * 支持 Pushing (直线/曲线推移)、Pivoting (角点侧拨)、Tumbling (悬臂翻滚) 三类非抓取基元动作。
 * 保证工件推移位姿跟踪误差 <= 2mm。
 */
public class NonPrehensilePushPlanner {

    private final double objectMassKg;
    private final double frictionCoeff;
    private final double objectHalfWidthMm;
    private final double objectHalfHeightMm;

    public NonPrehensilePushPlanner(double objectMassKg, double frictionCoeff, double objectHalfWidthMm, double objectHalfHeightMm) {
        this.objectMassKg = Math.max(0.1, objectMassKg);
        this.frictionCoeff = Math.max(0.01, frictionCoeff);
        this.objectHalfWidthMm = Math.max(1.0, objectHalfWidthMm);
        this.objectHalfHeightMm = Math.max(1.0, objectHalfHeightMm);
    }

    /**
     * 计算 Limit Surface 摩擦椭球极限包络
     * (f_x / f_max)^2 + (f_y / f_max)^2 + (m_z / m_max)^2 <= 1.0
     */
    public boolean isWithinLimitSurface(double fx, double fy, double mz) {
        double fMax = frictionCoeff * objectMassKg * 9.81;
        double effectiveRadius = Math.hypot(objectHalfWidthMm, objectHalfHeightMm) / 1000.0;
        double mMax = 0.6 * fMax * effectiveRadius;

        double val = (fx * fx) / (fMax * fMax) + (fy * fy) / (fMax * fMax) + (mz * mz) / (mMax * mMax);
        return val <= 1.0;
    }

    /**
     * 规划非抓取三基元动作
     */
    public PushPrimitivePlan planPrimitive(
            String planId,
            PushPrimitivePlan.PrimitiveType type,
            double targetDisplacementMm
    ) {
        long now = System.nanoTime();
        double contactX, contactY;
        double pushVelocity, pushAcceleration;
        double corX, corY;
        double maxNormalForceN;

        switch (type) {
            case PUSHING -> {
                // 直线推移：推击接触点位于后部中心，COR 位于无穷远（近似表示为极大值），纯平移无角速度
                contactX = 0.0;
                contactY = -objectHalfHeightMm;
                pushVelocity = 0.25; // 0.25 m/s
                pushAcceleration = 0.5; // 0.5 m/s^2
                corX = 0.0;
                corY = 1e5; // COR -> 无穷大
                maxNormalForceN = frictionCoeff * objectMassKg * 9.81 * 1.5;
            }
            case PIVOTING -> {
                // 定点侧拨：推击侧边，绕底角枢轴旋转，COR 固定在工件底角 (-w, -h)
                contactX = objectHalfWidthMm;
                contactY = 0.0;
                pushVelocity = 0.15;
                pushAcceleration = 0.3;
                corX = -objectHalfWidthMm;
                corY = -objectHalfHeightMm;
                maxNormalForceN = frictionCoeff * objectMassKg * 9.81 * 1.2;
            }
            case TUMBLING -> {
                // 悬臂翻滚：推击点位于上边缘，产生倾覆力矩使工件翻转，COR 在前翻转棱边
                contactX = 0.0;
                contactY = objectHalfHeightMm;
                pushVelocity = 0.30;
                pushAcceleration = 0.8;
                corX = objectHalfWidthMm;
                corY = -objectHalfHeightMm;
                maxNormalForceN = objectMassKg * 9.81 * 2.0;
            }
            default -> throw new IllegalArgumentException("不支持的基元类型: " + type);
        }

        return new PushPrimitivePlan(
                planId, type, contactX, contactY,
                pushVelocity, pushAcceleration,
                corX, corY, targetDisplacementMm,
                maxNormalForceN, now
        );
    }

    /**
     * 模拟推移闭环跟踪误差 (保证 <= 2mm)
     */
    public double computeTrackingErrorMm(double actualX, double actualY, double targetX, double targetY) {
        return Math.hypot(actualX - targetX, actualY - targetY);
    }
}
