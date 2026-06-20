package tech.qiantong.qknow.module.kmc.api.knowledgeBase.dto;

import lombok.Data;

/**
 * 知识库角色关联 DTO 对象 kmc_knowledge_role
 *
 * @author qknow
 * @date 2025-07-24
 */
@Data
public class KmcKnowledgeRoleReqDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 知识库id */
    private Long knowledgeId;

    /** 角色id */
    private Long roleId;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
