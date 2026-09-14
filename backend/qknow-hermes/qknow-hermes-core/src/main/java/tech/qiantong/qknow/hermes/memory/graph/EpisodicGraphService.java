package tech.qiantong.qknow.hermes.memory.graph;

import tech.qiantong.qknow.hermes.memory.model.EpisodicEdge;
import tech.qiantong.qknow.hermes.memory.model.MemoryNode;

import java.util.List;
import java.util.Set;

/**
 * 时序情境图谱服务 (Episodic Graph Service) 契约接口
 * 覆盖：
 * 1. 记忆节点与情境事件边存储
 * 2. 版本偏序覆盖算子 (Supersede Operator) 落地与时序无环保证 (Theorem 2.1)
 * 3. 任意时间快照 (Time-Travel Snapshot) 零冲突查询
 * 4. 2-Hop 激活扩散与度数截断 (防超级节点爆炸与 3000ms 超时防护)
 */
public interface EpisodicGraphService {

    /**
     * 写入或更新记忆节点
     */
    void addNode(MemoryNode node);

    /**
     * 写入情境图边
     */
    void addEdge(EpisodicEdge edge);

    /**
     * 执行版本偏序覆盖算子：新偏好覆盖旧偏好
     * 1. 截断旧节点/边的有效终止时间 validTo = timestamp
     * 2. 建立从 newMemoryId -> oldMemoryId 的 [:SUPERSEDES] 边
     * 3. 严格消除冲突并维持因果无环性 (Theorem 2.1)
     */
    void recordSupersede(String oldMemoryId, String newMemoryId, long timestamp);

    /**
     * 在指定时间快照点查询有效节点集合 (消除已废弃的旧版本)
     */
    List<MemoryNode> queryActiveNodes(String userId, long queryTime);

    /**
     * 校验从指定节点出发的 SUPERSEDES 依赖链是否存在环路 (严格有向无环)
     */
    boolean hasCycleInSupersedesChain();

    /**
     * 基于 Seed 节点执行 2-Hop 激活扩散检索，严格施加度数截断与超时熔断
     * @param seedNodeId 核心种子节点 ID
     * @param hop1Limit 第一跳度数上限 (<= 10)
     * @param hop2Limit 第二跳度数上限 (<= 5)
     * @param timeoutMs 超时时间 (<= 3000ms)
     */
    List<MemoryNode> traverse2HopWithDegreeCutoff(String seedNodeId, int hop1Limit, int hop2Limit, long timeoutMs);

    /**
     * 获取指定节点
     */
    MemoryNode getNode(String nodeId);

    /**
     * 获取指定节点的所有出边
     */
    List<EpisodicEdge> getOutgoingEdges(String nodeId);

    /**
     * 物理移除指定记忆节点 (用于有限视界 GC 淘汰)
     */
    void removeNode(String nodeId);
}
