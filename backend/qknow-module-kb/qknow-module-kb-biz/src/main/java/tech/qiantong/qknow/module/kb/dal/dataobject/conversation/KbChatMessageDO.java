package tech.qiantong.qknow.module.kb.dal.dataobject.conversation;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

@Data
@TableName(value = "chat_message")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KbChatMessageDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private Integer tokenCount;

    private String metadata;

    private Boolean validFlag;

    @TableLogic
    private Boolean delFlag;
}
