package tech.qiantong.qknow.module.kmc.service.sync.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Phase 27: 抽取的高保真表格拓扑结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedTableDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 单个表格单元格元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableCellDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer row;
        private Integer col;
        private Integer rowspan;
        private Integer colspan;
        private String text;
        private Boolean isHeader;
    }

    /** 表格唯一ID */
    private String tableId;

    /** 所在页码 */
    private Integer pageNumber;

    /** 空间包围框 [x1, y1, x2, y2] */
    private List<Double> bbox;

    /** 网格总行数与总列数 */
    private Integer rows;
    private Integer cols;

    /** 单元格原子列表 */
    private List<TableCellDTO> cells;

    /** 包含 colspan/rowspan 的标准 HTML <table> 表达 */
    private String htmlContent;

    /** 降级纯文本 Markdown Table 表达 */
    private String markdownContent;

    /** 是否包含跨行或跨列合并单元格 */
    private Boolean hasMergedCells;

    /** 是否为跨页续表 */
    private Boolean isContinuation;
}
