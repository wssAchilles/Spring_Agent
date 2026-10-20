package tech.qiantong.qknow.module.kmc.service.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalResult {

    /** RRF 排位倒数融合得分量纲 */
    public static final String SCORE_TYPE_RRF = "RRF";
    /** 原始余弦/向量相似度得分量纲 */
    public static final String SCORE_TYPE_COSINE = "COSINE";

    private Long segmentId;

    private String qmSegmentId;

    private String parentSegmentId;

    private Long documentId;

    private String documentName;

    private String content;

    private String answer;

    private double score;

    /** 得分量纲契约: RRF / COSINE（生产链路必须显式赋值，跨库门禁据此选择是否施加余弦阈值） */
    private String scoreType;

    private String source;

    private java.util.Map<String, Object> metadata;
}
