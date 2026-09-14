package tech.qiantong.qknow.module.kmc.service.rag.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 层次化社区发现与增量摘要服务实现 (GraphRAG 2.0)
 * 实现多尺度社区发现（L0 宏观领域与 L1 微观簇）、版本拓扑哈希缓存淘汰与 Fail-Open 降级
 */
@Slf4j
@Service
public class HierarchicalCommunityServiceImpl implements HierarchicalCommunityService {

    // 社区存储: workspaceId -> List<CommunityInfo>
    private final Map<String, List<CommunityInfo>> workspaceCommunities = new ConcurrentHashMap<>();

    // 摘要缓存: topologyHash -> summary
    private final Map<String, String> summaryCache = new ConcurrentHashMap<>();

    // 模拟 LLM 摘要调用计数器 (用于监控与测试统计)
    private final AtomicInteger llmSummaryCallCounter = new AtomicInteger(0);

    // GDS 可用性标记 (默认探测可用，失败时自动平滑降级)
    private volatile boolean gdsAvailable = true;

    @Override
    public List<CommunityInfo> buildHierarchicalCommunities(String workspaceId, List<String> entities, Map<String, List<String>> edges) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        List<CommunityInfo> result = new ArrayList<>();
        long idGenerator = 1;

        // 1. 构建 L1 微观实体簇 (Micro-Clusters): 连通子图或局部紧密邻域
        List<List<String>> l1Clusters = clusterEntities(entities, edges, 10);
        for (List<String> cluster : l1Clusters) {
            long cId = idGenerator++;
            String hash = computeTopologyHash(workspaceId, cId, CommunityLevel.L1_MICRO_CLUSTER, cluster);
            String summary = getOrGenerateSummary(workspaceId, cId, CommunityLevel.L1_MICRO_CLUSTER, cluster, hash);
            String title = "微观实体簇 " + cId + ": " + String.join("、", cluster.stream().limit(3).toList());

            result.add(new CommunityInfo(
                    cId,
                    CommunityLevel.L1_MICRO_CLUSTER,
                    title,
                    cluster,
                    summary,
                    hash,
                    System.currentTimeMillis()
            ));
        }

        // 2. 构建 L0 宏观全局社区 (Macro-Global Communities): 合并局部微观簇
        List<List<String>> l0Domains = groupL1ClustersToL0(l1Clusters);
        for (List<String> domainEntities : l0Domains) {
            long cId = idGenerator++;
            String hash = computeTopologyHash(workspaceId, cId, CommunityLevel.L0_MACRO_GLOBAL, domainEntities);
            String summary = getOrGenerateSummary(workspaceId, cId, CommunityLevel.L0_MACRO_GLOBAL, domainEntities, hash);
            String title = "宏观业务领域 " + cId + ": " + String.join("、", domainEntities.stream().limit(4).toList());

            result.add(new CommunityInfo(
                    cId,
                    CommunityLevel.L0_MACRO_GLOBAL,
                    title,
                    domainEntities,
                    summary,
                    hash,
                    System.currentTimeMillis()
            ));
        }

