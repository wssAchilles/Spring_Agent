package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.domain.BaseEntity;

/**
 * 知识库角色关联 创建/修改 Request VO kmc_knowledge_role
 *
 * @author qknow
 * @date 2025-07-24
 */
@Schema(description = "知识库角色关联 Response VO")
@Data
public class KmcKnowledgeRoleSaveReqVO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "知识库id", example = "")
    private Long knowledgeId;

    @Schema(description = "角色id", example = "")
    private Long roleId;

    @Schema(description = "角色ids", example = "")
    private String roleIds;

}
