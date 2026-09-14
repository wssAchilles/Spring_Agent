package tech.qiantong.qknow.ai.embodied.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * 三维李群 SE(3) 刚体位姿实体 (Theorem 1.1)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class SpatialPose3D implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    private double x;
    private double y;
    private double z;
    private double roll;  // 横滚角 (度)
    private double pitch; // 俯仰角 (度)
    private double yaw;   // 偏航角 (度)

    public SpatialPose3D() {
    }

    public SpatialPose3D(double x, double y, double z) {
        this(x, y, z, 0.0, 0.0, 0.0);
    }

    public SpatialPose3D(double x, double y, double z, double roll, double pitch, double yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.roll = roll;
        this.pitch = pitch;
        this.yaw = yaw;
    }

    /**
     * 计算两点间欧氏测地距离 (SE(3) 距离不变性)
     */
    public double distanceTo(SpatialPose3D other) {
        if (other == null) return Double.MAX_VALUE;
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 执行刚体位姿变换
     */
    public SpatialPose3D transform(double deltaX, double deltaY, double deltaZ, double deltaYaw) {
        return new SpatialPose3D(this.x + deltaX, this.y + deltaY, this.z + deltaZ,
                this.roll, this.pitch, (this.yaw + deltaYaw) % 360.0);
    }

    @Override
    public SpatialPose3D clone() {
        try {
            return (SpatialPose3D) super.clone();
        } catch (CloneNotSupportedException e) {
            return new SpatialPose3D(x, y, z, roll, pitch, yaw);
        }
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
    public double getRoll() { return roll; }
    public void setRoll(double roll) { this.roll = roll; }
    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }
    public double getYaw() { return yaw; }
    public void setYaw(double yaw) { this.yaw = yaw; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpatialPose3D that = (SpatialPose3D) o;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0 && Double.compare(that.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
