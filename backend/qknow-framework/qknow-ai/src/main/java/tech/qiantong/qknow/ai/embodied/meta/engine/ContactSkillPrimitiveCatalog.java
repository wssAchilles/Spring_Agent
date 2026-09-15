package tech.qiantong.qknow.ai.embodied.meta.engine;

import tech.qiantong.qknow.ai.embodied.meta.dto.ContactSkillPrimitiveType;
import tech.qiantong.qknow.ai.embodied.meta.dto.SkillPrimitiveParameters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 69: 接触丰富技能元编排器 (ContactSkillPrimitiveCatalog)
 * 维护四类典型接触技能元的参数集，并支持阿里千问 1536 维超球面嵌入匹配 (定理 1.1)
 *
 * @author Achilles
 * @since 2026-09-15
 */
public class ContactSkillPrimitiveCatalog {

    public static final int EMBEDDING_DIMENSION = 1536;

    private final Map<ContactSkillPrimitiveType, SkillPrimitiveParameters> defaultTemplates = new ConcurrentHashMap<>();
    private final Map<ContactSkillPrimitiveType, float[]> hypersphereEmbeddings = new ConcurrentHashMap<>();

    public ContactSkillPrimitiveCatalog() {
        initDefaultTemplates();
        initDefaultEmbeddings();
    }

    private void initDefaultTemplates() {
        // 1. 插拔 (INSERTION): Z 轴刚度 800N/m 进给，X/Y 径向 300N/m 柔顺对准，目标推力 -15.0N
        defaultTemplates.put(ContactSkillPrimitiveType.INSERTION, new SkillPrimitiveParameters(
                new double[]{300.0, 300.0, 800.0, 20.0, 20.0, 20.0},
                new double[]{40.0, 40.0, 80.0, 2.0, 2.0, 2.0},
                new double[]{0.0, 0.0, -15.0, 0.0, 0.0, 0.0},
                0.0
        ));

        // 2. 对准 (ALIGNMENT): 径向柔顺刚度 100N/m 找平，极小法向贴合力 -5.0N
        defaultTemplates.put(ContactSkillPrimitiveType.ALIGNMENT, new SkillPrimitiveParameters(
                new double[]{100.0, 100.0, 150.0, 5.0, 5.0, 5.0},
                new double[]{20.0, 20.0, 30.0, 1.0, 1.0, 1.0},
                new double[]{0.0, 0.0, -5.0, 0.0, 0.0, 0.0},
                0.0
        ));

        // 3. 旋拧 (SCREWING): 导程 0.0015 m/rad，拧紧扭矩 0.8 Nm
        defaultTemplates.put(ContactSkillPrimitiveType.SCREWING, new SkillPrimitiveParameters(
                new double[]{500.0, 500.0, 600.0, 30.0, 30.0, 10.0},
                new double[]{50.0, 50.0, 70.0, 3.0, 3.0, 1.5},
                new double[]{0.0, 0.0, -20.0, 0.0, 0.0, 0.8},
                0.0015
        ));

        // 4. 打磨 (POLISHING): 法向 Z 轴刚度 1200N/m，恒定法向研磨力 -30.0N
        defaultTemplates.put(ContactSkillPrimitiveType.POLISHING, new SkillPrimitiveParameters(
                new double[]{150.0, 150.0, 1200.0, 15.0, 15.0, 25.0},
                new double[]{25.0, 25.0, 120.0, 2.0, 2.0, 3.0},
                new double[]{0.0, 0.0, -30.0, 0.0, 0.0, 0.0},
                0.0
        ));
    }

    private void initDefaultEmbeddings() {
        // 在 1536 维空间上为 4 类技能元分配正交块，确保两两正交且严格模长归一化
        int blockSize = EMBEDDING_DIMENSION / 4; // 384
        for (ContactSkillPrimitiveType type : ContactSkillPrimitiveType.values()) {
            float[] vec = new float[EMBEDDING_DIMENSION];
            int offset = type.ordinal() * blockSize;
            double norm = Math.sqrt(blockSize);
            for (int i = 0; i < blockSize; i++) {
                vec[offset + i] = (float) (1.0 / norm);
            }
            hypersphereEmbeddings.put(type, vec);
        }
    }

    public float[] getEmbedding(ContactSkillPrimitiveType type) {
        return hypersphereEmbeddings.get(type);
    }

    /**
     * 基于千问 1536 维超球面嵌入计算余弦内积，检索最匹配的技能元
     */
    public ContactSkillPrimitiveType matchSkillPrimitive(float[] queryEmbedding) {
        if (queryEmbedding == null || queryEmbedding.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("Query embedding must be 1536-dimensional");
        }
        ContactSkillPrimitiveType bestMatch = ContactSkillPrimitiveType.INSERTION;
        double maxCosine = -Double.MAX_VALUE;

        for (Map.Entry<ContactSkillPrimitiveType, float[]> entry : hypersphereEmbeddings.entrySet()) {
            double dot = 0.0;
            float[] ref = entry.getValue();
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                dot += queryEmbedding[i] * ref[i];
            }
            if (dot > maxCosine) {
                maxCosine = dot;
                bestMatch = entry.getKey();
            }
        }
        return bestMatch;
    }

    public SkillPrimitiveParameters getParameters(ContactSkillPrimitiveType type) {
        return defaultTemplates.getOrDefault(type, defaultTemplates.get(ContactSkillPrimitiveType.INSERTION));
    }
}
