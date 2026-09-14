package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.*;

/**
 * DST 对话状态帧：DialogueStateFrame
 * 支持：当前意图、已填槽位、状态类型（ACTIVE, SUSPENDED, COMPLETED）、挂起栈帧（用于分支插话恢复）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialogueStateFrame implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum StateStatus {
        ACTIVE,
        SUSPENDED,
        COMPLETED
    }

    /**
     * 会话 ID
     */
    private String sessionId;

    /**
     * 当前主活跃意图 (如 METRIC_DIAGNOSIS, SYSTEM_LOAD_QUERY)
     */
    private String currentIntent;

    /**
     * 当前意图绑定的槽位集合 (slotName -> SlotValue)
     */
    @Builder.Default
    private Map<String, SlotValue> slots = new HashMap<>();

    /**
     * 分支插话挂起栈帧 (保存被挂起的上一级状态帧，先进后出)
     */
    @Builder.Default
    private Deque<DialogueStateFrame> intentStack = new ArrayDeque<>();

    /**
     * 状态帧生命周期状态
     */
    @Builder.Default
    private StateStatus status = StateStatus.ACTIVE;

    /**
     * 最近更新时间戳
     */
    private long updatedAt;

    /**
     * 设置槽位值
     */
    public void setSlot(String slotName, Object value, double confidence) {
        slots.put(slotName, SlotValue.builder()
                .slotName(slotName)
                .value(value)
                .confidence(confidence)
                .confirmed(true)
                .updatedAt(System.currentTimeMillis())
                .build());
    }

    /**
     * 获取指定槽位值
     */
    public Object getSlotVal(String slotName) {
        SlotValue sv = slots.get(slotName);
        return sv != null ? sv.getValue() : null;
    }
}
