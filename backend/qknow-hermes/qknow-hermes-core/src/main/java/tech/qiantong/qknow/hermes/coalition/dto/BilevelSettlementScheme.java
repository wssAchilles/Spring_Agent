package tech.qiantong.qknow.hermes.coalition.dto;

import java.util.Map;

/**
 * 双层信贷清算方案 Record
 */
public record BilevelSettlementScheme(
    String settlementId,
    String coalitionId,
    double globalParetoUtility,
    Map<String, Double> shapleyCreditAllocations,
    double settlementResidual,
    boolean freeRidersNullified,
    long settledTimestamp
) {
    public BilevelSettlementScheme {
        if (settlementId == null || settlementId.isBlank()) {
            throw new IllegalArgumentException("settlementId 不能为空");
        }
        if (shapleyCreditAllocations == null) {
            throw new IllegalArgumentException("shapleyCreditAllocations 不能为空");
        }
    }
}
