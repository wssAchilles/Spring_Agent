package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaMatchDO.JudgementResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 39: DeepSeek-R1 链式深度思考与结构化 JSON 裁判引擎
 * 负责解析 <think> 思考链，提取结构化胜负判定，杜绝非结构化解析异常。
 *
 * @author qknow
 */
@Slf4j
@Service
public class DeepSeekR1JudgeEngine {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern THINK_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(\\{.*?\\})\\s*```", Pattern.DOTALL);

    /**
     * 构建双盲对决评估提示词
     */
    public String buildComparisonPrompt(String question, String answer1, String answer2, Map<String, String> criteria) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名公正严苛的 AI 评测裁判。请基于以下问题，对比两个匿名候选模型的回答。\n\n");
        sb.append("【用户问题】\n").append(question).append("\n\n");
        sb.append("【候选回答 1】\n").append(answer1).append("\n\n");
        sb.append("【候选回答 2】\n").append(answer2).append("\n\n");
        sb.append("【评估维度】准确性、事实忠实度、逻辑完备性、表达精炼度（严厉打击空洞注水）。\n\n");
        sb.append("请先在 <think> 标签内进行深入链式思考，权衡两个回答的优缺点与事实证据。\n");
        sb.append("最后在 ```json ``` 代码块中严格输出以下格式的 JSON 裁决：\n");
        sb.append("{\n");
        sb.append("  \"winner\": \"A\" 或 \"B\" 或 \"TIE\",\n");
        sb.append("  \"confidence\": 0.85,\n");
        sb.append("  \"rationale\": \"裁决归因说明\"\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 解析 DeepSeek-R1 裁判原始响应，抽取思考链与结构化判定
     */
    public JudgementResult parseJudgeResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return JudgementResult.builder()
                    .winner("TIE")
                    .confidence(0.5)
                    .rationale("裁判响应为空，默认平局")
                    .thinking("")
                    .build();
        }

        String thinking = "";
        Matcher thinkMatcher = THINK_PATTERN.matcher(rawResponse);
        if (thinkMatcher.find()) {
            thinking = thinkMatcher.group(1).trim();
        }

        String jsonContent = null;
        Matcher jsonMatcher = JSON_BLOCK_PATTERN.matcher(rawResponse);
        if (jsonMatcher.find()) {
            jsonContent = jsonMatcher.group(1).trim();
        } else {
            // 尝试寻找普通大括号 JSON
            int start = rawResponse.lastIndexOf('{');
            int end = rawResponse.lastIndexOf('}');
            if (start != -1 && end > start) {
                jsonContent = rawResponse.substring(start, end + 1);
            }
        }

        if (jsonContent != null) {
            try {
                JsonNode node = objectMapper.readTree(jsonContent);
                String winner = node.has("winner") ? node.get("winner").asText().trim().toUpperCase() : "TIE";
                if (!"A".equals(winner) && !"B".equals(winner) && !"TIE".equals(winner)) {
                    winner = "TIE";
                }
                double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.8;
                String rationale = node.has("rationale") ? node.get("rationale").asText() : "无归因说明";

                return JudgementResult.builder()
                        .winner(winner)
                        .confidence(Math.max(0.0, Math.min(1.0, confidence)))
                        .rationale(rationale)
                        .thinking(thinking)
                        .build();
            } catch (Exception e) {
                log.warn("解析裁判 JSON 失败: {}, 降级为文本启发式抽取", e.getMessage());
            }
        }

        // 容错降级抽取
        String winner = "TIE";
        if (rawResponse.contains("\"winner\": \"A\"") || rawResponse.contains("\"winner\":\"A\"")) {
            winner = "A";
        } else if (rawResponse.contains("\"winner\": \"B\"") || rawResponse.contains("\"winner\":\"B\"")) {
            winner = "B";
        }

        return JudgementResult.builder()
                .winner(winner)
                .confidence(0.6)
                .rationale("容错降级抽取结果")
                .thinking(thinking)
                .build();
    }
}
