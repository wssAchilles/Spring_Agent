package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import tech.qiantong.qknow.common.core.page.PageParam;

/**
 * 知识库角色关联 Request VO 对象 kmc_knowledge_role
 *
 * @author qknow
 * @date 2025-07-24
 */
@Schema(description = "知识库角色关联 Request VO")
@Data
public class KmcKnowledgeRolePageReqVO extends PageParam {

    private static final long serialVersionUID = 1L;
    @Schema(description = "ID", example = "")
    private Long id;
    @Schema(description = "工作区id", example = "")
    private Long workspaceId;

    @Schema(description = "知识库id", example = "")
    private Long knowledgeId;

    @Schema(description = "角色id", example = "")
    private Long roleId;




}
