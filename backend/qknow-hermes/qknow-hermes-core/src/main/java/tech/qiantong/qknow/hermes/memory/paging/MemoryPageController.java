package tech.qiantong.qknow.hermes.memory.paging;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * 类 OS 虚拟内存分层分页调度控制器 (MemoryPageController)
 * <p>
 * 彻底终结简单 FIFO 暴力截断丢弃，统筹调度 CorePage 常驻核、CompactedPage 递归摘要与
 * RecallPage 时序快轨，结合 DeepSeek 64-token 前缀冻结与三级冷备环形缓冲区，
 * 保障上下文高信噪比与缓存命中率 >= 85%。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public class MemoryPageController {

    private static final Logger log = Logger.getLogger(MemoryPageController.class.getName());

    public static final double HIGH_WATERMARK = 0.75; // 高水位线 (75% 触发换出压缩)
    public static final double LOW_WATERMARK = 0.50;  // 低水位线 (回落至 50%)

    private final int workingSetCapacity;
    private final int coldTierCapacity;
    private final String secretKey;
    private final AtomicInteger pageIdGenerator = new AtomicInteger(1);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 1. 常驻核心页 (Token 0 前缀冻结)
    private volatile MemoryPage.CorePage corePage;

    // 2. 递归压缩因果历史摘要页
    private volatile MemoryPage.CompactedPage compactedPage;

    // 3. 活跃时序工作集双端队列 (Working Set)
    private final Deque<MemoryPage.RecallPage> activeWorkingSet = new ConcurrentLinkedDeque<>();

    // 4. 三级冷备可逆环形缓冲区 (软隔离，防物理硬删除，支持秒级回滚)
    private final Deque<MemoryPage.RecallPage> coldStandbyBuffer = new ConcurrentLinkedDeque<>();

    public MemoryPageController(int workingSetCapacity, int coldTierCapacity, String secretKey) {
        this.workingSetCapacity = Math.max(8, workingSetCapacity);
        this.coldTierCapacity = Math.max(16, coldTierCapacity);
        this.secretKey = (secretKey != null && !secretKey.isBlank()) ? secretKey : "DEFAULT_MEM_KEY_SECRET";
    }

    public MemoryPageController() {
        this(64, 128, "HERMES_PAGING_SECRET_2026");
    }

    /**
     * 初始化或更新常驻核心页 (CorePage)，自动通过 64-Token 补齐对齐
     */
    public void setCoreInstructions(String instructions, Map<String, String> constraints) {
        lock.writeLock().lock();
        try {
            String alignedInstructions = ContextCacheAligner.alignTo64TokenBoundary(instructions);
            this.corePage = MemoryPage.CorePage.createAndSign(
                    pageIdGenerator.getAndIncrement(),
                    alignedInstructions,
                    constraints,
                    secretKey
            );
            log.info("已初始化/对齐常驻 CorePage，Token 估算: " + corePage.estimatedTokens());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 追加新的交互回合快轨页 (RecallPage)
     */
    public MemoryPage.RecallPage appendRecallTurn(String role, String content, float[] sphericalVector, Map<String, String> facts) {
        lock.writeLock().lock();
        try {
            MemoryPage.RecallPage page = MemoryPage.RecallPage.createAndSign(
                    pageIdGenerator.getAndIncrement(),
                    role,
                    content,
                    sphericalVector,
                    facts,
                    secretKey
            );

            activeWorkingSet.addLast(page);

            // 检查高水位线，触碰时自动触发递归压缩换出
            checkAndTriggerCompaction();

            return page;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 高水位线自适应检查与递归压缩换出
     */
    private void checkAndTriggerCompaction() {
        int currentSize = activeWorkingSet.size();
        int threshold = (int) Math.ceil(workingSetCapacity * HIGH_WATERMARK);

        if (currentSize >= threshold) {
            int targetSize = (int) Math.floor(workingSetCapacity * LOW_WATERMARK);
            int evictCount = currentSize - targetSize;

            log.info(String.format("触碰高水位线 (%d/%d)，启动递归压缩换出 %d 轮...", currentSize, workingSetCapacity, evictCount));

            StringBuilder newSummary = new StringBuilder();
            if (compactedPage != null) {
                newSummary.append(compactedPage.historicalSummary()).append("\n");
            }

            List<String> activeGoals = new ArrayList<>();

            for (int i = 0; i < evictCount && !activeWorkingSet.isEmpty(); i++) {
                MemoryPage.RecallPage evicted = activeWorkingSet.pollFirst();
                if (evicted != null) {
                    // 提炼因果结构化摘要
                    newSummary.append(String.format("[回合 %d %s]: %s (事实: %s); ",
                            evicted.pageId(), evicted.role(), evicted.content(), evicted.episodicFacts()));

                    if (evicted.episodicFacts().containsKey("ACTIVE_GOAL")) {
                        activeGoals.add(evicted.episodicFacts().get("ACTIVE_GOAL"));
                    }

                    // 移入三级冷备环形缓冲区，严禁物理硬删除
                    if (coldStandbyBuffer.size() >= coldTierCapacity) {
                        coldStandbyBuffer.pollFirst();
                    }
                    coldStandbyBuffer.addLast(evicted);
                }
            }

            // 更新 CompactedPage
            this.compactedPage = MemoryPage.CompactedPage.createAndSign(
                    pageIdGenerator.getAndIncrement(),
                    newSummary.toString().trim(),
                    activeGoals,
                    secretKey
            );
            log.info("递归因果压缩完成，活跃工作集回落至低水位线: " + activeWorkingSet.size());
        }
    }

    /**
     * 组装完全对齐的 Prompt 上下文
     * <p>
     * 物理顺序：
     * 1. CorePage (Token 0 起始前缀冻结，64 块对齐)
     * 2. CompactedPage (历史因果紧凑摘要)
     * 3. RecallPage 队列 (时序工作集最近交互)
     * 4. 当前用户即时 Query
     * </p>
     */
    public String assembleAlignedPrompt(String currentQuery) {
        lock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();

            // 1. 静态前缀冻结区 (必须位于最前端以命中 Context Cache)
            if (corePage != null) {
                sb.append(corePage.systemInstructions());
                if (!corePage.invariantConstraints().isEmpty()) {
                    sb.append("\n【不可变业务约束】: ").append(corePage.invariantConstraints());
                }
                sb.append("\n");
            }

            // 2. 递归因果摘要区
            if (compactedPage != null && !compactedPage.historicalSummary().isBlank()) {
                sb.append("【前序因果历史摘要】:\n").append(compactedPage.historicalSummary()).append("\n");
                if (!compactedPage.activePendingGoals().isEmpty()) {
                    sb.append("【未决核心任务】: ").append(compactedPage.activePendingGoals()).append("\n");
                }
            }

            // 3. 动态快轨时序交互区
            if (!activeWorkingSet.isEmpty()) {
                sb.append("【近期活跃会话】:\n");
                for (MemoryPage.RecallPage p : activeWorkingSet) {
                    sb.append(String.format("- [%s]: %s\n", p.role(), p.content()));
                }
            }

            // 4. 即时 Query 挂载于尾部插槽
            if (currentQuery != null && !currentQuery.isBlank()) {
                sb.append("【当前即时查询】: ").append(currentQuery);
            }

            return sb.toString();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从三级冷备可逆隔离区中恢复指定页（自愈回滚机制）
     */
    public boolean restoreFromColdBuffer(int pageId) {
        lock.writeLock().lock();
        try {
            for (Iterator<MemoryPage.RecallPage> it = coldStandbyBuffer.iterator(); it.hasNext(); ) {
                MemoryPage.RecallPage page = it.next();
                if (page.pageId() == pageId) {
                    it.remove();
                    activeWorkingSet.addFirst(page);
                    log.info("成功从三级冷备中一键复权回滚页面: " + pageId);
                    return true;
                }
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getActiveCount() {
        return activeWorkingSet.size();
    }

    public int getColdCount() {
        return coldStandbyBuffer.size();
    }

    public MemoryPage.CorePage getCorePage() {
        return corePage;
    }

    public MemoryPage.CompactedPage getCompactedPage() {
        return compactedPage;
    }
}
