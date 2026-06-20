package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 召回记录 创建/修改 Request VO kmc_knowledge_recall_log
 *
 * @author qknow
 * @date 2025-07-24
 */
@Schema(description = "召回记录 Response VO")
@Data
public class KmcKnowledgeRecallLogSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    @NotNull(message = "工作区id不能为空")
    private Long workspaceId;

    @Schema(description = "知识库id", example = "")
    private Long knowledgeId;

    @Schema(description = "问题", example = "")
    @NotBlank(message = "问题不能为空")
    @Size(max = 256, message = "问题长度不能超过256个字符")
    private String query;

    @Schema(description = "备注", example = "")
    @NotBlank(message = "备注不能为空")
    @Size(max = 512, message = "备注长度不能超过512个字符")
    private String remark;


}
