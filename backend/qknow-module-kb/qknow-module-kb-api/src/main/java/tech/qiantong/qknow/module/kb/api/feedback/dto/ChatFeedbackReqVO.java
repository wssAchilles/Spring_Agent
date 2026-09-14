package tech.qiantong.qknow.module.kb.api.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户对话反馈请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatFeedbackReqVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话 ID
     */
    private String conversationId;

    /**
     * 消息 ID
     */
    private String messageId;

    /**
     * 用户 ID（未登录可为 anonymous）
     */
    private String userId;

    /**
     * 反馈类型：UPVOTE(点赞), DOWNVOTE(点踩), COPY(复制), DWELL(停留), CORRECTION(纠错修改)
     */
    private String feedbackType;

    /**
     * 显式评分 (1-5，可选)
     */
    private Integer rating;

    /**
     * 停留交互时长（毫秒）
     */
    private Long dwellTimeMs;

    /**
     * 用户文字反馈/评论
     */
    private String comment;

    /**
     * 用户修正后的回答文本
     */
    private String correctedText;

    /**
     * 当时采用的检索路由策略 (VECTOR, BM25, HYBRID, GRAPH, MULTI_KB)
     */
    private String actionType;

    /**
     * 选中文档的展示排序位次（从 1 开始计数，用于 IPS 逆倾向加权计算）
     */
    private Integer positionIdx;

    /**
     * 客户端时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * HMAC-SHA256 加盐防伪签名
     */
    private String signature;
}
