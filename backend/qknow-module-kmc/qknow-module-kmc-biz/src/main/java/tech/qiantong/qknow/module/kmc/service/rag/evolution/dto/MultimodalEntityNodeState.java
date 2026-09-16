package tech.qiantong.qknow.module.kmc.service.rag.evolution.dto;

import java.util.List;
import java.util.Map;

/**
 * 多模态实体节点状态 (定理 1.1)
 * 封装实体唯一标识、规范名、所属领域、别名集、阿里千问 1536 维超球面单位向量、1-跳局部拓扑 Fréchet 均值与多模态属性
 */
public record MultimodalEntityNodeState(
        String entityId,
        String canonicalName,
        String sourceDomain,
        List<String> aliases,
        double[] sphericalEmbedding,
        double[] localTopologyEmbedding,
        Map<String, String> attributes,
        long timestamp
) {
    /**
     * 阿里千问 1536 维超球面单位向量范数强校验 (||v||_2 = 1.0 ± 10^-4)
     */
    public boolean isValidEmbedding() {
        if (sphericalEmbedding == null || sphericalEmbedding.length != 1536) {
            return false;
        }
        double sumSq = 0.0;
        for (double v : sphericalEmbedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-4;
    }

    public boolean isValidTopologyEmbedding() {
        if (localTopologyEmbedding == null || localTopologyEmbedding.length != 1536) {
            return false;
        }
        double sumSq = 0.0;
        for (double v : localTopologyEmbedding) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        return Math.abs(norm - 1.0) <= 1e-4;
    }
}
