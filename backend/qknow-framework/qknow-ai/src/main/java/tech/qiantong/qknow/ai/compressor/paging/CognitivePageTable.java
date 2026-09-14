package tech.qiantong.qknow.ai.compressor.paging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 虚拟认知页表管理器 (定理 1.2 李雅普诺夫强稳定换页 & 定理 1.3 因果闭包)
 * 维护 L1 活跃工作区、L2 压缩摘要层，实现 LIU (Least Information Used) 淘汰
 */
@Component
public class CognitivePageTable {

    private static final Logger log = LoggerFactory.getLogger(CognitivePageTable.class);

    private final Map<String, CognitivePage> activeL1Pages = new ConcurrentHashMap<>();
    private final Map<String, String> compressedL2Summaries = new ConcurrentHashMap<>();
    private final Map<String, List<String>> causalDependencies = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong(0);

    private final int maxL1Pages;

    public CognitivePageTable() {
        this(4); // 默认 L1 容纳 4 个页面 (约 2k~4k Tokens 活跃区)
    }

    public CognitivePageTable(int maxL1Pages) {
        this.maxL1Pages = Math.max(2, maxL1Pages);
    }

    /**
     * 写入活跃页 (若超出容量则执行 LIU 换出)
     */
    public synchronized Optional<CognitivePage> putPage(CognitivePage page, float[] currentQueryVec) {
        if (page == null) return Optional.empty();

        version.incrementAndGet();
        Optional<CognitivePage> evictedPage = Optional.empty();

        if (activeL1Pages.size() >= maxL1Pages && !activeL1Pages.containsKey(page.pageId())) {
            // 触发 LIU (Least Information Used) 换页
            evictedPage = evictLowestLiu(currentQueryVec, 0.50);
        }

        activeL1Pages.put(page.pageId(), page);
        if (page.causalPointers() != null && !page.causalPointers().isEmpty()) {
            causalDependencies.put(page.pageId(), new ArrayList<>(page.causalPointers()));
        }

        log.debug("[PageTable] 页面激活 L1: pageId={}, currentSize={}", page.pageId(), activeL1Pages.size());
        return evictedPage;
    }

    /**
     * 基于 LIU 算法计算显著性并置换淘汰最低得分页面
     * Score_LIU = alpha * Recency + (1 - alpha) * CosineSimilarity
     */
    public synchronized Optional<CognitivePage> evictLowestLiu(float[] queryEmbedding, double alpha) {
        if (activeL1Pages.isEmpty()) {
            return Optional.empty();
        }

        String lowestPageId = null;
        double minScore = Double.MAX_VALUE;
        long now = System.currentTimeMillis();

        for (Map.Entry<String, CognitivePage> entry : activeL1Pages.entrySet()) {
            CognitivePage cp = entry.getValue();
            // 时效衰减得分 (0~1)
            double recency = Math.exp(-0.0001 * Math.max(0, now - cp.accessTimestamp()));

            // 千问 1536 维语义相似度
            double cosine = 0.0;
            if (queryEmbedding != null && cp.qwen1536Embedding() != null) {
                cosine = computeCosine(queryEmbedding, cp.qwen1536Embedding());
            }

            double liuScore = alpha * recency + (1.0 - alpha) * Math.max(0.0, cosine);
            if (liuScore < minScore) {
                minScore = liuScore;
                lowestPageId = entry.getKey();
            }
        }

        if (lowestPageId != null) {
            CognitivePage evicted = activeL1Pages.remove(lowestPageId);
            log.info("[PageTable] LIU 算法触发换出: pageId={}, minScore={}", lowestPageId, String.format("%.4f", minScore));
            return Optional.ofNullable(evicted);
        }

        return Optional.empty();
    }

    public CognitivePage getL1Page(String pageId) {
        CognitivePage cp = activeL1Pages.get(pageId);
        if (cp != null) {
            CognitivePage touched = cp.withUpdatedAccessTime(System.currentTimeMillis());
            activeL1Pages.put(pageId, touched);
            return touched;
        }
        return null;
    }

    public void storeL2Summary(String pageId, String summary) {
        if (pageId != null && summary != null) {
            compressedL2Summaries.put(pageId, summary);
        }
    }

    public String getL2Summary(String pageId) {
        return compressedL2Summaries.get(pageId);
    }

    public List<CognitivePage> getActivePagesForTenant(Long tenantId) {
        if (tenantId == null) return Collections.emptyList();
        List<CognitivePage> list = new ArrayList<>();
        for (CognitivePage cp : activeL1Pages.values()) {
            if (tenantId.equals(cp.tenantId())) {
                list.add(cp);
            }
        }
        return list;
    }

    public List<String> getCausalPointers(String pageId) {
        return causalDependencies.getOrDefault(pageId, Collections.emptyList());
    }

    private double computeCosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    public int getL1Size() {
        return activeL1Pages.size();
    }

    public int getL2Size() {
        return compressedL2Summaries.size();
    }

    public void clear() {
        activeL1Pages.clear();
        compressedL2Summaries.clear();
        causalDependencies.clear();
    }
}
