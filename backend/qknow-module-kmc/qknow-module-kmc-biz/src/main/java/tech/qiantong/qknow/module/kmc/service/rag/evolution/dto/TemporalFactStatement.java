package tech.qiantong.qknow.module.kmc.service.rag.evolution.dto;

/**
 * 时态事实声明三元组 (定理 1.2)
 * 封装事实唯一标识、主谓宾、业务生效时间 validTime、系统记录时间 recordTime、置信度、数据源与权威性权重及声明状态
 */
public record TemporalFactStatement(
        String statementId,
        String subjectId,
        String predicate,
        String objectId,
        long validTime,
        long recordTime,
        double confidence,
        String sourceDomain,
        double authorityWeight,
        FactStatus status
) {
    public enum FactStatus {
        ACTIVE,      // 活跃事实 (唯一生效)
        SUPERSEDED,  // 被覆写事实 (历史已失效)
        DISPUTED     // 争议事实 (证据冲突待仲裁)
    }

    public TemporalFactStatement withStatus(FactStatus newStatus) {
        return new TemporalFactStatement(
                statementId, subjectId, predicate, objectId,
                validTime, recordTime, confidence, sourceDomain, authorityWeight, newStatus
        );
    }
}
