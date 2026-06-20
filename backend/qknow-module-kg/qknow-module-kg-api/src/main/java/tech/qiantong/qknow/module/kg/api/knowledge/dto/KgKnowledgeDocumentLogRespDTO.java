package tech.qiantong.qknow.module.kg.api.knowledge.dto;

import lombok.*;

/**
 * 文件操作日志 DTO 对象 kg_knowledge_document_log
 *
 * @author qknow
 * @date 2025-10-22
 */
@Data
public class KgKnowledgeDocumentLogRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作区id */
    private Long workspaceId;

    /** 用户id */
    private Long userId;

    /** 用户名 */
    private String userName;

    /** 文件id */
    private Long documentId;

    /** 文件名 */
    private String documentName;

    /** 操作类型 */
    private String type;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
