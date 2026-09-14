package tech.qiantong.qknow.ai.audit.merkle;

import java.util.List;

/**
 * 对数级 Merkle InclusionProof 存在性证明载荷
 *
 * @param traceId 交互任务全局跟踪 ID
 * @param leafIndex 叶子节点在树中的序号索引
 * @param leafHash 待验证叶子节点的 SHA-256 哈希值
 * @param merkleRoot 目标 Merkle 根哈希
 * @param proofPath 对数级兄弟节点哈希路径及方位
 *
 * @author qknow
 */
public record MerkleProof(
        String traceId,
        int leafIndex,
        String leafHash,
        String merkleRoot,
        List<ProofElement> proofPath
) {
    /**
     * 单个路径节点元素
     * @param hash 兄弟节点哈希
     * @param isLeft 当前兄弟节点是否位于左侧
     */
    public record ProofElement(
            String hash,
            boolean isLeft
    ) {}
}
