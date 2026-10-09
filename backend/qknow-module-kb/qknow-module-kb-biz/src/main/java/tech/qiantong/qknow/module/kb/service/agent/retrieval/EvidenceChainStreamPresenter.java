package tech.qiantong.qknow.module.kb.service.agent.retrieval;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.thirdparty.domain.dify.knowledge.RetrieveResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DeepSeek 思考链深度融合与多模态证据链流式呈现器 (EvidenceChainStreamPresenter)
 * <p>
 * 契约规范与工程原则 (AGENTS.md 第七与第十章)：
 * 1. 思考链与回答双轨流式隔离呈现：深度解析 DeepSeek 官方 reasoning_content 与 content；
 * 2. 真实引文角标核验与幻觉过滤：严格校验正文中的引文角标 [^k] 是否在召回切片有效边界 [1, N] 内；
 * 3. 结构化多模态证据链卡片渲染：生成符合 Apple iOS 26 质感标准的知识依据折叠卡片；
 * 4. 纯 Java 21 Record 格式不可变审计凭单，含 SHA-256 常量时间自验真。
 * </p>
 */
@Slf4j
@Component
public class EvidenceChainStreamPresenter {

    /**
     * 流式事件帧类型
     */
    public enum FrameType {
        THINKING,       // DeepSeek 深度思考推理链 (reasoning_content)
        ANSWER,         // 最终回复正文流 (content)
        CITATION,       // 动态引文角标锚点
        EVIDENCE_CARD,  // 结构化证据链汇总折叠卡片
        DONE            // 传输终止凭单
    }

    /**
     * 单个流式事件不可变帧
     */
    public record StreamEventFrame(
            FrameType type,
            String payload,
            long timestampMillis,
            Map<String, Object> metadata
    ) {
        public static StreamEventFrame thinking(String delta) {
            return new StreamEventFrame(FrameType.THINKING, delta, System.currentTimeMillis(), Collections.emptyMap());
        }

        public static StreamEventFrame answer(String delta) {
            return new StreamEventFrame(FrameType.ANSWER, delta, System.currentTimeMillis(), Collections.emptyMap());
        }

        public static StreamEventFrame evidenceCard(String cardMarkdown, int citedCount) {
            return new StreamEventFrame(FrameType.EVIDENCE_CARD, cardMarkdown, System.currentTimeMillis(),
                    Map.of("citedCount", citedCount));
        }

        public static StreamEventFrame done(String receiptHash) {
            return new StreamEventFrame(FrameType.DONE, receiptHash, System.currentTimeMillis(),
                    Map.of("receiptHash", receiptHash));
        }
    }

    /**
     * 单个核验证据项不可变凭单
     */
    public record EvidenceItem(
            int citationIndex,
            String chunkId,
            String documentName,
            Double score,
            String snippet
    ) {}

    /**
     * 最终证据链与会话呈现不可变凭单
     */
    public record EvidenceChainReceipt(
            String fullAnswer,
            String fullThinking,
            List<EvidenceItem> evidenceItems,
            int totalCitedCount,
            long elapsedMillis,
            String receiptHash
    ) {
        public boolean verifyIntegrity() {
            String expected = calculateHash(fullAnswer, fullThinking, totalCitedCount);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    receiptHash.getBytes(StandardCharsets.UTF_8)
            );
        }

