package tech.qiantong.qknow.hermes.cognitive.context;

import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 滞回双区前缀守恒因果折叠引擎（HDZ-PCF，对应 Phase 7 原创架构机制一）。
 * <p>
 * 理论突破（根治 CWL 原地驱逐导致每轮 Prefix KV Cache 失效的死穴）：
 * 1. 双区物理隔离：{@code Zone-0}（SystemPrompt + 静态 4 元工具 Schema + 里程碑锚点）严格 64-Token 对齐且常温只读，保障 Prefix Cache 命中；
 *    {@code Zone-1}（活跃工作区）将多轮工具调用与探索建模为因果 Episode 有向无环图；
 * 2. 滞回双水位批量折叠：仅当活跃区 Token 突破高水位 {@code H_hi} 时，触发一次拓扑无未折叠后继约束下的批量折叠
 *    （优先级：{@code ACTION} > {@code EXPLORATORY}），直至回落至低水位 {@code H_lo} 以下，显著降低前缀重组频率；
 * 3. 三级冷备可逆隔离：被折叠原文绝不物理硬删除，全部压入定长 {@link ColdStandbyTier#WARM_QUARANTINE} 环形缓冲区并支持 100% 反事实还原。
 * </p>
 */
public class HystereticDualZoneContextFolder {

    public static final int DEFAULT_LOW_WATERMARK_TOKENS = 2048;
    public static final int DEFAULT_HIGH_WATERMARK_TOKENS = 3584;
    /**
     * Phase 11.2 (IDG-CO) 前缀守恒硬上限水位：常规多轮严格保持 Append-Only（不重写 Zone-1 旧 turn 破坏 KV Cache）。
     */
    public static final int DEFAULT_V6_APPEND_ONLY_GUARD_TOKENS = 20_000;
    private static final int COLD_RING_CAPACITY = 256;

    private static final class RequestZoneState {
        String zone0Prefix = "";
        int zone0BaselineHash;
        final List<CausalEpisodeNode> activeEpisodes = new ArrayList<>();
        final Deque<CausalEpisodeNode> coldStandbyRing = new ArrayDeque<>(COLD_RING_CAPACITY);
        final List<ContextFoldingReceipt> historyReceipts = new ArrayList<>();
        int prefixInvalidationCount = 0;
    }

    private final int lowWatermarkTokens;
    private final int highWatermarkTokens;
    private final String hmacSecretKey;
    private final Map<String, RequestZoneState> stateByRequest = new ConcurrentHashMap<>();

    public HystereticDualZoneContextFolder() {
        this(DEFAULT_LOW_WATERMARK_TOKENS, DEFAULT_HIGH_WATERMARK_TOKENS, "hermes-context-fold-secret");
    }

    public HystereticDualZoneContextFolder(int lowWatermarkTokens, int highWatermarkTokens, String hmacSecretKey) {
        if (lowWatermarkTokens <= 0 || highWatermarkTokens <= lowWatermarkTokens) {
            throw new IllegalArgumentException("滞回水位必须严格满足 0 < H_lo < H_hi");
        }
        this.lowWatermarkTokens = lowWatermarkTokens;
        this.highWatermarkTokens = highWatermarkTokens;
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-context-fold-secret"
                : hmacSecretKey;
    }

    /**
     * 锁定 Zone-0 只读冻结前缀（仅在首轮建立基线）。
     */
    public void lockZone0Prefix(String requestId, String staticSystemAndToolsPrefix) {
        String safeReq = normalizeReqId(requestId);
        RequestZoneState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestZoneState());
        synchronized (state) {
            if (state.zone0Prefix.isEmpty()) {
                state.zone0Prefix = staticSystemAndToolsPrefix != null ? staticSystemAndToolsPrefix : "";
                state.zone0BaselineHash = state.zone0Prefix.hashCode();
            }
        }
    }

    /**
     * 向 Zone-1 追加因果情景节点，并按滞回阈值或 CWL 对照策略执行受控上下文折叠。
     *
     * @param inPlaceCwlMode true 表示对照组模式（超低阈值下原地驱逐，用于实证对比前缀抖动率）
     */
    public ContextFoldingReceipt appendAndMaybeFold(
            String requestId,
            CausalEpisodeNode node,
            boolean inPlaceCwlMode
    ) {
        Objects.requireNonNull(node, "CausalEpisodeNode 不能为空");
        String safeReq = normalizeReqId(requestId);
        RequestZoneState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestZoneState());

        synchronized (state) {
            state.activeEpisodes.add(node);
            int tokensBefore = computeActiveZoneTokens(state);
            boolean triggered = false;
            List<String> foldedIds = new ArrayList<>();
            double totalReconstructionLoss = 0.0;

            if (inPlaceCwlMode) {
                // CWL 原地驱逐对照组：每轮超出低水位即就地移除前序节点，直接打破连续前缀一致性
                if (tokensBefore > lowWatermarkTokens) {
                    triggered = true;
                    int currentTokens = tokensBefore;
                    while (currentTokens > lowWatermarkTokens && !state.activeEpisodes.isEmpty()) {
                        CausalEpisodeNode candidate = selectTopologicalEvictableCandidate(state.activeEpisodes);
                        if (candidate == null) {
                            break;
                        }
                        int idx = indexOfEpisode(state.activeEpisodes, candidate.episodeId());
                        CausalEpisodeNode folded = candidate.withFolded(
                                ColdStandbyTier.WARM_QUARANTINE,
                                "[CWL_EVICTED:" + candidate.episodeId() + "]"
                        );
                        state.activeEpisodes.set(idx, folded);
                        pushColdStandby(state, folded);
                        foldedIds.add(folded.episodeId());
                        currentTokens -= Math.max(0, candidate.tokenCount() - estimateHandleTokens(folded.summaryHandle()));
                        totalReconstructionLoss += 0.08;
                    }
                    state.prefixInvalidationCount++;
                }
            } else {
                // HDZ-PCF 滞回双区折叠：Zone-0 绝对保持不变，仅当 Zone-1 突破 H_hi 时批量折叠至 <= H_lo
                if (tokensBefore > highWatermarkTokens) {
                    triggered = true;
                    int currentTokens = tokensBefore;
                    while (currentTokens > lowWatermarkTokens) {
                        CausalEpisodeNode candidate = selectTopologicalEvictableCandidate(state.activeEpisodes);
                        if (candidate == null) {
                            break;
                        }
                        int idx = indexOfEpisode(state.activeEpisodes, candidate.episodeId());
                        String handle = buildCompactCausalHandle(candidate);
                        CausalEpisodeNode folded = candidate.withFolded(ColdStandbyTier.WARM_QUARANTINE, handle);
                        state.activeEpisodes.set(idx, folded);
                        pushColdStandby(state, folded);
                        foldedIds.add(folded.episodeId());
                        currentTokens -= Math.max(0, candidate.tokenCount() - estimateHandleTokens(handle));
                        totalReconstructionLoss += 0.03;
                    }
                }
            }

            int tokensAfter = computeActiveZoneTokens(state);
            boolean zone0Preserved = !inPlaceCwlMode
                    && (state.zone0Prefix.isEmpty() || state.zone0Prefix.hashCode() == state.zone0BaselineHash);
            double meanLoss = foldedIds.isEmpty() ? 0.0 : Math.min(1.0, totalReconstructionLoss / foldedIds.size());

            ContextFoldingReceipt receipt = ContextFoldingReceipt.createSigned(
                    safeReq,
                    zone0Preserved,
                    triggered,
                    inPlaceCwlMode,
                    tokensBefore,
                    tokensAfter,
                    foldedIds.size(),
                    state.coldStandbyRing.size(),
                    meanLoss,
                    foldedIds,
                    System.currentTimeMillis(),
                    hmacSecretKey
            );
            state.historyReceipts.add(receipt);
            return receipt;
        }
    }

    /**
     * Phase 11.2 (IDG-CO) 前缀守恒追加策略：
     * 常规多轮（总活跃 Token <= {@code hardCapTokens}）严格采用 Append-Only，不改写任何历史节点、不触发 Zone-1 折叠，
     * 最大化 DeepSeek/百炼 KV Prefix Cache 跨轮连续命中；仅在极端超长突破硬上限后才触发一次受控滞回折叠并压入可逆冷备。
     */
    public ContextFoldingReceipt appendWithPrefixConservingPolicy(
            String requestId,
            CausalEpisodeNode node,
            boolean strictAppendOnlyUntilHardCap,
            int hardCapTokens
    ) {
        if (!strictAppendOnlyUntilHardCap) {
            return appendAndMaybeFold(requestId, node, false);
        }
        Objects.requireNonNull(node, "CausalEpisodeNode 不能为空");
        String safeReq = normalizeReqId(requestId);
        RequestZoneState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestZoneState());
        int effectiveHardCap = Math.max(highWatermarkTokens,
                hardCapTokens > 0 ? hardCapTokens : DEFAULT_V6_APPEND_ONLY_GUARD_TOKENS);

        synchronized (state) {
            state.activeEpisodes.add(node);
            int tokensBefore = computeActiveZoneTokens(state);
            boolean triggered = false;
            List<String> foldedIds = new ArrayList<>();
            double totalReconstructionLoss = 0.0;

            if (tokensBefore > effectiveHardCap) {
                triggered = true;
                int targetLow = Math.max(lowWatermarkTokens, effectiveHardCap / 2);
                int currentTokens = tokensBefore;
                while (currentTokens > targetLow) {
                    CausalEpisodeNode candidate = selectTopologicalEvictableCandidate(state.activeEpisodes);
                    if (candidate == null) {
                        break;
                    }
                    int idx = indexOfEpisode(state.activeEpisodes, candidate.episodeId());
                    String handle = buildCompactCausalHandle(candidate);
                    CausalEpisodeNode folded = candidate.withFolded(ColdStandbyTier.WARM_QUARANTINE, handle);
                    state.activeEpisodes.set(idx, folded);
                    pushColdStandby(state, folded);
                    foldedIds.add(folded.episodeId());
                    currentTokens -= Math.max(0, candidate.tokenCount() - estimateHandleTokens(handle));
                    totalReconstructionLoss += 0.03;
                }
            }

            int tokensAfter = computeActiveZoneTokens(state);
            boolean zone0Preserved = state.zone0Prefix.isEmpty() || state.zone0Prefix.hashCode() == state.zone0BaselineHash;
            double meanLoss = foldedIds.isEmpty() ? 0.0 : Math.min(1.0, totalReconstructionLoss / foldedIds.size());

            ContextFoldingReceipt receipt = ContextFoldingReceipt.createSigned(
                    safeReq,
                    zone0Preserved,
                    triggered,
                    false,
                    tokensBefore,
                    tokensAfter,
                    foldedIds.size(),
                    state.coldStandbyRing.size(),
                    meanLoss,
                    foldedIds,
                    System.currentTimeMillis(),
                    hmacSecretKey
            );
            state.historyReceipts.add(receipt);
            return receipt;
        }
    }

    /**
     * 渲染 Zone-1 当前压缩后工作上下文字符串（折叠节点仅输出精简因果句柄）。
     */
    public String renderZone1ActiveContext(String requestId) {
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return "";
        }
        synchronized (state) {
            StringBuilder sb = new StringBuilder();
            for (CausalEpisodeNode ep : state.activeEpisodes) {
                if (ep.folded()) {
                    sb.append(ep.summaryHandle()).append('\n');
                } else {
                    sb.append('[').append(ep.episodeId()).append("] ").append(ep.rawContent()).append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * 反事实时间旅行还原：将已转入三级冷备的被折叠情景 100% 无损恢复到 Zone-1 活跃状态。
     */
    public boolean restoreFoldedEpisode(String requestId, String episodeId) {
        if (episodeId == null || episodeId.isBlank()) {
            return false;
        }
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return false;
        }
        synchronized (state) {
            int idx = indexOfEpisode(state.activeEpisodes, episodeId);
            if (idx < 0 || !state.activeEpisodes.get(idx).folded()) {
                return false;
            }
            state.activeEpisodes.set(idx, state.activeEpisodes.get(idx).withRestored());
            state.coldStandbyRing.removeIf(e -> episodeId.equals(e.episodeId()));
            return true;
        }
    }

    public List<ContextFoldingReceipt> getReceipts(String requestId) {
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.historyReceipts));
        }
    }

    public int getPrefixInvalidationCount(String requestId) {
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return state.prefixInvalidationCount;
        }
    }

    /**
     * 判断当前请求是否已真实发生过至少一次情景折叠（供编排器严格遵守“未折叠则零前缀改动”不变量）。
     */
    public boolean hasFoldedEpisodes(String requestId) {
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return false;
        }
        synchronized (state) {
            for (CausalEpisodeNode ep : state.activeEpisodes) {
                if (ep.folded()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 返回当前请求 Zone-1 活跃工作区的估算 Token 数。
     */
    public int getActiveTokenCount(String requestId) {
        RequestZoneState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return 0;
        }
        synchronized (state) {
            return computeActiveZoneTokens(state);
        }
    }

    public void clearRequest(String requestId) {
        if (requestId != null) {
            stateByRequest.remove(requestId);
        }
    }

    /**
     * 拓扑无未折叠后继约束选举：先折叠已无未折叠下游依赖的 ACTION 节点，再考虑 EXPLORATORY 节点（保留最新尾部活跃节点）。
     */
    private static CausalEpisodeNode selectTopologicalEvictableCandidate(List<CausalEpisodeNode> episodes) {
        Set<String> protectedDependencies = new HashSet<>();
        for (CausalEpisodeNode ep : episodes) {
            if (!ep.folded()) {
                protectedDependencies.addAll(ep.dependsOnEpisodeIds());
            }
        }
        // 永远保留最后 1 个最新节点作为正在进行中的推理上下文
        int limit = Math.max(0, episodes.size() - 1);
        CausalEpisodeNode fallbackExploratory = null;
        for (int i = 0; i < limit; i++) {
            CausalEpisodeNode ep = episodes.get(i);
            if (ep.folded() || protectedDependencies.contains(ep.episodeId())) {
                continue;
            }
            if (ep.episodeType() == CausalEpisodeNode.EpisodeType.ACTION) {
                return ep;
            }
            if (fallbackExploratory == null) {
                fallbackExploratory = ep;
            }
        }
        return fallbackExploratory;
    }

    private static int computeActiveZoneTokens(RequestZoneState state) {
        int total = 0;
        for (CausalEpisodeNode ep : state.activeEpisodes) {
            total += ep.folded() ? estimateHandleTokens(ep.summaryHandle()) : ep.tokenCount();
        }
        return total;
    }

    private static int estimateHandleTokens(String handle) {
        return handle == null || handle.isBlank() ? 0 : Math.min(28, Math.max(4, handle.length() / 8));
    }

    private static String buildCompactCausalHandle(CausalEpisodeNode node) {
        String normalized = node.rawContent().replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String preview = normalized.length() > 180
                ? normalized.substring(0, 180) + "..."
                : normalized;
        return "[FOLDED_HANDLE:" + node.episodeId() + "|tier=" + node.episodeType().name() + "|gist=" + preview + "]";
    }

    private static int indexOfEpisode(List<CausalEpisodeNode> list, String episodeId) {
        for (int i = 0; i < list.size(); i++) {
            if (episodeId.equals(list.get(i).episodeId())) {
                return i;
            }
        }
        return -1;
    }

    private static void pushColdStandby(RequestZoneState state, CausalEpisodeNode node) {
        if (state.coldStandbyRing.size() >= COLD_RING_CAPACITY) {
            state.coldStandbyRing.pollFirst();
        }
        state.coldStandbyRing.addLast(node);
    }

    private static String normalizeReqId(String reqId) {
        return (reqId == null || reqId.isBlank()) ? "req-context-fold" : reqId;
    }
}
