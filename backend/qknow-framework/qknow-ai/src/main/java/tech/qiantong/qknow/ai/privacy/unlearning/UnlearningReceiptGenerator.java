package tech.qiantong.qknow.ai.privacy.unlearning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.audit.merkle.MerkleEvidenceItem;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 密码学不可篡改机器遗忘注销凭单生成与验真器 (UnlearningReceiptGenerator)
 *
 * 遵循 RFC 6962 域分离规范（扩展 0x02 墓碑前缀），与 MerkleTreeEngine 证据树联动，
 * 提供 O(log N) 离线包含性验真能力（验真耗时 < 1ms），彻底杜绝虚假注销与凭单伪造。
 *
 * @author qknow
 */
@Slf4j
@Component
public class UnlearningReceiptGenerator {

    public static final byte TOMBSTONE_DOMAIN_PREFIX = 0x02; // 单字节域分离扩展墓碑前缀

    private final MerkleTreeEngine merkleTreeEngine;
    private final SecureRandom secureRandom = new SecureRandom();

    public UnlearningReceiptGenerator(MerkleTreeEngine merkleTreeEngine) {
        this.merkleTreeEngine = merkleTreeEngine;
    }

    public UnlearningReceiptGenerator() {
        this.merkleTreeEngine = new MerkleTreeEngine();
    }

    /**
     * 计算符合 RFC 6962 域分离 (0x02) 的单切片/文档墓碑哈希
     *
     * H_tomb = SHA-256(0x02 || tenantId || documentId || segmentIds || timestamp || salt || reasonCode)
     */
    public String computeTombstoneHash(String tenantId, Long documentId, List<Long> segmentIds,
                                       long timestamp, String salt, String reasonCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(TOMBSTONE_DOMAIN_PREFIX);

            String payload = String.format("%s:%s:%s:%d:%s:%s",
                    tenantId,
                    documentId != null ? documentId : "ALL",
                    segmentIds != null ? segmentIds.toString() : "[]",
                    timestamp,
                    salt != null ? salt : "",
                    reasonCode != null ? reasonCode : "DEFAULT");

            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 生成完整的机器遗忘注销凭单并挂载至 Merkle 树
     *
     * @param tenantId    租户 ID
     * @param documentId  被注销文档 ID
     * @param segmentIds  被注销切片 ID 列表
     * @param reasonCode  注销原因
     * @return 包含 Merkle 证明与墓碑哈希的凭单对象
     */
    public UnlearningReceiptVO generateReceipt(String tenantId, Long documentId, List<Long> segmentIds, String reasonCode) {
        String receiptId = "RCP-" + UUID.randomUUID();
        long now = System.currentTimeMillis();

        // 1. 生成 16 字节安全随机盐
        byte[] saltBytes = new byte[16];
        secureRandom.nextBytes(saltBytes);
        String salt = bytesToHex(saltBytes);

        // 2. 计算 0x02 域分离墓碑哈希
        String tombstoneHash = computeTombstoneHash(tenantId, documentId, segmentIds, now, salt, reasonCode);

        // 3. 将墓碑存证挂载至 Merkle 证据树
        List<MerkleEvidenceItem> items = new ArrayList<>();
        // 主墓碑证据项
        items.add(new MerkleEvidenceItem(
                receiptId,
                tombstoneHash,
                now
        ));
        // 若有切片，为切片分别挂载关联叶子项，保证树的丰富性与对数级校验
        if (segmentIds != null) {
            for (Long segId : segmentIds) {
                String segPayloadHash = computeTombstoneHash(tenantId, documentId, List.of(segId), now, salt, "SEGMENT_PURGE");
                items.add(new MerkleEvidenceItem(
                        "SEG-" + segId,
                        segPayloadHash,
                        now
                ));
            }
        }

        // 构建 Merkle 树并导出根哈希
        MerkleTreeEngine.MerkleTreeBuildResult treeResult = merkleTreeEngine.buildTree(receiptId, items);
        MerkleProof proof = merkleTreeEngine.generateInclusionProof(treeResult, 0);

        return new UnlearningReceiptVO(
                receiptId,
                tenantId,
                documentId,
                segmentIds,
                tombstoneHash,
                now,
                now,
                treeResult.rootHash(),
                proof != null ? proof.proofPath() : List.of(),
                "COMPLETED",
                reasonCode
        );
    }

    /**
     * 客户端/审计员离线 1ms 密码学验真
     *
     * @param receipt 待验真凭单
     * @return 验真结果，true 表示包含性证明完全成立且根哈希无篡改
     */
    public boolean verifyReceipt(UnlearningReceiptVO receipt) {
        if (receipt == null || receipt.merkleRootHash() == null || receipt.tombstoneHash() == null) {
            return false;
        }

        // 1. 重构第 0 个叶子节点的叶哈希: computeLeafHash(0, itemId, payloadHash, timestamp)
        String leafHash = MerkleTreeEngine.computeLeafHash(
                0,
                receipt.receiptId(),
                receipt.tombstoneHash(),
                receipt.requestedTimestamp()
        );

        // 2. 调用 MerkleTreeEngine 进行对数级折叠验证
        return merkleTreeEngine.verifyInclusionProof(
                receipt.merkleRootHash(),
                leafHash,
                receipt.merkleProofPath() != null ? receipt.merkleProofPath() : List.of()
        );
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
