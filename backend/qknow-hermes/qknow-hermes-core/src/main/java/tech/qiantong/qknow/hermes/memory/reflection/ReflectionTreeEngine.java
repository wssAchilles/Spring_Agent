package tech.qiantong.qknow.hermes.memory.reflection;

import tech.qiantong.qknow.hermes.memory.model.MemoryNode;
import tech.qiantong.qknow.hermes.memory.model.ReflectiveInsightVO;

import java.util.List;

/**
 * 反思折叠树引擎 (Reflection Tree Engine) 契约接口
 * 覆盖：
 * 1. 递归聚类低阶离散情境观测事件
 * 2. 提炼宏观高阶反思见解 (Theorem 1.1: 压缩率 >= 70%)
 * 3. 关联因果溯源证据链 (supportingEvidenceIds)
 */
public interface ReflectionTreeEngine {

    /**
     * 将细粒度记忆事件观测折叠抽象为高阶见解
     */
    List<ReflectiveInsightVO> foldReflections(String userId, List<MemoryNode> observations);

    /**
     * 计算折叠压缩率 (以 UTF-8 字节/字符量度量)
     * CompressionRatio = 1.0 - (InsightSize / RawObservationSize)
     */
    double computeCompressionRatio(List<MemoryNode> observations, List<ReflectiveInsightVO> insights);
}
