package tech.qiantong.qknow.module.kb.dal.dataobject.conversation;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

@Data
@TableName(value = "conversation")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KbConversationDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Long botId;

    private Long userId;

    private String title;

    private Integer status;

    private Boolean validFlag;

    @TableLogic
    private Boolean delFlag;
}
