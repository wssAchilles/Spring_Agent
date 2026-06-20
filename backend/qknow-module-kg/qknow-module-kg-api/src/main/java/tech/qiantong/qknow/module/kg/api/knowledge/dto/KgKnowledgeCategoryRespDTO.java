package tech.qiantong.qknow.module.kg.api.knowledge.dto;

import lombok.*;

/**
 * 知识分类 DTO 对象 kg_knowledge_category
 *
 * @author qknow
 * @date 2025-10-20
 */
@Data
public class KgKnowledgeCategoryRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 图谱id */
    private Long graphId;

    /** 父级id */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 显示顺序 */
    private Long orderNum;

    /** 祖级列表 */
    private String ancestors;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
