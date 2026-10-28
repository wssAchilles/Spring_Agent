package tech.qiantong.qknow.ai.service;

/**
 * 向量生效物理维度提供者 (支持从底层存储自适应探测)
 *
 * @author qknow
 */
public interface IEmbeddingDimensionProvider {

    /**
     * 获取当前生效的物理向量维度 (若未探测到或无约束返回 0)
     *
     * @return 物理维度数值
     */
    int getEffectiveDimension();
}
