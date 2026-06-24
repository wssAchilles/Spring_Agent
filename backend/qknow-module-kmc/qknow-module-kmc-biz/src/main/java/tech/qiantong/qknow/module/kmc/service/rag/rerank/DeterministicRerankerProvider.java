package tech.qiantong.qknow.module.kmc.service.rag.rerank;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.module.kmc.service.rag.model.QueryIntent;
import tech.qiantong.qknow.module.kmc.service.rag.model.RetrievalResult;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class DeterministicRerankerProvider implements RerankerProvider {

    @Override
    public String name() {
        return "deterministic";
    }

    @Override
    public boolean supports(RerankRequestContext context) {
        return true;
    }

    @Override
    public List<RetrievalResult> rerank(RerankRequestContext context, List<RetrievalResult> candidates,
                                        QueryIntent queryIntent, int topK) {
        for (RetrievalResult candidate : candidates) {
            double bonus = 0.0;
            String docName = candidate.getDocumentName();
            String content = candidate.getContent();

            if (queryIntent.getDayNo() != null && StrUtil.isNotBlank(docName)) {
                String dayPattern = String.format("Day%02d", queryIntent.getDayNo());
                if (docName.contains(dayPattern) || docName.contains("Day" + queryIntent.getDayNo())) {
                    bonus += 3.0;
                }
            }

            if (StrUtil.isNotBlank(queryIntent.getDocName()) && StrUtil.isNotBlank(docName)) {
                if (docName.contains(queryIntent.getDocName())) {
                    bonus += 2.0;
                }
            }

            if (CollUtil.isNotEmpty(queryIntent.getKeywords()) && StrUtil.isNotBlank(content)) {
                for (String keyword : queryIntent.getKeywords()) {
                    if (content.contains(keyword)) {
                        bonus += 1.0;
                    }
                }
            }

            candidate.setScore(candidate.getScore() + bonus);
        }

        return candidates.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}
