package tech.qiantong.qknow.ai.dataagent.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 单色钛金毛玻璃 (Monochromatic Titanium Glassmorphism) ECharts 5.5 图表规格模型
 */
public class ChartSpecVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推荐图表类型: metric-card, line, bar, pie, table */
    private String chartType;
    /** 推荐理由与统计依据 (因果解释) */
    private String rationale;
    /** 识别出的主要维度字段 (分类/时序) */
    private String dimensionField;
    /** 识别出的度量数值字段列表 */
    private List<String> metricFields;
    /** 符合 ECharts 5.5 规范的完整 Option 选项字典 */
    private Map<String, Object> echartsOption;

    public ChartSpecVO() {}

    public ChartSpecVO(String chartType, String rationale, String dimensionField, List<String> metricFields, Map<String, Object> echartsOption) {
        this.chartType = chartType;
        this.rationale = rationale;
        this.dimensionField = dimensionField;
        this.metricFields = metricFields;
        this.echartsOption = echartsOption;
    }

    public String getChartType() { return chartType; }
    public void setChartType(String chartType) { this.chartType = chartType; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public String getDimensionField() { return dimensionField; }
    public void setDimensionField(String dimensionField) { this.dimensionField = dimensionField; }
    public List<String> getMetricFields() { return metricFields; }
    public void setMetricFields(List<String> metricFields) { this.metricFields = metricFields; }
    public Map<String, Object> getEchartsOption() { return echartsOption; }
    public void setEchartsOption(Map<String, Object> echartsOption) { this.echartsOption = echartsOption; }
}