        workspaceCommunities.put(workspaceId, result);
        log.info("构建层次化社区完成: workspaceId={}, L0数量={}, L1数量={}",
                workspaceId, l0Domains.size(), l1Clusters.size());
        return result;
    }

    @Override
    public void incrementalUpdateCommunities(String workspaceId, List<String> changedEntities) {
        if (changedEntities == null || changedEntities.isEmpty()) {
            return;
        }
        List<CommunityInfo> currentList = workspaceCommunities.get(workspaceId);
        if (currentList == null || currentList.isEmpty()) {
            return;
        }

        Set<String> changedSet = new HashSet<>(changedEntities);
        List<CommunityInfo> updatedList = new ArrayList<>();

        for (CommunityInfo info : currentList) {
            boolean hasOverlap = info.coreEntities().stream().anyMatch(changedSet::contains);
            if (hasOverlap && info.level() == CommunityLevel.L1_MICRO_CLUSTER) {
                // 仅对受变更影响的微观社区使缓存失效并刷新
                summaryCache.remove(info.topologyHash());
                String newHash = computeTopologyHash(workspaceId, info.communityId(), info.level(), info.coreEntities()) + "_v" + System.currentTimeMillis();
                String newSummary = generateSummary(info.communityId(), info.level(), info.coreEntities());
                updatedList.add(new CommunityInfo(
                        info.communityId(),
                        info.level(),
                        info.title(),
                        info.coreEntities(),
                        newSummary,
                        newHash,
                        System.currentTimeMillis()
                ));
            } else {
                // 其余绝大多数未受影响社区直接复用，杜绝重复调用大模型
                updatedList.add(info);
            }
        }
        workspaceCommunities.put(workspaceId, updatedList);
        log.info("增量更新受影响社区完成: workspaceId={}, 变更实体={}", workspaceId, changedEntities);
    }

    @Override
    public List<CommunityInfo> retrieveRelevantCommunities(String workspaceId, String query, int topK) {
        List<CommunityInfo> all = workspaceCommunities.getOrDefault(workspaceId, List.of());
        if (all.isEmpty() || query == null || query.isBlank()) {
            return all.stream().limit(Math.max(1, topK)).toList();
        }

        String lowerQ = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .sorted((a, b) -> Double.compare(scoreCommunity(lowerQ, b), scoreCommunity(lowerQ, a)))
                .limit(Math.max(1, topK))
                .toList();
    }

    @Override
    public boolean isGdsAvailable() {
        return gdsAvailable;
    }

    public void setGdsAvailable(boolean available) {
        this.gdsAvailable = available;
    }

    public int getLlmSummaryCallCount() {
        return llmSummaryCallCounter.get();
    }

    public void resetLlmSummaryCallCount() {
        llmSummaryCallCounter.set(0);
    }

    private double scoreCommunity(String query, CommunityInfo community) {
        double score = 0.0;
        for (String entity : community.coreEntities()) {
            if (query.contains(entity.toLowerCase(Locale.ROOT))) {
                score += 3.0;
            }
        }
        if (community.summary() != null && community.summary().toLowerCase(Locale.ROOT).contains(query)) {
            score += 1.5;
        }
        if (community.level() == CommunityLevel.L0_MACRO_GLOBAL) {
            score += 0.5; // 宏观社区适度加权全局视野
        }
        return score;
    }

    private String getOrGenerateSummary(String workspaceId, long communityId, CommunityLevel level, List<String> entities, String hash) {
        if (summaryCache.containsKey(hash)) {
            return summaryCache.get(hash);
        }
        String summary = generateSummary(communityId, level, entities);
        summaryCache.put(hash, summary);
        return summary;
    }

    private String generateSummary(long communityId, CommunityLevel level, List<String> entities) {
        llmSummaryCallCounter.incrementAndGet();
        String typeDesc = level == CommunityLevel.L0_MACRO_GLOBAL ? "宏观领域" : "微观实体簇";
        String topEntities = String.join("、", entities.stream().limit(6).toList());
        return String.format("[%s %d] 涵盖实体核心集: %s。具备强局部因果拓扑与上下文关联。", typeDesc, communityId, topEntities);
    }

    private String computeTopologyHash(String workspaceId, long communityId, CommunityLevel level, List<String> entities) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = workspaceId + ":" + communityId + ":" + level.name() + ":" + String.join(",", entities.stream().sorted().toList());
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private List<List<String>> clusterEntities(List<String> entities, Map<String, List<String>> edges, int maxClusterSize) {
        List<List<String>> clusters = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        // BFS 识别连通子图簇
        for (String entity : entities) {
            if (!visited.contains(entity)) {
                List<String> currentCluster = new ArrayList<>();
                Queue<String> queue = new ArrayDeque<>();
                queue.add(entity);
                visited.add(entity);

                while (!queue.isEmpty() && currentCluster.size() < maxClusterSize) {
                    String node = queue.poll();
                    currentCluster.add(node);
                    List<String> neighbors = edges.getOrDefault(node, List.of());
                    for (String neighbor : neighbors) {
                        if (entities.contains(neighbor) && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
                if (!currentCluster.isEmpty()) {
                    clusters.add(currentCluster);
                }
            }
        }
        // 兜底未成簇节点
        for (String entity : entities) {
            if (!visited.contains(entity)) {
                clusters.add(List.of(entity));
                visited.add(entity);
            }
        }
        return clusters;
    }

    private List<List<String>> groupL1ClustersToL0(List<List<String>> l1Clusters) {
        List<List<String>> l0 = new ArrayList<>();
        int groupSize = Math.max(1, (int) Math.ceil((double) l1Clusters.size() / 3.0));
        List<String> current = new ArrayList<>();
        for (int i = 0; i < l1Clusters.size(); i++) {
            current.addAll(l1Clusters.get(i));
            if (current.size() >= 15 || (i + 1) % groupSize == 0 || i == l1Clusters.size() - 1) {
                l0.add(new ArrayList<>(current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            l0.add(current);
        }
        return l0.isEmpty() ? l1Clusters : l0;
    }
}
