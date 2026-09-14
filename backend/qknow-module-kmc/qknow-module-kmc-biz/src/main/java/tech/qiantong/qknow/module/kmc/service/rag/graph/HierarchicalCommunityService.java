package tech.qiantong.qknow.module.kmc.service.rag.graph;

import java.util.List;
import java.util.Map;

/**
 * 层次化社区发现与增量摘要服务接口 (GraphRAG 2.0)
 * 构建 L0 宏观全局领域社区与 L1 微观实体簇社区，支持基于拓扑版本哈希的增量摘要缓存与 Fail-Open 降级
 */
public interface HierarchicalCommunityService {

    enum CommunityLevel {
        L0_MACRO_GLOBAL,   // 顶层全局宏观社区 (3~8个)
        L1_MICRO_CLUSTER   // 局部实体紧密簇 (5~15实体/簇)
    }

    record CommunityInfo(
            long communityId,
            CommunityLevel level,
            String title,
            List<String> coreEntities,
            String summary,
            String topologyHash,
            long updatedAt
    ) {}

    /**
     * 构建或重构指定工作区的层次化社区拓扑树
     */
    List<CommunityInfo> buildHierarchicalCommunities(String workspaceId, List<String> entities, Map<String, List<String>> edges);

    /**
     * 增量更新受影响实体的局部微观社区 (脏位淘汰缓存)
     */
    void incrementalUpdateCommunities(String workspaceId, List<String> changedEntities);

    /**
     * 获取与查询语义最相关的社区摘要列表 (严格按相关度与配额截断)
     */
    List<CommunityInfo> retrieveRelevantCommunities(String workspaceId, String query, int topK);

    /**
     * 检查当前环境 Neo4j GDS 算法插件是否可用
     */
    boolean isGdsAvailable();
}
