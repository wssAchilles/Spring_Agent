package tech.qiantong.qknow.hermes.agent.debate;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * 辩论轮次记录 Record (不可变)
 * 记录单轮辩论中智能体的命题、反驳论据与证据指针
 */
public record DebateRoundRecord(
        String debateId,
        int roundIndex,
        String agentName,
        String proposition,
        String critiqueOrRebuttal,
        List<String> evidencePointers,
        long timestampMs
) implements Serializable {

    public DebateRoundRecord {
        Objects.requireNonNull(debateId, "debateId 不能为空");
        Objects.requireNonNull(agentName, "agentName 不能为空");
        evidencePointers = (evidencePointers == null) ? List.of() : List.copyOf(evidencePointers);
    }
}
