package tech.qiantong.qknow.ai.audit.merkle;

/**
 * 密码学 Merkle 树审计存证项
 *
 * @param itemId 证据项唯一标识符（如切片 ID、Query ID、Response ID）
 * @param payloadHash 明文载荷的 SHA-256 哈希值
 * @param timestamp 存证生成时间戳（毫秒）
 *
 * @author qknow
 */
public record MerkleEvidenceItem(
        String itemId,
        String payloadHash,
        long timestamp
) {}