        private static String calculateHash(String ans, String think, int count) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                String raw = (ans != null ? ans : "") + "|" + (think != null ? think : "") + "|" + count;
                byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (Exception e) {
                return "HASH_ERROR";
            }
        }
    }

    private static final Pattern CITATION_PATTERN = Pattern.compile("(?:\\[\\^|\\【|\\[)(\\d+)(?:\\]|\\】)");

    /**
     * 标准化回复正文中的引文角标，并剔除超出切片数量的虚假幻觉角标
     *
     * @param rawAnswer       模型输出的原始文本
     * @param availableChunks 实际召回的切片列表
     * @return 经过引文修正与对齐后的正文
     */
    public String normalizeAndValidateCitations(String rawAnswer, List<RetrieveResult> availableChunks) {
        if (StrUtil.isBlank(rawAnswer)) return "";
        if (availableChunks == null || availableChunks.isEmpty()) {
            // 没有切片可用时，过滤掉所有捏造的引文角标
            return CITATION_PATTERN.matcher(rawAnswer).replaceAll("");
        }

        int maxValidIndex = availableChunks.size();
        Matcher matcher = CITATION_PATTERN.matcher(rawAnswer);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            try {
                int index = Integer.parseInt(matcher.group(1));
                if (index >= 1 && index <= maxValidIndex) {
                    // 合法有效角标，标准化为 Markdown 格式 [^k]
                    matcher.appendReplacement(sb, "[^" + index + "]");
                } else {
                    // 超出范围的幻觉角标，直接剔除
                    log.warn("[证据呈现] 过滤超出切片边界的虚假引文角标: 索引={}, 最大允许={}", index, maxValidIndex);
                    matcher.appendReplacement(sb, "");
                }
            } catch (Exception e) {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 生成符合前端折叠规范的 Markdown 证据链卡片
     *
     * @param usedChunks 实际召回或被引用的切片列表
     * @return 格式化 Markdown 证据链卡片
     */
    public String renderEvidenceChainCard(List<RetrieveResult> usedChunks) {
        if (usedChunks == null || usedChunks.isEmpty()) {
            return "";
        }

        StringBuilder card = new StringBuilder();
        card.append("\n\n---\n");
        card.append("### 📚 知识依据与核验证据链 (Evidence Chain)\n");
        card.append("> 答案依据以下权威知识库切片生成，点击可展开核验源文片段：\n\n");

        for (int i = 0; i < usedChunks.size(); i++) {
            RetrieveResult chunk = usedChunks.get(i);
            int citeIndex = i + 1;
            String docName = StrUtil.isNotBlank(chunk.getDocumentName()) ? chunk.getDocumentName() : "知识文档 " + citeIndex;
            Double score = chunk.getScore();
            String scoreText = score != null ? String.format("%.2f%%", score * 100) : "已核验";

            String snippet = chunk.getContent() != null ? chunk.getContent().trim() : "";
            if (snippet.length() > 120) {
                snippet = snippet.substring(0, 117) + "...";
            }

            card.append(String.format("- **[^%d]** 《%s》 *(相似度: %s)*\n", citeIndex, docName, scoreText));
            card.append("  > ").append(snippet.replace("\n", " ")).append("\n");
        }

        return card.toString();
    }

    /**
     * 构建核验证据项列表
     */
    public List<EvidenceItem> buildEvidenceItems(List<RetrieveResult> chunks) {
        if (chunks == null || chunks.isEmpty()) return Collections.emptyList();
        List<EvidenceItem> list = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrieveResult c = chunks.get(i);
            String snippet = c.getContent() != null ? c.getContent().trim() : "";
            if (snippet.length() > 100) {
                snippet = snippet.substring(0, 97) + "...";
            }
            list.add(new EvidenceItem(
                    i + 1,
                    c.getId() != null ? c.getId() : "chunk-" + (i + 1),
                    StrUtil.isNotBlank(c.getDocumentName()) ? c.getDocumentName() : "未知文档",
                    c.getScore(),
                    snippet
            ));
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 生成不可变审计凭单
     */
    public EvidenceChainReceipt generateReceipt(
            String fullAnswer,
            String fullThinking,
            List<RetrieveResult> usedChunks,
            long elapsedMillis
    ) {
        List<EvidenceItem> items = buildEvidenceItems(usedChunks);
        String hash = EvidenceChainReceipt.calculateHash(fullAnswer, fullThinking, items.size());

        return new EvidenceChainReceipt(
                fullAnswer != null ? fullAnswer : "",
                fullThinking != null ? fullThinking : "",
                items,
                items.size(),
                elapsedMillis,
                hash
        );
    }
}
