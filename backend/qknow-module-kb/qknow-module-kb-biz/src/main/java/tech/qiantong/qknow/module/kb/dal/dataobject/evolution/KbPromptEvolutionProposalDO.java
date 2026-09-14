package tech.qiantong.qknow.module.kb.dal.dataobject.evolution;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import java.time.LocalDateTime;

/**
 * Prompt 自进化审核提案实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_prompt_evolution_proposal")
public class KbPromptEvolutionProposalDO extends BaseEntity {

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String proposalId;

    /**
     * 目标类型：AGENT, BOT, KNOWLEDGE_BASE
     */
    private String targetType;

    /**
     * 目标业务 ID
     */
    private String targetId;

    /**
     * 原始 Prompt
     */
    private String originalPrompt;

    /**
     * 提议优化 Prompt
     */
    private String proposedPrompt;

    /**
     * 差异比对摘要
     */
    private String diffSummary;

    /**
     * DeepSeek-R1 链式反思与归因论证
     */
    private String reflectionRationale;

    /**
     * 触发此进化的负反馈样本总数
     */
    private Integer negativeSampleCount;

    /**
     * 关联的负反馈样本 ID 集合（JSON 格式）
     */
    private String negativeSampleIds;

    /**
     * 状态：PENDING_REVIEW, APPROVED, REJECTED, ROLLED_BACK
     */
    private String status;

    /**
     * 审核人 ID
     */
    private String reviewerId;

    /**
     * 审核意见
     */
    private String reviewComment;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;
}
