package tech.qiantong.qknow.hermes.coalition.dto;

import java.util.List;

/**
 * 动态联盟拓扑结构 Record
 */
public record DynamicCoalitionStructure(
    String coalitionId,
    long generation,
    List<String> memberAgentIds,
    double totalCharacteristicValue,
    boolean isCoreStable,
    double superadditivityMargin,
    long formedTimestamp
) {
    public DynamicCoalitionStructure {
        if (coalitionId == null || coalitionId.isBlank()) {
            throw new IllegalArgumentException("coalitionId 不能为空");
        }
        if (memberAgentIds == null || memberAgentIds.isEmpty()) {
            throw new IllegalArgumentException("memberAgentIds 不能为空");
        }
    }
}
