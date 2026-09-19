package tech.qiantong.qknow.ai.mor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.mor.model.ThinkingStreamInterruptionReceipt;

import java.util.*;
import java.util.regex.Pattern;

/**
 * DeepSeek R1 因果断点提炼与单轮自反思纠偏协调器 (定理 1.2)
 * 逆向扫描剔除死循环震荡片段，精准提取最后有效因果命题 (Last Valid Premise)，
 * 注入单轮强约束纠偏指引，引导模型跳出死循环直接输出确定性解答，并生成不可变密码学存证
 */
@Component
public class ReflectiveSelfHealingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveSelfHealingCoordinator.class);

    private static final List<String> OSCILLATION_KEYWORDS = List.of(
            "wait", "rethink", "however", "but actually", "hold on", "let me reconsider",
            "等一下", "重新想", "但是等等", "不对", "真的如此吗", "让我想想"
    );

    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[。！？!?\n;；])");

    /**
     * 自愈决策与结果
     */
    public record HealingPlan(
            String lastValidPremise,
            String reflectionPrompt,
            boolean isHealable,
            String fallbackAnswer
    ) {}

    /**
     * 从中断的思考流中提取最后有效因果命题并构建单轮纠偏计划
     *
     * @param fullThinkingProcess 截止中断时累积的全部思考推演文本
     * @param originalQuery       用户原始意图
     * @return 自愈计划
     */
    public HealingPlan coordinateHealing(String fullThinkingProcess, String originalQuery) {
        if (fullThinkingProcess == null || fullThinkingProcess.isBlank()) {
            return new HealingPlan(
                    "无前序有效推演",
                    "请直接根据已知事实完成作答，跳过深度推演。",
                    false,
                    "已检测到推演异常，已为您切换为直接解答模式。"
            );
        }

        String lastValidPremise = extractLastValidPremise(fullThinkingProcess);

        String reflectionPrompt = buildReflectionPrompt(lastValidPremise, originalQuery);
        String fallbackAnswer = String.format("【认知自愈答复】基于前序推演确认事实（%s），直接得出结论。",
                truncate(lastValidPremise, 80));

        return new HealingPlan(lastValidPremise, reflectionPrompt, true, fallbackAnswer);
    }

    /**
     * 逆向扫描文本，定位震荡前最后一个具备实体因果关系的非怀疑性命题
     */
    public String extractLastValidPremise(String thinkingProcess) {
        String[] sentences = SENTENCE_SPLIT_PATTERN.split(thinkingProcess);
        if (sentences.length == 0) {
            return truncate(thinkingProcess, 100);
        }

        // 从后往前寻找不包含震荡关键字的有效完整句子
        for (int i = sentences.length - 1; i >= 0; i--) {
            String s = sentences[i].trim();
            if (s.length() < 10) {
                continue; // 过滤过短碎片
            }

            boolean hasOscillation = false;
            String lower = s.toLowerCase();
            for (String kw : OSCILLATION_KEYWORDS) {
                if (lower.contains(kw)) {
                    hasOscillation = true;
                    break;
                }
            }

            if (!hasOscillation) {
                return s;
            }
        }

        // 若全部句子均有怀疑词，取前半段正文作为基准
        int midpoint = sentences.length / 2;
        return sentences[Math.max(0, midpoint)].trim();
    }

    /**
     * 构造结构化单轮自反思纠偏提示词 (Reflection Hint)
     */
    public String buildReflectionPrompt(String lastValidPremise, String originalQuery) {
        return String.join("\n",
                "[系统干预：链式思考纠偏指引]",
                "检测到前序链式推演陷入自我怀疑与循环假设。",
                "前序推演中已确认的关键因果依据为：",
                "「" + lastValidPremise + "」",
                "",
                "请严格遵循以下纪律直接输出最终业务答复：",
                "1. 立即停止任何推翻与反刍思考，严禁再次输出 \"Wait...\"、\"Let me rethink...\"；",
                "2. 针对用户问题「" + (originalQuery != null ? originalQuery : "") + "」，基于上述已确认依据直接得出最终结论；",
                "3. 保持回答简洁、精确、专业。"
        );
    }

    /**
     * 记录并生成中断与自愈存证凭单
     */
    public ThinkingStreamInterruptionReceipt recordInterruption(
            String sessionId,
            String traceId,
            String interruptionReason,
            long thinkingTokensSpent,
            long tokensSaved,
            double entropyScore,
            double ngramRepetitionScore,
            String healingAction
    ) {
        ThinkingStreamInterruptionReceipt receipt = ThinkingStreamInterruptionReceipt.create(
                sessionId,
                traceId,
                interruptionReason,
                thinkingTokensSpent,
                tokensSaved,
                entropyScore,
                ngramRepetitionScore,
                healingAction
        );

        log.info("[ReflectiveSelfHealing] 已生成密码学存证凭单: sessionId={}, tokensSaved={}, sig={}",
                sessionId, tokensSaved, receipt.signature());
        return receipt;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
