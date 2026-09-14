package tech.qiantong.qknow.module.kmc.service.sync.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Phase 27: 多模态高保真文档解析统一产物
 * 包含：拓扑重构后的完整 Markdown、版面拓扑元素清单、结构化表格与图表切片实体及性能指标。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDocumentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务/文档追踪标识 */
    private String taskId;

    /** 文档原始名称 */
    private String documentName;

    /** 拓扑阅读序重构后的高保真 Markdown 文本 */
    private String fullMarkdown;

    /** 文档总页数 */
    private Integer totalPages;

    /** 结构化版面元素清单 (按拓扑阅读序排列) */
    private List<ParsedLayoutElementDTO> elements;

    /** 抽取的表格结构清单 (包含 HTML 与 Markdown 双重表达) */
    private List<ExtractedTableDTO> tables;

    /** 抽取的图表切片及视觉语义增强摘要 */
    private List<ExtractedFigureDTO> figures;

    /** 额外性能指标 (解析耗时、OCR 页数、是否触发降级等) */
    private Map<String, Object> metrics;
}
