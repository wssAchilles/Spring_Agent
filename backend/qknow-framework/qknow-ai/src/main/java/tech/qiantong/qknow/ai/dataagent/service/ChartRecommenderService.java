package tech.qiantong.qknow.ai.dataagent.service;

import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.dataagent.model.ChartSpecVO;

import java.util.*;

/**
 * 自适应 ECharts 5.5 图表推荐器
 * 遵循 Wilkinson 图形语法与 Mackinlay APT 表达性与有效性准则 (定理 4.1)
 * 输出符合 Monochromatic Titanium Glassmorphism 单色钛金毛玻璃视觉规范的图表规格
 */
@Service
public class ChartRecommenderService {

    // 单色钛金毛玻璃专属色板
    private static final List<String> GLASS_PALETTE = Arrays.asList(
            "#38bdf8", "#818cf8", "#34d399", "#fbbf24", "#f43f5e", "#a78bfa"
    );

    /**
     * 根据查询结果集结构自适应推导最优图表规格
     *
     * @param columns 列名列表
     * @param rows    行记录数据列表 (Map<列名, 值>)
     * @return 结构化 ChartSpecVO 对象
     */
    public ChartSpecVO recommendChart(List<String> columns, List<Map<String, Object>> rows) {
        if (columns == null || columns.isEmpty() || rows == null || rows.isEmpty()) {
            return new ChartSpecVO("table", "空结果集默认以表格呈现", null, Collections.emptyList(), buildTableOption());
        }

        // 1. 推导列属性: 维度 (Temporal vs Categorical) 与 度量 (Numerical)
        String temporalCol = null;
        List<String> categoricalCols = new ArrayList<>();
        List<String> metricCols = new ArrayList<>();

        Map<String, Object> sampleRow = rows.get(0);
        for (String col : columns) {
            Object val = sampleRow.get(col);
            String lowerCol = col.toLowerCase();

            if (isTemporalColumn(lowerCol, val)) {
                if (temporalCol == null) temporalCol = col;
            } else if (isNumerical(val)) {
                metricCols.add(col);
            } else {
                categoricalCols.add(col);
            }
        }

        String categoricalCol = !categoricalCols.isEmpty() ? categoricalCols.get(0) : null;
        int rowCount = rows.size();

        // 2. 映射规则矩阵 (定理 4.1 单射判定)
        // 场景 1: 单行单度量 (纯单度量，或单分类+单度量，总分类维度 <= 1) -> 指标卡
        if (rowCount == 1 && metricCols.size() == 1 && categoricalCols.size() <= 1 && temporalCol == null) {
            String mCol = metricCols.get(0);
            String cardTitle = categoricalCol != null ? String.valueOf(rows.get(0).get(categoricalCol)) + " " + mCol : mCol;
            return new ChartSpecVO("metric-card", "单行数值聚合，推荐使用大号指标卡聚焦关键量化值", categoricalCol, metricCols, buildMetricCardOption(cardTitle, rows.get(0).get(mCol)));
        }

        // 场景 2: 时间序列维度 + 数值度量 -> 折线图/面积图
        if (temporalCol != null && !metricCols.isEmpty()) {
            return new ChartSpecVO("line", "包含时序维度与数值度量，推荐平滑时序面积折线图以展现趋势", temporalCol, metricCols, buildLineChartOption(temporalCol, metricCols, rows));
        }

        // 场景 3: 分类维度 + 单一数值度量 (2 <= Distinct <= 7) -> 饼图/环形图
        if (categoricalCol != null && metricCols.size() == 1 && rowCount >= 2 && rowCount <= 7) {
            return new ChartSpecVO("pie", "分类数量少于 7 项且包含占比度量，推荐极简环形占比图", categoricalCol, metricCols, buildPieChartOption(categoricalCol, metricCols.get(0), rows));
        }

        // 场景 4: 分类维度 + 度量 (8 <= Distinct <= 30) -> 柱状图
        if (categoricalCol != null && !metricCols.isEmpty() && rowCount >= 2 && rowCount <= 30) {
            return new ChartSpecVO("bar", "离散分类横向或纵向数值对比，推荐使用单色圆角柱状图", categoricalCol, metricCols, buildBarChartOption(categoricalCol, metricCols, rows));
        }

        // 场景 5: 多维表格降级
        return new ChartSpecVO("table", "多维或大结果集网格数据，推荐使用具备排序与检索功能的数据透视表格", categoricalCol != null ? categoricalCol : temporalCol, metricCols, buildTableOption());
    }

