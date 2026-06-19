package tech.qiantong.qknow.module.kb.dal.dataobject.judge;

import lombok.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

@Data
@TableName(value = "ai_judge_score")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KbAiJudgeScoreDO extends BaseEntity {
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workspaceId;

    private Long conversationId;

    private Long messageId;

    private Double factualityScore;

    private Double relevanceScore;

    private Double instructionScore;

    private Double overallScore;

    private Boolean passed;

    private Integer retryCount;

    private String feedback;

    private Boolean validFlag;

    @TableLogic
    private Boolean delFlag;
}
