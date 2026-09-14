package tech.qiantong.qknow.module.kmc.service.rag.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 跨实体多跳因果推理链抽取器接口
 * 严格限制 1~2 跳，节点出入度截断 Degree Cutoff <= 30，杜绝超级节点 Supernode 拖垮查询
 */
public interface MultiHopCausalPathExtractor {

    int MAX_HOPS = 2;
    int DEGREE_CUTOFF = 30;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CausalPathFact {
        private String sourceEntity;
        private String firstRelation;
        private String intermediateEntity;
        private String secondRelation;
        private String targetEntity;
        private double causalScore;

        public String toFormattedFact() {
            if (intermediateEntity == null || intermediateEntity.isBlank()) {
                return String.format("[%s] --[%s]--> [%s] (置信度: %.2f)",
                        sourceEntity, firstRelation, targetEntity, causalScore);
            }
            return String.format("[%s] --[%s]--> [%s] --[%s]--> [%s] (置信度: %.2f)",
                    sourceEntity, firstRelation, intermediateEntity, secondRelation, targetEntity, causalScore);
        }
    }

    /**
     * 从种子实体集抽取多跳因果推理链
     * @param workspaceId 知识库工作区ID
     * @param seedEntities 种子实体列表
     * @param maxChains 最大返回事实链条数
     * @return 过滤与度数截断后的结构化因果事实列表
     */
    List<CausalPathFact> extractCausalChains(String workspaceId, List<String> seedEntities, int maxChains);
}
