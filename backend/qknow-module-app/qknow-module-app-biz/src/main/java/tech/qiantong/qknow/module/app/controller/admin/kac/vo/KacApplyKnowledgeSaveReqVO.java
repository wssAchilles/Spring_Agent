package tech.qiantong.qknow.module.app.controller.admin.kac.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 应用知识库关联 创建/修改 Request VO kac_apply_knowledge
 *
 * @author qknow
 * @date 2026-06-22
 */
@Schema(description = "应用知识库关联 Save VO")
@Data
public class KacApplyKnowledgeSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "应用id", example = "")
    private Long applyId;

    @Schema(description = "知识库id", example = "")
    private Long knowledgeId;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;
}