    private boolean isTemporalColumn(String colName, Object val) {
        if (colName.matches(".*(time|date|day|month|year|created_at|updated_at).*")) return true;
        if (val instanceof java.util.Date || val instanceof java.time.temporal.Temporal) return true;
        if (val instanceof String s && s.matches("^\\d{4}[-/]\\d{2}[-/]\\d{2}.*")) return true;
        return false;
    }

    private boolean isNumerical(Object val) {
        if (val == null) return false;
        return val instanceof Number;
    }

    private Map<String, Object> buildMetricCardOption(String title, Object value) {
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("title", title);
        opt.put("value", value != null ? value.toString() : "0");
        opt.put("theme", "titanium-glass");
        return opt;
    }

    private Map<String, Object> buildLineChartOption(String temporalCol, List<String> metricCols, List<Map<String, Object>> rows) {
        Map<String, Object> opt = new LinkedHashMap<>();
        List<String> xData = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get(temporalCol);
            xData.add(v != null ? v.toString() : "");
        }

        opt.put("tooltip", Map.of("trigger", "axis", "backgroundColor", "rgba(15, 23, 42, 0.9)", "borderColor", "rgba(255,255,255,0.1)"));
        opt.put("color", GLASS_PALETTE);
        opt.put("xAxis", Map.of("type", "category", "data", xData, "axisLine", Map.of("lineStyle", Map.of("color", "rgba(255,255,255,0.2)"))));
        opt.put("yAxis", Map.of("type", "value", "splitLine", Map.of("lineStyle", Map.of("color", "rgba(255,255,255,0.05)"))));

        List<Map<String, Object>> series = new ArrayList<>();
        for (String m : metricCols) {
            List<Object> yData = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                yData.add(r.get(m));
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", m);
            s.put("type", "line");
            s.put("smooth", true);
            s.put("data", yData);
            s.put("areaStyle", Map.of("opacity", 0.15));
            series.add(s);
        }
        opt.put("series", series);
        return opt;
    }

    private Map<String, Object> buildPieChartOption(String catCol, String metricCol, List<Map<String, Object>> rows) {
        Map<String, Object> opt = new LinkedHashMap<>();
        opt.put("tooltip", Map.of("trigger", "item", "backgroundColor", "rgba(15, 23, 42, 0.9)"));
        opt.put("color", GLASS_PALETTE);

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object name = r.get(catCol);
            Object value = r.get(metricCol);
            data.add(Map.of("name", name != null ? name.toString() : "未知", "value", value != null ? value : 0));
        }

        opt.put("series", List.of(Map.of(
                "type", "pie",
                "radius", List.of("45%", "70%"),
                "itemStyle", Map.of("borderRadius", 6, "borderColor", "#0f172a", "borderWidth", 2),
                "data", data
        )));
        return opt;
    }

    private Map<String, Object> buildBarChartOption(String catCol, List<String> metricCols, List<Map<String, Object>> rows) {
        Map<String, Object> opt = new LinkedHashMap<>();
        List<String> xData = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get(catCol);
            xData.add(v != null ? v.toString() : "");
        }

        opt.put("tooltip", Map.of("trigger", "axis", "backgroundColor", "rgba(15, 23, 42, 0.9)"));
        opt.put("color", GLASS_PALETTE);
        opt.put("xAxis", Map.of("type", "category", "data", xData));
        opt.put("yAxis", Map.of("type", "value", "splitLine", Map.of("lineStyle", Map.of("color", "rgba(255,255,255,0.05)"))));

        List<Map<String, Object>> series = new ArrayList<>();
        for (String m : metricCols) {
            List<Object> yData = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                yData.add(r.get(m));
            }
            series.add(Map.of("name", m, "type", "bar", "itemStyle", Map.of("borderRadius", List.of(4, 4, 0, 0)), "data", yData));
        }
        opt.put("series", series);
        return opt;
    }

    private Map<String, Object> buildTableOption() {
        return Map.of("type", "grid-table", "theme", "titanium-glass");
    }
}
