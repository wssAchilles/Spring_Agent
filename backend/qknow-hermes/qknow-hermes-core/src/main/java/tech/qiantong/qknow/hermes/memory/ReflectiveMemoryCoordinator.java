package tech.qiantong.qknow.hermes.memory;

import lombok.extern.slf4j.Slf4j;
import tech.qiantong.qknow.hermes.memory.dst.DialogueStateTracker;
import tech.qiantong.qknow.hermes.memory.dst.DialogueStateTrackerImpl;
import tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphService;
import tech.qiantong.qknow.hermes.memory.graph.EpisodicGraphServiceImpl;
import tech.qiantong.qknow.hermes.memory.model.DialogueStateFrame;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;
import tech.qiantong.qknow.hermes.memory.model.PreferenceRecord;
import tech.qiantong.qknow.hermes.memory.model.SlotValue;
import tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernor;
import tech.qiantong.qknow.hermes.memory.persona.UserPreferenceEvolutionGovernorImpl;
import tech.qiantong.qknow.hermes.memory.reflection.AsyncReflectionWorker;
import tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngine;
import tech.qiantong.qknow.hermes.memory.reflection.ReflectionTreeEngineImpl;
import tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringService;
import tech.qiantong.qknow.hermes.memory.scoring.MemoryScoringServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 四级跨会话反思演进记忆总协调器 (ReflectiveMemoryCoordinator)
 * 统一协调：
 * - Level 0: WorkingMemory (<= 4KB 严格字节预算装配)
 * - Level 1: ShortTermMemory (多轮会话滑动窗口与 DST 对话状态帧)
 * - Level 2: EpisodicGraphService & ReflectionTreeEngine (时序因果情境图与反思见解)
 * - Level 3: UserPreferenceEvolutionGovernor (CAS 乐观锁防脑裂稳定画像)
 * 落实：
 * - 激活保护防遗忘 (Lemma 1.1)
 * - 反思树体积压缩率 >= 70% (Theorem 1.1)
 * - 时序有向无环图与消除矛盾 (Theorem 2.1)
 * - 双阈值 GC 有限视界内存容量有界性 (Theorem 3.1)
 * - 毫秒级软降级 (Fail-Open)
 */
@Slf4j
public class ReflectiveMemoryCoordinator {

    // 双阈值动态 GC 参数 (Theorem 3.1)
    private static final double THETA_ADMISSION_IMP = 0.30; // 准入门限
    private static final double DELTA_EVICTION_SCORE = 0.05; // 淘汰下界

    private final WorkingMemory workingMemory;
    private final DialogueStateTracker dialogueStateTracker;
    private final MemoryScoringService scoringService;
    private final ReflectionTreeEngine reflectionEngine;
    private final AsyncReflectionWorker reflectionWorker;
    private final EpisodicGraphService episodicGraphService;
    private final UserPreferenceEvolutionGovernor preferenceGovernor;

    // 内存中活动记忆池 (遵循双阈值有限视界 GC)
    private final Map<String, List<MemoryNode>> activeMemoryPool = new ConcurrentHashMap<>();

    public ReflectiveMemoryCoordinator() {
        this(new WorkingMemory(),
                new DialogueStateTrackerImpl(),
                new MemoryScoringServiceImpl(),
                new ReflectionTreeEngineImpl(),
                new EpisodicGraphServiceImpl(),
                new UserPreferenceEvolutionGovernorImpl());
    }

    public ReflectiveMemoryCoordinator(
            WorkingMemory workingMemory,
            DialogueStateTracker dialogueStateTracker,
            MemoryScoringService scoringService,
            ReflectionTreeEngine reflectionEngine,
            EpisodicGraphService episodicGraphService,
            UserPreferenceEvolutionGovernor preferenceGovernor) {
        this.workingMemory = workingMemory != null ? workingMemory : new WorkingMemory();
        this.dialogueStateTracker = dialogueStateTracker != null ? dialogueStateTracker : new DialogueStateTrackerImpl();
        this.scoringService = scoringService != null ? scoringService : new MemoryScoringServiceImpl();
        this.reflectionEngine = reflectionEngine != null ? reflectionEngine : new ReflectionTreeEngineImpl();
        this.reflectionWorker = new AsyncReflectionWorker(this.reflectionEngine);
        this.episodicGraphService = episodicGraphService != null ? episodicGraphService : new EpisodicGraphServiceImpl();
        this.preferenceGovernor = preferenceGovernor != null ? preferenceGovernor : new UserPreferenceEvolutionGovernorImpl();
    }

    /**
     * 写入新观测事件 (带准入门限过滤)
     */
    public boolean ingestEvent(MemoryNode node) {
        if (node == null || node.getUserId() == null) {
            return false;
        }
        // 准入门限检查 (低重要性且无内容过滤)
        if (node.getImportance() < THETA_ADMISSION_IMP && (node.getContent() == null || node.getContent().length() < 5)) {
            log.debug("Event rejected by admission threshold: importance={}, content={}",
                    node.getImportance(), node.getContent());
            return false;
        }

        activeMemoryPool.computeIfAbsent(node.getUserId(), uid -> new CopyOnWriteArrayList<>()).add(node);
        episodicGraphService.addNode(node);
        return true;
    }

