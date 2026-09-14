package tech.qiantong.qknow.ai.embodied.dto;

import java.io.Serializable;

/**
 * 三维空间实体对象
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class SpatialEntityDO implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;

    private String entityId;
    private String semanticName;
    private double[] embedding;       // 阿里千问 1536 维超球面语义向量
    private SpatialPose3D pose;
    private BoundingBox3D boundingBox;
    private boolean movable;
    private double massKg;

    public SpatialEntityDO() {
        this.pose = new SpatialPose3D();
        this.boundingBox = new BoundingBox3D();
        this.movable = true;
    }

    public SpatialEntityDO(String entityId, String semanticName, SpatialPose3D pose, BoundingBox3D boundingBox, boolean movable) {
        this.entityId = entityId;
        this.semanticName = semanticName;
        this.pose = pose != null ? pose : new SpatialPose3D();
        this.boundingBox = boundingBox != null ? boundingBox : new BoundingBox3D();
        this.movable = movable;
        this.massKg = 1.0;
    }

    /**
     * 深度克隆用于数字孪生沙盒
     */
    public SpatialEntityDO deepCopy() {
        SpatialEntityDO copy = new SpatialEntityDO(this.entityId, this.semanticName,
                this.pose.clone(), this.boundingBox.clone(), this.movable);
        copy.setMassKg(this.massKg);
        if (this.embedding != null) {
            copy.setEmbedding(this.embedding.clone());
        }
        return copy;
    }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getSemanticName() { return semanticName; }
    public void setSemanticName(String semanticName) { this.semanticName = semanticName; }
    public double[] getEmbedding() { return embedding; }
    public void setEmbedding(double[] embedding) { this.embedding = embedding; }
    public SpatialPose3D getPose() { return pose; }
    public void setPose(SpatialPose3D pose) {
        this.pose = pose;
        if (this.boundingBox != null) {
            this.boundingBox.setCenter(pose);
        }
    }
    public BoundingBox3D getBoundingBox() { return boundingBox; }
    public void setBoundingBox(BoundingBox3D boundingBox) { this.boundingBox = boundingBox; }
    public boolean isMovable() { return movable; }
    public void setMovable(boolean movable) { this.movable = movable; }
    public double getMassKg() { return massKg; }
    public void setMassKg(double massKg) { this.massKg = massKg; }
}
