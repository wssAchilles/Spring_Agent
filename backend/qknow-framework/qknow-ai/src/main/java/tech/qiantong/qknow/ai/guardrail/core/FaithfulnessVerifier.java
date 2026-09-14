package tech.qiantong.qknow.ai.guardrail.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailViolationType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输出侧事实忠实度与幽灵引用快速核验器 (FaithfulnessVerifier)
 *
 * 1. 幽灵引用查杀与自愈：核验输出中的引用标记是否越界，自动清洗幽灵引文字符；
 * 2. 事实忠实度初筛与预警降级：若输出内容与参考上下文词法严重脱节，自动追加合规预警横幅。
 *
 * @author qknow
 */
@Slf4j
@Component
public class FaithfulnessVerifier {

    public static final String ALERT_BANNER_PREFIX =
            "【合规提示】本回答部分推论未在知识库切片中获得完全引用证实，请结合业务实际谨慎参考。\n\n";

    // 匹配类似 [Doc-1], [Doc-999], [来源1], [1], [切片-1] 的引用角标
    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "\\[(?:Doc-|切片-|来源)?([A-Za-z0-9_-]+)\\]"
    );

    /**
     * 校验输出文本中的引文标签与实际装配切片集合是否一致 (幽灵引用查杀与自愈清洗)
     */
    public GuardrailDecision verifyCitations(String outputText, Set<String> validChunkIds) {
        if (outputText == null || outputText.isBlank()) {
            return GuardrailDecision.permit(outputText, 0L);
        }

        long startNano = System.nanoTime();
        Set<String> validSet = (validChunkIds != null) ? validChunkIds : Collections.emptySet();

        Matcher matcher = CITATION_PATTERN.matcher(outputText);
        boolean hasPhantom = false;
        List<String> phantomCitations = new ArrayList<>();

        StringBuffer cleanedSb = new StringBuffer();
        while (matcher.find()) {
            String citedId = matcher.group(1);
            // 排除系统脱敏占位符 (如 REDACTED_PHONE, REDACTED_ID_CARD 等)
            if (citedId.startsWith("REDACTED_")) {
                matcher.appendReplacement(cleanedSb, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            if (!validSet.contains(citedId)) {
                hasPhantom = true;
                phantomCitations.add(matcher.group());
                // 清洗移除幽灵引文字符
                matcher.appendReplacement(cleanedSb, "");
            } else {
                matcher.appendReplacement(cleanedSb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(cleanedSb);

        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        if (hasPhantom) {
            log.warn("检测到幽灵引用 (Phantom Citations): {}, 执行合规自愈清洗", phantomCitations);
            return GuardrailDecision.redact(
                    cleanedSb.toString(),
                    GuardrailViolationType.PHANTOM_CITATION,
                    "已自动清洗不存在的幽灵引用: " + String.join(", ", phantomCitations),
                    elapsedMicros
            );
        }

        return GuardrailDecision.permit(outputText, elapsedMicros);
    }

    /**
     * 校验输出文本与参考切片上下文的事实忠实度 (词法覆盖率与预警降级)
     */
    public GuardrailDecision verifyFaithfulness(String outputText, List<String> referenceContexts, double minOverlapRatio) {
        if (outputText == null || outputText.isBlank() || referenceContexts == null || referenceContexts.isEmpty()) {
            return GuardrailDecision.permit(outputText, 0L);
        }

        long startNano = System.nanoTime();

        Set<String> contextTokens = extractCharOrWordTokens(String.join(" ", referenceContexts));
        Set<String> outputTokens = extractCharOrWordTokens(outputText);

        if (outputTokens.isEmpty()) {
            return GuardrailDecision.permit(outputText, 0L);
        }

        // 计算输出词元在上下文中的覆盖比例
        int matchCount = 0;
        for (String token : outputTokens) {
            if (contextTokens.contains(token)) {
                matchCount++;
            }
        }

        double overlapRatio = (double) matchCount / outputTokens.size();
        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        if (overlapRatio < minOverlapRatio) {
            log.warn("输出事实忠实度不足: overlapRatio={:.4f} < threshold={:.4f}, 执行预警降级", overlapRatio, minOverlapRatio);
            String degradedText = ALERT_BANNER_PREFIX + outputText;
            return GuardrailDecision.degrade(
                    degradedText,
                    GuardrailViolationType.FAITHFULNESS_LOW,
                    String.format("词法重合度过低(%.2f < %.2f)，疑似脱离上下文虚构事实", overlapRatio, minOverlapRatio),
                    elapsedMicros
            );
        }

        return GuardrailDecision.permit(outputText, elapsedMicros);
    }

    private Set<String> extractCharOrWordTokens(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        // 中文字符与英文单词切分
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s+]", " ");
        String[] words = normalized.split("\\s+");
        for (String word : words) {
            if (word.length() > 1) {
                tokens.add(word);
            }
        }
        // 提取中文字符
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                tokens.add(String.valueOf(c).toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }
}
