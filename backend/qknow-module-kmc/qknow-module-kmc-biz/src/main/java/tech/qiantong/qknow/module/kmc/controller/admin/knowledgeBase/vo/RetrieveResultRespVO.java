package tech.qiantong.qknow.module.kmc.controller.admin.knowledgeBase.vo;

import lombok.Data;

import java.util.List;

/**
 * 知识库检索结果
 *
 * @author qknow
 * @date 2025-03-17
 */
@Data
public class RetrieveResultRespVO {

    /** */
    private String id;

    /** */
    private Integer position;

    /** 文档id */
    private String documentId;

    /** 匹配的文档内容 */
    private String content;

    /** */
    private String answer;

    /** */
    private Integer wordCount;

    /** */
    private Integer tokens;

    /** 关键字 */
    private List<String> keywords;

    /** */
    private String indexNodeId;

    /** */
    private String indexNodeHash;

    /** */
    private Integer hitCount;

    /** */
    private Boolean enabled;

    /** */
    private Long disabledAt;

    /** */
    private String disabledBy;

    /** 状态 */
    private String status;

    /** */
    private String createdBy;

    /** */
    private Long createdAt;

    /** */
    private Long indexingAt;

    /** */
    private Long completedAt;

    /** */
    private String error;

    /** */
    private Long stoppedAt;

    /** 文档名称 */
    private String documentName;

    /** 相似度 */
    private Double score;

    /** 来源 */
    private String source;

    /** 调试信息 */
    private java.util.Map<String, Object> debugInfo;

}
