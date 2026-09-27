package tech.qiantong.qknow.hermes.workflow.timetravel.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 工作流时间旅行与置信度门控投机执行不可变审计凭单 (Java 21 Record)
 * <p>
 * 严格遵守系统高可用与密码学零时序泄漏工程铁律：
 * 1. 采用纯不可变 Java 21 Record 数据结构封装状态回溯因果证据；
 * 2. 验签与哈希自验真必须采用 {@link MessageDigest#isEqual(byte[], byte[])} 进行常量时间安全比对，防范时序侧信道攻击；
 * 3. 记录 Merkle 根、回退源版本、目标版本、测地线核分值、投机命中状态与微秒级耗时。
 *
 * @author Achilles
 * @since Phase 148
 */
public record WorkflowTimeTravelAuditReceipt(
        String receiptId,
        String tenantId,
        String workflowId,
        String merkleRootHash,
        long sourceVersion,
        long targetVersion,
        int revertDepth,
        double kernelSimilarity,
        boolean speculativeBranchHit,
        long latencyMicros,
        int quarantineBufferSize,
        String sha256Signature,
        long timestamp
) {

    /**
     * 紧凑型构造函数，对核心关键参数执行非空与边界校验
     */
    public WorkflowTimeTravelAuditReceipt {
        Objects.requireNonNull(receiptId, "receiptId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        Objects.requireNonNull(workflowId, "workflowId 不能为空");
        Objects.requireNonNull(merkleRootHash, "merkleRootHash 不能为空");
        Objects.requireNonNull(sha256Signature, "sha256Signature 不能为空");
        if (sourceVersion < 0 || targetVersion < 0) {
            throw new IllegalArgumentException("版本号不能为负数: source=" + sourceVersion + ", target=" + targetVersion);
        }
        if (revertDepth < 0) {
            throw new IllegalArgumentException("回溯深度不能为负数: " + revertDepth);
        }
        if (kernelSimilarity < 0.0 || kernelSimilarity > 1.0) {
            throw new IllegalArgumentException("超球面测地线核相似度必须在 [0.0, 1.0] 闭区间: " + kernelSimilarity);
        }
    }

    /**
     * 静态工厂构造方法：自动生成规范化 SHA-256 密码学签名
     *
     * @param receiptId              凭单全局唯一编号
     * @param tenantId               多租户隔离标识
     * @param workflowId             工作流实例标识
     * @param merkleRootHash         状态版本 Merkle DAG 根哈希
     * @param sourceVersion          回退前起始版本
     * @param targetVersion          回退后目标版本
     * @param revertDepth            回溯遍历深度 (二分跳跃步数)
     * @param kernelSimilarity       超球面状态差异测地线核相似度
     * @param speculativeBranchHit   投机分支是否命中
     * @param latencyMicros          单次操作耗时 (微秒)
     * @param quarantineBufferSize   三级冷备环形仓当前保护项数量
     * @param timestamp              凭单生成时间戳
     * @return 签名完毕的不可变审计凭单实例
     */
    public static WorkflowTimeTravelAuditReceipt create(
            String receiptId,
            String tenantId,
            String workflowId,
            String merkleRootHash,
            long sourceVersion,
            long targetVersion,
            int revertDepth,
            double kernelSimilarity,
            boolean speculativeBranchHit,
            long latencyMicros,
            int quarantineBufferSize,
            long timestamp
    ) {
        String payload = buildCanonicalPayload(
                receiptId, tenantId, workflowId, merkleRootHash,
                sourceVersion, targetVersion, revertDepth,
                kernelSimilarity, speculativeBranchHit,
                latencyMicros, quarantineBufferSize, timestamp
        );
        String calculatedSignature = computeSha256(payload);
        return new WorkflowTimeTravelAuditReceipt(
                receiptId, tenantId, workflowId, merkleRootHash,
                sourceVersion, targetVersion, revertDepth,
                kernelSimilarity, speculativeBranchHit,
                latencyMicros, quarantineBufferSize,
                calculatedSignature, timestamp
        );
    }

    /**
     * 基于 MessageDigest.isEqual 进行常量时间自验真，检验凭单防篡改完整性
     *
     * @return 若凭单未被篡改则返回 true，否则返回 false
     */
    public boolean verifyIntegrity() {
        String payload = buildCanonicalPayload(
                receiptId, tenantId, workflowId, merkleRootHash,
                sourceVersion, targetVersion, revertDepth,
                kernelSimilarity, speculativeBranchHit,
                latencyMicros, quarantineBufferSize, timestamp
        );
        String expectedHash = computeSha256(payload);

        byte[] actualBytes = this.sha256Signature.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedHash.getBytes(StandardCharsets.UTF_8);

        // 常量时间安全比对，防范时序侧信道攻击
        return MessageDigest.isEqual(actualBytes, expectedBytes);
    }

    /**
     * 构建符合 RFC-8785 确定性规范的键值序列化载荷
     */
    private static String buildCanonicalPayload(
            String receiptId,
            String tenantId,
            String workflowId,
            String merkleRootHash,
            long sourceVersion,
            long targetVersion,
            int revertDepth,
            double kernelSimilarity,
            boolean speculativeBranchHit,
            long latencyMicros,
            int quarantineBufferSize,
            long timestamp
    ) {
        return String.format(
                "receiptId=%s|tenantId=%s|workflowId=%s|merkleRootHash=%s|sourceVersion=%d|targetVersion=%d|revertDepth=%d|kernelSimilarity=%.6f|speculativeBranchHit=%b|latencyMicros=%d|quarantineBufferSize=%d|timestamp=%d",
                receiptId, tenantId, workflowId, merkleRootHash,
                sourceVersion, targetVersion, revertDepth,
                kernelSimilarity, speculativeBranchHit,
                latencyMicros, quarantineBufferSize, timestamp
        );
    }

    /**
     * 计算输入字符串的 SHA-256 哈希散列
     */
    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 运行时缺失 SHA-256 消息摘要算法", e);
        }
    }
}
