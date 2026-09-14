package tech.qiantong.qknow.module.kmc.service.rag.graph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨实体多跳因果推理链抽取器实现
 * 硬编码节点出入度截断 (Degree Cutoff <= 30) 与 1~2 跳路径收敛，消除超级节点 OOM
 */
@Slf4j
@Service
public class MultiHopCausalPathExtractorImpl implements MultiHopCausalPathExtractor {

    // 存储模拟/缓存拓扑路径: workspaceId -> List<CausalPathFact>
    private final Map<String, List<CausalPathFact>> mockPaths = new ConcurrentHashMap<>();

    // 存储节点出入度信息: workspaceId -> (node -> degree)
    private final Map<String, Map<String, Integer>> nodeDegrees = new ConcurrentHashMap<>();

    // 存储节点邻接列表: workspaceId -> (node -> List<String>)
    private final Map<String, Map<String, List<String>>> nodeNeighbors = new ConcurrentHashMap<>();

    @Override
    public List<CausalPathFact> extractCausalChains(String workspaceId, List<String> seedEntities, int maxChains) {
        if (seedEntities == null || seedEntities.isEmpty()) {
            return List.of();
        }

        List<CausalPathFact> candidateFacts = new ArrayList<>();
        Map<String, Integer> degrees = nodeDegrees.getOrDefault(workspaceId, Collections.emptyMap());
        Map<String, List<String>> neighborsMap = nodeNeighbors.getOrDefault(workspaceId, Collections.emptyMap());
        List<CausalPathFact> registeredPaths = mockPaths.getOrDefault(workspaceId, Collections.emptyList());

        for (String seed : seedEntities) {
            int degree = degrees.getOrDefault(seed, 0);

            // 1. 超级节点出入度截断防护 (Degree Cutoff <= 30)
            if (degree > DEGREE_CUTOFF) {
                log.warn("检测到超级节点 [{}], 出入度 [{}] 超过阈值 [{}], 强制触发截断过滤",
                        seed, degree, DEGREE_CUTOFF);
                List<String> rawNeighbors = neighborsMap.getOrDefault(seed, Collections.emptyList());
                int truncatedSize = Math.min(rawNeighbors.size(), DEGREE_CUTOFF);
                for (int i = 0; i < truncatedSize; i++) {
                    candidateFacts.add(CausalPathFact.builder()
                            .sourceEntity(seed)
                            .firstRelation("关联合约")
                            .targetEntity(rawNeighbors.get(i))
                            .causalScore(0.85)
                            .build());
                }
                continue;
            }

            // 2. 正常检索 1~2 跳因果事实链
            for (CausalPathFact path : registeredPaths) {
                if (seed.equalsIgnoreCase(path.getSourceEntity())) {
                    // 检查中介节点度数，若中介节点为超级节点亦进行截断保护
                    if (path.getIntermediateEntity() != null) {
                        int midDegree = degrees.getOrDefault(path.getIntermediateEntity(), 0);
                        if (midDegree > DEGREE_CUTOFF) {
                            log.warn("中介节点 [{}] 度数 [{}] 超限，截断第二跳扩散", path.getIntermediateEntity(), midDegree);
                            candidateFacts.add(CausalPathFact.builder()
                                    .sourceEntity(path.getSourceEntity())
                                    .firstRelation(path.getFirstRelation())
                                    .targetEntity(path.getIntermediateEntity())
                                    .causalScore(path.getCausalScore() * 0.9)
                                    .build());
                            continue;
                        }
                    }
                    candidateFacts.add(path);
                }
            }
        }

        // 3. 按置信度排序截断返回
        return candidateFacts.stream()
                .sorted((a, b) -> Double.compare(b.getCausalScore(), a.getCausalScore()))
                .limit(Math.max(1, maxChains))
                .toList();
    }

    /**
     * 测试/预置辅助方法：注册测试高维超级节点
     */
    public void mockRegisterNodeWithHighDegree(String workspaceId, String supernode, List<String> neighbors) {
        nodeDegrees.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>())
                .put(supernode, neighbors.size());
        nodeNeighbors.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>())
                .put(supernode, new ArrayList<>(neighbors));
    }

    /**
     * 测试/预置辅助方法：注册结构化因果路径
     */
    public void mockRegisterPath(String workspaceId, String source, String r1, String mid, String r2, String target, double score) {
        CausalPathFact fact = CausalPathFact.builder()
                .sourceEntity(source)
                .firstRelation(r1)
                .intermediateEntity(mid)
                .secondRelation(r2)
                .targetEntity(target)
                .causalScore(score)
                .build();
        mockPaths.computeIfAbsent(workspaceId, k -> new ArrayList<>()).add(fact);
        nodeDegrees.computeIfAbsent(workspaceId, k -> new ConcurrentHashMap<>())
                .put(source, 2);
    }
}
