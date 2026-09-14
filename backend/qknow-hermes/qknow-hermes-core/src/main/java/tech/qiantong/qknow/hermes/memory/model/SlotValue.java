package tech.qiantong.qknow.hermes.memory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DST 槽位键值对对象：SlotValue
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 槽位名称
     */
    private String slotName;

    /**
     * 槽位填充值
     */
    private Object value;

    /**
     * 提取置信度 \in [0.0, 1.0]
     */
    @Builder.Default
    private double confidence = 1.0;

    /**
     * 是否经过用户明确确认
     */
    @Builder.Default
    private boolean confirmed = false;

    /**
     * 更新时间戳
     */
    private long updatedAt;
}
