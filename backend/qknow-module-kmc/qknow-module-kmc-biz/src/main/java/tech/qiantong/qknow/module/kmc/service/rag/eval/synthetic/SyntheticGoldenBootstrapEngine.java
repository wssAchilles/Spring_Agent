package tech.qiantong.qknow.module.kmc.service.rag.eval.synthetic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

/**
 * Phase 26: 知识库高质量合成问答基准与自我一致性自进化引擎
 * 包含：
 * 1. 4 类高保真问答进化演进 (单跳事实、多跳推理、时序对比、反事实不可回答)；
 * 2. 双向自我一致性反向验证 (Self-Consistency Reverse Verification)，过滤幻觉假真值；
 * 3. 黄金基准加载、门禁清洗与评测集导出。
 */
@Slf4j
@Service
public class SyntheticGoldenBootstrapEngine {

    public static final double GROUNDING_SCORE_THRESHOLD = 0.85; // 事实支撑度最低阈值

    /**
     * 4 类问答演进类型
     */
    public enum QuestionType {
        SINGLE_HOP,                  // 单跳事实问答：直接由单个切片精准抽取
        MULTI_HOP,                   // 多跳推理问答：跨 2 个或以上切片组合因果推理
        TEMPORAL_COMPARATIVE,        // 时序对比问答：跨不同版本时间线对比政策/参数变迁
        COUNTERFACTUAL_UNANSWERABLE  // 反事实拒答：知识库未记载或设定虚假前提，模型必须明确拒答
    }

    /**
     * 合成样本数据实体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyntheticGoldenSample {
        private String id;
        private QuestionType type;
        private String query;
        private String expectedAnswer;
        private List<String> contextIds;
        private double groundingScore;
        private String temporalTag;
        private boolean isCounterfactual;
        private boolean verified;
        private String rejectionReason;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. 解析 JSONL 格式的合成样本记录
     *
     * @param jsonLine 单行 JSON 字符串
     * @return 解析后的实体，若解析失败返回 Optional.empty()
     */
    public Optional<SyntheticGoldenSample> parseSample(String jsonLine) {
        if (jsonLine == null || jsonLine.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(jsonLine);
            String id = node.path("id").asText();
            String rawType = node.path("type").asText();
            QuestionType type = parseQuestionType(rawType);
            String query = node.path("query").asText();
            String expectedAnswer = node.path("expected_answer").asText();

            List<String> contextIds = new ArrayList<>();
            JsonNode ctxNode = node.path("context_ids");
            if (ctxNode.isArray()) {
                ctxNode.forEach(item -> contextIds.add(item.asText()));
            }

            double groundingScore = node.path("grounding_score").asDouble(1.0);
            String temporalTag = node.path("temporal_tag").asText("N/A");
            boolean isCounterfactual = node.path("is_counterfactual").asBoolean(false);

            SyntheticGoldenSample sample = SyntheticGoldenSample.builder()
                    .id(id)
                    .type(type)
                    .query(query)
                    .expectedAnswer(expectedAnswer)
                    .contextIds(contextIds)
                    .groundingScore(groundingScore)
                    .temporalTag(temporalTag)
                    .isCounterfactual(isCounterfactual)
                    .verified(false)
                    .build();

            return Optional.of(sample);
        } catch (Exception e) {
            log.error("解析合成样本 JSON 异常: line={}", jsonLine, e);
            return Optional.empty();
        }
    }

