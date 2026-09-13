package tech.qiantong.qknow.module.kmc.service.rag.citation;

import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 精准溯源提取与生成侧引用评估器 (Citation Extractor & Grounding Verifier)
 * 依据 ALCE / RAGChecker / RAGFlow 工业规范，实现轻量级角标提取、越界虚假引用拦截与引用率度量。
 */
@Slf4j
@Component
public class CitationExtractor {

    private static final Pattern CITATION_PATTERN = Pattern.compile(
            "(?:\\[来源\\s*(\\d+)\\]|\\[(\\d+)\\]|【(?:来源)?\\s*(\\d+)】)"
    );

    /**
     * 从生成的答案文本中提取结构化引用角标，并与已装填的上下文段落严格锚定
     */
    public CitationReport extractAndEvaluate(String answer, List<RetrievalResult> emittedSources) {
        if (StrUtil.isBlank(answer)) {
            return CitationReport.builder()
                    .citations(Collections.emptyList())
                    .totalCitations(0)
                    .validCitations(0)
                    .citationPrecision(1.0)
                    .citationRecall(0.0)
                    .hasHallucinatedCitation(false)
                    .build();
        }

        List<RetrievalResult> safeSources = emittedSources != null ? emittedSources : Collections.emptyList();
        int totalEmitted = safeSources.size();

        Matcher matcher = CITATION_PATTERN.matcher(answer);
        List<CitationRef> citations = new ArrayList<>();
        Set<Integer> citedSourceIndices = new HashSet<>();
        int totalCitations = 0;
        int validCitations = 0;

        while (matcher.find()) {
            totalCitations++;
            String indexStr = matcher.group(1);
            if (indexStr == null) {
                indexStr = matcher.group(2);
            }
            if (indexStr == null) {
                indexStr = matcher.group(3);
            }

            int index;
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                continue;
            }

            if (index >= 1 && index <= totalEmitted) {
                RetrievalResult source = safeSources.get(index - 1);
                validCitations++;
                citedSourceIndices.add(index);
                String snippet = source.getContent();
                if (snippet != null && snippet.length() > 120) {
                    snippet = snippet.substring(0, 120) + "...";
                }

                citations.add(CitationRef.builder()
                        .index(index)
                        .rawMarker(matcher.group(0))
                        .segmentId(source.getSegmentId())
                        .documentId(source.getDocumentId())
                        .documentName(source.getDocumentName())
                        .snippet(snippet)
                        .valid(true)
                        .build());
            } else {
                // 越界引用：大模型虚构了超出上下文范围的引用编号
                citations.add(CitationRef.builder()
                        .index(index)
                        .rawMarker(matcher.group(0))
                        .segmentId(null)
                        .documentId(null)
                        .documentName("INVALID_SOURCE")
                        .snippet(null)
                        .valid(false)
                        .build());
            }
        }

        double precision = totalCitations == 0 ? 1.0 : (double) validCitations / totalCitations;
        double recall = totalEmitted == 0 ? 0.0 : (double) citedSourceIndices.size() / totalEmitted;
        boolean hasHallucinated = validCitations < totalCitations;

        return CitationReport.builder()
                .citations(citations)
                .totalCitations(totalCitations)
                .validCitations(validCitations)
                .citationPrecision(precision)
                .citationRecall(recall)
                .hasHallucinatedCitation(hasHallucinated)
                .build();
    }

    @Data
    @Builder
    public static class CitationRef {
        private int index;
        private String rawMarker;
        private Long segmentId;
        private Long documentId;
        private String documentName;
        private String snippet;
        private boolean valid;
    }

    @Data
    @Builder
    public static class CitationReport {
        private List<CitationRef> citations;
        private int totalCitations;
        private int validCitations;
        private double citationPrecision;
        private double citationRecall;
        private boolean hasHallucinatedCitation;
    }
}