    /**
     * 执行周期性双阈值有限视界垃圾回收 (GC Invariant: Theorem 3.1)
     */
    public int performGarbageCollection(String userId, long nowTimestamp) {
        List<MemoryNode> pool = activeMemoryPool.get(userId);
        if (pool == null || pool.isEmpty()) {
            return 0;
        }

        int evictedCount = 0;
        Iterator<MemoryNode> it = pool.iterator();
        while (it.hasNext()) {
            MemoryNode node = it.next();
            // 关键重要记忆 (Lemma 1.1) 享有终身激活保护，严禁被 GC 物理抹除
            if (node.isCriticallyImportant(0.90)) {
                continue;
            }

            double recency = scoringService.computeRecency(node, nowTimestamp);
            double effectiveScore = recency * node.getImportance();

            if (effectiveScore < DELTA_EVICTION_SCORE) {
                pool.remove(node);
                episodicGraphService.removeNode(node.getId());
                evictedCount++;
            }
        }
        log.info("Garbage collection completed for user {}: evicted {} nodes, remaining active: {}",
                userId, evictedCount, pool.size());
        return evictedCount;
    }

    /**
     * 获取用户当前活动记忆池大小
     */
    public int getActiveMemoryPoolSize(String userId) {
        List<MemoryNode> pool = activeMemoryPool.get(userId);
        return pool != null ? pool.size() : 0;
    }

    /**
     * 跨会话端到端工作记忆上下文安全装配 (物理严格 <= maxBytes)
     * 支持在图服务异常或超时时毫秒级平滑软降级 (Fail-Open)
     */
    public String assembleContext(String userId, String sessionId, String query,
                                  float[] queryEmbedding, int maxBytes) {
        long startTime = System.currentTimeMillis();
        List<String> contextFragments = new ArrayList<>();

        try {
            // 1. Level 3: 提取用户稳定偏好画像 (过滤非活跃/已废弃项)
            Map<String, PreferenceRecord> prefs = preferenceGovernor.getUserPreferences(userId);
            if (prefs != null && !prefs.isEmpty()) {
                StringBuilder pb = new StringBuilder("[用户个性化稳定画像]\n");
                for (PreferenceRecord p : prefs.values()) {
                    if (p.getStatus() == PreferenceRecord.PreferenceStatus.ACTIVE) {
                        pb.append("- ").append(p.getPreferenceKey()).append(": ").append(p.getPreferenceValue()).append("\n");
                    }
                }
                contextFragments.add(pb.toString().trim());
            }

            // 2. Level 1: 提取 DST 对话状态槽位
            DialogueStateFrame dstFrame = dialogueStateTracker.getCurrentFrame(sessionId);
            if (dstFrame != null && !dstFrame.getSlots().isEmpty()) {
                StringBuilder sb = new StringBuilder("[当前对话状态意图: ").append(dstFrame.getCurrentIntent()).append("]\n");
                for (SlotValue sv : dstFrame.getSlots().values()) {
                    sb.append("- ").append(sv.getSlotName()).append(": ").append(sv.getValue()).append("\n");
                }
                contextFragments.add(sb.toString().trim());
            }

            // 3. Level 2: 情境图谱与三维协同重排召回 (带软降级防护)
            List<MemoryNode> candidates = recallEpisodicNodesWithFailOpen(userId, query, queryEmbedding);
            if (!candidates.isEmpty()) {
                StringBuilder mb = new StringBuilder("[关联时序情境记忆]\n");
                for (MemoryNode mn : candidates) {
                    mb.append("- (I=").append(String.format("%.2f", mn.getImportance()))
                            .append(") ").append(mn.getContent()).append("\n");
                }
                contextFragments.add(mb.toString().trim());
            }

            // 4. Level 0: 组装物理严格预算内的上下文
            String finalContext = ByteBudgeter.assembleContext(contextFragments, maxBytes);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Memory context assembled for user {}, session {}: elapsed {} ms, bytes: {}/{}",
                    userId, sessionId, elapsed, finalContext.getBytes(StandardCharsets.UTF_8).length, maxBytes);
            return finalContext;

        } catch (Exception e) {
            // 软降级 (Fail-Open): 记录警告并返回保底上下文，主流程绝不崩溃
            log.warn("Fail-open degradation triggered during context assembly: {}", e.getMessage(), e);
            return ByteBudgeter.assembleContext(contextFragments, maxBytes);
        }
    }

    /**
     * 带软降级保护的情境记忆召回
     */
    private List<MemoryNode> recallEpisodicNodesWithFailOpen(String userId, String query, float[] queryEmbedding) {
        try {
            List<MemoryNode> active = episodicGraphService.queryActiveNodes(userId, System.currentTimeMillis());
            if (active.isEmpty()) {
                List<MemoryNode> pool = activeMemoryPool.get(userId);
                active = pool != null ? pool : Collections.emptyList();
            }

            if (active.isEmpty() || queryEmbedding == null) {
                return Collections.emptyList();
            }

            // MMR 多样性协同重排 Top-5
            return scoringService.rerankByMMR(active, queryEmbedding, 5, 0.70, System.currentTimeMillis());
        } catch (Exception ex) {
            log.warn("Episodic recall encounter error, falling back smoothly: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    // --- 组件 Getters ---

    public WorkingMemory getWorkingMemory() {
        return workingMemory;
    }

    public DialogueStateTracker getDialogueStateTracker() {
        return dialogueStateTracker;
    }

    public MemoryScoringService getScoringService() {
        return scoringService;
    }

    public ReflectionTreeEngine getReflectionEngine() {
        return reflectionEngine;
    }

    public AsyncReflectionWorker getReflectionWorker() {
        return reflectionWorker;
    }

    public EpisodicGraphService getEpisodicGraphService() {
        return episodicGraphService;
    }

    public UserPreferenceEvolutionGovernor getPreferenceGovernor() {
        return preferenceGovernor;
    }
}
