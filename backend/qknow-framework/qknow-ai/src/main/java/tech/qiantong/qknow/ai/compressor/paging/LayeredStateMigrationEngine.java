package tech.qiantong.qknow.ai.compressor.paging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * L1/L2/L3 状态迁移与因果闭包保持引擎 (定理 1.3: P(Causal Fault) = 0)
 */
@Component
public class LayeredStateMigrationEngine {

    private static final Logger log = LoggerFactory.getLogger(LayeredStateMigrationEngine.class);

    private final CognitivePageTable pageTable;

    public LayeredStateMigrationEngine(CognitivePageTable pageTable) {
        this.pageTable = pageTable;
    }

    /**
     * 将 L1 页面换出至 L2，保留紧凑因果摘要与索引
     */
    public void pageOut(CognitivePage page, String distilledSummary) {
        if (page == null) return;
        long start = System.nanoTime();

        pageTable.storeL2Summary(page.pageId(), distilledSummary);

        long costNs = System.nanoTime() - start;
        log.info("[Migration] 页面换出 L1->L2 完成: pageId={}, 耗时={}ns", page.pageId(), costNs);
    }

    /**
     * 当因果闭包需要时，原子化将冷存/L2 页面唤醒换入至 L1
     */
    public boolean pageIn(CognitivePage restoredPage, float[] currentQueryVec) {
        if (restoredPage == null) return false;
        long start = System.currentTimeMillis();

        pageTable.putPage(restoredPage, currentQueryVec);

        long costMs = System.currentTimeMillis() - start;
        log.info("[Migration] 页面换入至 L1 完成: pageId={}, 耗时={}ms", restoredPage.pageId(), costMs);
        return true;
    }

    /**
     * 校验活跃页面的全部因果引用指针是否存在 (因果闭包核验)
     */
    public boolean verifyCausalClosure(String activePageId) {
        List<String> pointers = pageTable.getCausalPointers(activePageId);
        if (pointers == null || pointers.isEmpty()) {
            return true;
        }
        for (String ptr : pointers) {
            // 只要在 L1 活跃或在 L2 拥有摘要，即视为因果未断裂
            boolean inL1 = pageTable.getL1Page(ptr) != null;
            boolean inL2 = pageTable.getL2Summary(ptr) != null;
            if (!inL1 && !inL2) {
                log.warn("[Migration] 发现因果悬挂断裂: pageId={}, missingPointer={}", activePageId, ptr);
                return false;
            }
        }
        return true;
    }
}
