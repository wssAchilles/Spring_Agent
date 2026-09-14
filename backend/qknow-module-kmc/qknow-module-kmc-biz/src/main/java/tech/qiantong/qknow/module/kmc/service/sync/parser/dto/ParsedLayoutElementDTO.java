package tech.qiantong.qknow.module.kmc.service.sync.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Phase 27: 结构化版面空间几何与语义元素
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedLayoutElementDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版面语义元素类别
     */
    public enum ElementType {
        TITLE,       // 标题元素 (#, ##, ###)
        TEXT,        // 普通正文段落
        TABLE,       // 表格区域
        FIGURE,      // 插图/图表区域
        CAPTION,     // 图表标题说明文字
        HEADER,      // 页眉 (通常剔除)
        FOOTER       // 页脚 (通常剔除)
    }

    /** 元素唯一ID */
    private String id;

    /** 所在页码 (1-based) */
    private Integer pageNumber;

    /** 语义类别 */
    private ElementType type;

    /** 归一化空间包围框 [x1, y1, x2, y2] */
    private List<Double> bbox;

    /** 提取的内容文本 */
    private String content;

    /** 字体大小 (用于标题层级判定) */
    private Double fontSize;

    /** 拓扑重构后的绝对阅读序 */
    private Integer readingOrder;
}
