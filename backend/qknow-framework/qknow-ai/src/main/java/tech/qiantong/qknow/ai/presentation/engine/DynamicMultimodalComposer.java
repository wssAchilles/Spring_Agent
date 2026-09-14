package tech.qiantong.qknow.ai.presentation.engine;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.presentation.dto.MultimodalPresentationBlockDTO;
import tech.qiantong.qknow.ai.presentation.enums.PresentationBlockType;

import java.util.List;

/**
 * 动态多模态组件装配器
 * 
 * 遵循 .shared/ui-ux-pro-max 单色钛金毛玻璃设计规范。
 */
@Component
public class DynamicMultimodalComposer {

    public MultimodalPresentationBlockDTO composeMarkdownBlock(String blockId, String title, String markdownContent, int displayOrder) {
        return new MultimodalPresentationBlockDTO(blockId, PresentationBlockType.TEXT_MARKDOWN, title, markdownContent, displayOrder);
    }

    public MultimodalPresentationBlockDTO composeMetricCardBlock(String blockId, String title, String metricValue, String unit, double changeRate, int displayOrder) {
        JSONObject payload = new JSONObject();
        payload.put("value", metricValue);
        payload.put("unit", unit);
        payload.put("changeRate", changeRate);
        payload.put("trend", changeRate >= 0 ? "UP" : "DOWN");
        payload.put("colorTheme", "monochromatic-titanium");
        return new MultimodalPresentationBlockDTO(blockId, PresentationBlockType.METRIC_CARD, title, payload.toJSONString(), displayOrder);
    }

    public MultimodalPresentationBlockDTO composeEchartBlock(String blockId, String title, String chartType, List<String> xAxisData, List<Double> seriesData, int displayOrder) {
        JSONObject spec = new JSONObject();
        spec.put("chartType", chartType);
        JSONObject xAxis = new JSONObject();
        xAxis.put("type", "category");
        xAxis.put("data", xAxisData);
        spec.put("xAxis", xAxis);

        JSONObject yAxis = new JSONObject();
        yAxis.put("type", "value");
        spec.put("yAxis", yAxis);

        JSONObject series = new JSONObject();
        series.put("type", chartType.toLowerCase());
        series.put("data", seriesData);
        series.put("smooth", true);
        spec.put("series", List.of(series));

        return new MultimodalPresentationBlockDTO(blockId, PresentationBlockType.ECHART_SPEC, title, spec.toJSONString(), displayOrder);
    }

    public MultimodalPresentationBlockDTO composeActionBannerBlock(String blockId, String title, String actionableInstruction, String severity, int displayOrder) {
        JSONObject payload = new JSONObject();
        payload.put("instruction", actionableInstruction);
        payload.put("severity", severity);
        payload.put("requireConfirm", true);
        return new MultimodalPresentationBlockDTO(blockId, PresentationBlockType.ACTION_BANNER, title, payload.toJSONString(), displayOrder);
    }
}
