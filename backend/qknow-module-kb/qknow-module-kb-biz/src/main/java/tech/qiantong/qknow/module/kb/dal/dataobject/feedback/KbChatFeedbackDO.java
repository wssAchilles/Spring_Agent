package tech.qiantong.qknow.module.kb.dal.dataobject.feedback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 对话反馈持久化实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chat_feedback")
public class KbChatFeedbackDO extends BaseEntity {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String messageId;

    private String userId;

    private Long tenantId;

    private String feedbackType;

    private Integer rating;

    private Long dwellTimeMs;

    private String comment;

    private String correctedText;

    private String actionType;

    private Integer positionIdx;

    private Double ipsWeight;

    private String clientIp;

    private String signature;
}
