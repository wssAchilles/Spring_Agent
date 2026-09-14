package tech.qiantong.qknow.module.kmc.service.sync.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Phase 27: 抽取的图表/插图切片与视觉语义增强实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedFigureDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 图表唯一标识 */
    private String figureId;

    /** 所在页码 */
    private Integer pageNumber;

    /** 空间包围框 [x1, y1, x2, y2] */
    private List<Double> bbox;

    /** 持久化图像访问路径 (MinIO 或本地存储) */
    private String imageUri;

    /** 原文图表标题文字 (Caption) */
    private String caption;

    /** DeepSeek API 生成的结构化图表视觉语义摘要 */
    private String visionSummary;

    /** 注入正文的 Markdown 占位符引用块 */
    private String placeholderMarkdown;
}
