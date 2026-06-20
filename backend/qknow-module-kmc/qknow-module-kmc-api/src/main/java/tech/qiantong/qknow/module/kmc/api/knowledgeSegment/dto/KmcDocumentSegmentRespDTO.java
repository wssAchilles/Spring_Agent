package tech.qiantong.qknow.module.kmc.api.knowledgeSegment.dto;

import lombok.Data;

/**
 * 文件分段 DTO 对象 kmc_document_segment
 *
 * @author qknow
 * @date 2025-08-28
 */
@Data
public class KmcDocumentSegmentRespDTO {

    private static final long serialVersionUID = 1L;

    /** ID */
    private Long id;

    /** 工作空间id */
    private Long workspaceId;

    /** 文件名称 */
    private String documentName;

    /** 文件id */
    private Long documentId;

    /** dify段落id */
    private String qmSegmentId;

    /** 位置 */
    private Long position;

    /** dify所属文档ID */
    private String qmDocumentId;

    /** 分段内容文本 */
    private String content;

    /** 签名内容文本 */
    private String signContent;

    /** 答案内容(如果有) */
    private String answer;

    /** 内容长度 */
    private Long wordCount;

    /** token数量 */
    private Long tokens;

    /** 关键词 */
    private String keywords;

    /** 索引节点ID */
    private String indexNodeId;

    /** 索引节点哈希值 */
    private String indexNodeHash;

    /** 访问次数 */
    private Long hitCount;

    /** 启用状态 */
    private Integer enabled;

    /** 状态 */
    private String status;

    /** 完成时间戳 */
    private String completedAt;

    /** 错误信息 */
    private String error;

    /** 子模块 */
    private String childChunks;

    /** 分段添加dify状态 */
    private Integer syncStatus;



    /** 父级id */
    private String parentId;

    /** 是否有效 */
    private Boolean validFlag;

    /** 删除标志 */
    private Boolean delFlag;


}
