package tech.qiantong.qknow.ai.privacy.unlearning;

import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;

import java.io.Serializable;
import java.util.List;

/**
 * 机器遗忘与被遗忘权合规注销凭单值对象 (UnlearningReceiptVO)
 *
 * 符合 GDPR Article 17 与 RFC 6962 密码学存证标准，包含域分离墓碑哈希、Merkle 包含性证明与数字指纹。
 *
 * @author qknow
 */
public record UnlearningReceiptVO(
        String receiptId,               // 凭单唯一全球标识
        String tenantId,                // 租户唯一标识
        Long documentId,                // 被注销知识文档 ID
        List<Long> revokedSegmentIds,   // 被销毁的全部切片 ID 列表
        String tombstoneHash,           // RFC 6962 域分离 (0x02) 墓碑哈希
        long requestedTimestamp,        // 用户发起注销时间戳
        long purgedTimestamp,           // 物理擦除完成时间戳
        String merkleRootHash,          // 挂载的 RFC 6962 Merkle 树根哈希
        List<MerkleProof.ProofElement> merkleProofPath, // 包含性证明路径
        String status,                  // 凭单状态: PENDING / COMPLETED / FAILED
        String reasonCode               // 注销原因代码 (如 USER_RIGHT_TO_BE_FORGOTTEN)
) implements Serializable {
}
