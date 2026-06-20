package tech.qiantong.qknow.module.kg.api.knowledge.dto;

import lombok.*;

/**
 * 知识文件 DTO 对象 kg_knowledge_document
 *
 * @author qknow
 * @date 2025-10-20
 */
@Data
public class KgKnowledgeDocumentReqDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 图谱id */
    private Long graphId;

    /** 知识分类id */
    private Long categoryId;

    /** 知识分类名称 */
    private String categoryName;

    /** 文件名称 */
    private String name;

    /** 文件路径 */
    private String path;

    /** 文件描述 */
    private String description;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
