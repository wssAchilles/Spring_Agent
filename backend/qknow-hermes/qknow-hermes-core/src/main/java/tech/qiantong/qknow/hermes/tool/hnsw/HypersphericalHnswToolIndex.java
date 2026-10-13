package tech.qiantong.qknow.hermes.tool.hnsw;

import java.io.Serializable;
import java.util.List;

/**
 * 超球面 HNSW 增量图与量化索引引擎接口
 * 面向海量 MCP 工具提供 O(log N) 亚毫秒级意图向量投影检索
 */
public interface HypersphericalHnswToolIndex {

    record ScoredTool(
            String toolCode,
            float cosineSimilarity,
            float angularDistance
    ) implements Serializable {}

    /**
     * 增量注册工具向量节点
     *
     * @param toolCode        工具编码
     * @param unitVector      1536 维超球面单位向量
     * @param useQuantization 是否启用 SQ8 标量量化压缩
     */
    void insert(String toolCode, float[] unitVector, boolean useQuantization);

    /**
     * 亚毫秒级 Top-K 意图投影近邻检索
     *
     * @param queryUnitVector 1536 维查询单位向量
     * @param topK            返回候选数量
     * @param efSearch        搜索动态候选束宽
     * @return 排序后的候选工具列表
     */
    List<ScoredTool> searchKnn(float[] queryUnitVector, int topK, int efSearch);

    /**
     * 软删除工具节点
     */
    boolean remove(String toolCode);

    /**
     * 获取当前有效索引工具总数
     */
    int size();
}