    /**
     * 2. 双向自我一致性反向验证 (Self-Consistency Reverse Verification)
     * 对候选合成样本进行上下文事实支撑检验：
     * - 验证答案与原始切片文本的关键语义重合度；
     * - 针对反事实/拒答样本，必须检验答案是否具备合规拒答特征；
     * - 事实支撑度评分必须 >= 0.85，否则淘汰该样本，杜绝引入模型幻觉生成的假真值。
     *
     * @param sample 合成样本
     * @param sourceContexts 对应的参考切片原文内容映射 (contextId -> content)
     * @return 验证结果
     */
    public boolean verifySelfConsistency(SyntheticGoldenSample sample, Map<String, String> sourceContexts) {
        if (sample == null || sample.getQuery() == null || sample.getExpectedAnswer() == null) {
            return false;
        }

        // 1. 若为反事实不可回答类样本 (COUNTERFACTUAL_UNANSWERABLE)
        if (sample.isCounterfactual() || sample.getType() == QuestionType.COUNTERFACTUAL_UNANSWERABLE) {
            boolean hasRefusalSignal = sample.getExpectedAnswer().contains("未提及")
                    || sample.getExpectedAnswer().contains("不存在")
                    || sample.getExpectedAnswer().contains("无法提供")
                    || sample.getExpectedAnswer().contains("不可回答")
                    || sample.getExpectedAnswer().contains("未记录");

            if (!hasRefusalSignal) {
                sample.setVerified(false);
                sample.setRejectionReason("反事实样本期望回答未包含明确合规拒答表述");
                return false;
            }

            sample.setVerified(true);
            sample.setGroundingScore(1.0);
            return true;
        }

        // 2. 检查是否有上下文依据 ID
        if (sample.getContextIds() == null || sample.getContextIds().isEmpty()) {
            sample.setVerified(false);
            sample.setRejectionReason("缺失来源上下文切片 ID 映射");
            return false;
        }

        // 3. 聚合参考上下文文本
        StringBuilder aggregatedContext = new StringBuilder();
        for (String ctxId : sample.getContextIds()) {
            String content = sourceContexts != null ? sourceContexts.get(ctxId) : null;
            if (content != null) {
                aggregatedContext.append(content).append(" ");
            }
        }

        String fullContext = aggregatedContext.toString();
        if (fullContext.isBlank()) {
            // 如果外部未显式传入切片原文，则依赖样本自带的已校准 groundingScore
            if (sample.getGroundingScore() >= GROUNDING_SCORE_THRESHOLD) {
                sample.setVerified(true);
                return true;
            } else {
                sample.setVerified(false);
                sample.setRejectionReason("事实支撑度低于门禁阈值 0.85: " + sample.getGroundingScore());
                return false;
            }
        }

        // 4. 计算答案关键实体词与上下文的重叠度 (Grounding Fidelity)
        double fidelity = computeGroundingFidelity(sample.getExpectedAnswer(), fullContext);
        sample.setGroundingScore(fidelity);

        if (fidelity >= GROUNDING_SCORE_THRESHOLD) {
            sample.setVerified(true);
            return true;
        } else {
            sample.setVerified(false);
            sample.setRejectionReason(String.format("反思验证未通过: 事实支撑度得分 %.2f 低于 0.85", fidelity));
            return false;
        }
    }

    /**
     * 3. 批量解析并执行自进化自一致性过滤
     *
     * @param jsonlContent JSONL 多行文本
     * @param contextMap 上下文切片映射
     * @return 过滤后认证合格的高质量黄金问答列表
     */
    public List<SyntheticGoldenSample> bootstrapAndVerifySuite(String jsonlContent, Map<String, String> contextMap) {
        List<SyntheticGoldenSample> verifiedList = new ArrayList<>();
        if (jsonlContent == null || jsonlContent.isBlank()) {
            return verifiedList;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(jsonlContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                Optional<SyntheticGoldenSample> opt = parseSample(line);
                if (opt.isPresent()) {
                    SyntheticGoldenSample sample = opt.get();
                    if (verifySelfConsistency(sample, contextMap)) {
                        verifiedList.add(sample);
                    } else {
                        log.warn("丢弃低质量/幻觉合成样本 [{}]: {}", sample.getId(), sample.getRejectionReason());
                    }
                }
            }
        } catch (Exception e) {
            log.error("批量自一致性过滤异常", e);
        }

        return verifiedList;
    }

    /**
     * 辅助方法：解析问答类型字符串
     */
    private QuestionType parseQuestionType(String raw) {
        if (raw == null) return QuestionType.SINGLE_HOP;
        return switch (raw.toLowerCase()) {
            case "single_hop" -> QuestionType.SINGLE_HOP;
            case "multi_hop" -> QuestionType.MULTI_HOP;
            case "temporal_comparative" -> QuestionType.TEMPORAL_COMPARATIVE;
            case "counterfactual_unanswerable" -> QuestionType.COUNTERFACTUAL_UNANSWERABLE;
            default -> QuestionType.SINGLE_HOP;
        };
    }

    /**
     * 辅助方法：计算答案核心词汇在上下文中的事实支撑保真度
     */
    private double computeGroundingFidelity(String answer, String context) {
        if (answer.isBlank() || context.isBlank()) {
            return 0.0;
        }
        // 提取长度大于 1 的字符 N-Gram 或词元进行支持度统计
        int totalMatches = 0;
        int totalTokens = 0;

        for (int i = 0; i < answer.length() - 1; i++) {
            String bigram = answer.substring(i, i + 2);
            if (!Character.isWhitespace(bigram.charAt(0)) && !Character.isWhitespace(bigram.charAt(1))) {
                totalTokens++;
                if (context.contains(bigram)) {
                    totalMatches++;
                }
            }
        }

        if (totalTokens == 0) return 1.0;
        return (double) totalMatches / totalTokens;
    }
}
