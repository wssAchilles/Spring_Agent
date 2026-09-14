package tech.qiantong.qknow.ai.embodied.dto;

import java.io.Serializable;

/**
 * 三维定向/轴对齐包围盒 (OBB) 实体 (Theorem 1.1)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class BoundingBox3D implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    private SpatialPose3D center;
    private double sizeX; // 长度 (米)
    private double sizeY; // 宽度 (米)
    private double sizeZ; // 高度 (米)

    public BoundingBox3D() {
        this.center = new SpatialPose3D();
    }

    public BoundingBox3D(SpatialPose3D center, double sizeX, double sizeY, double sizeZ) {
        this.center = center != null ? center : new SpatialPose3D();
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    /**
     * 判断两包围盒是否相交碰撞 (基于分离轴定理 SAT)
     */
    public boolean intersects(BoundingBox3D other) {
        if (other == null) return false;
        double halfX1 = this.sizeX / 2.0;
        double halfY1 = this.sizeY / 2.0;
        double halfZ1 = this.sizeZ / 2.0;

        double halfX2 = other.sizeX / 2.0;
        double halfY2 = other.sizeY / 2.0;
        double halfZ2 = other.sizeZ / 2.0;

        boolean overlapX = Math.abs(this.center.getX() - other.center.getX()) <= (halfX1 + halfX2);
        boolean overlapY = Math.abs(this.center.getY() - other.center.getY()) <= (halfY1 + halfY2);
        boolean overlapZ = Math.abs(this.center.getZ() - other.center.getZ()) <= (halfZ1 + halfZ2);

        return overlapX && overlapY && overlapZ;
    }

    /**
     * 计算两包围盒表面最近欧氏净距 (若相交返回 0.0)
     */
    public double surfaceDistanceTo(BoundingBox3D other) {
        if (intersects(other)) {
            return 0.0;
        }
        double dx = Math.max(0.0, Math.abs(this.center.getX() - other.center.getX()) - (this.sizeX + other.sizeX) / 2.0);
        double dy = Math.max(0.0, Math.abs(this.center.getY() - other.center.getY()) - (this.sizeY + other.sizeY) / 2.0);
        double dz = Math.max(0.0, Math.abs(this.center.getZ() - other.center.getZ()) - (this.sizeZ + other.sizeZ) / 2.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public BoundingBox3D clone() {
        return new BoundingBox3D(this.center.clone(), this.sizeX, this.sizeY, this.sizeZ);
    }

    public SpatialPose3D getCenter() { return center; }
    public void setCenter(SpatialPose3D center) { this.center = center; }
    public double getSizeX() { return sizeX; }
    public void setSizeX(double sizeX) { this.sizeX = sizeX; }
    public double getSizeY() { return sizeY; }
    public void setSizeY(double sizeY) { this.sizeY = sizeY; }
    public double getSizeZ() { return sizeZ; }
    public void setSizeZ(double sizeZ) { this.sizeZ = sizeZ; }
}
