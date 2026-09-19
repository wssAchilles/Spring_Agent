package tech.qiantong.qknow.hermes.flow.hitl.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 密码学工作流 Studio 调试与双向同步不可变存证凭单
 * 遵循 Java 21 Record 规范与 SHA-256 自签名防篡改验证
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowStudioReceipt(
        String receiptId,
        String workflowId,
        int epochVersion,
        String dslSha256,
        String topologyHash,
        String debugBatchId,
        int stepCount,
        String sha256Signature,
        long timestamp
) {
    /**
     * 工厂方法：生成并签署凭单
     *
     * @param workflowId 工作流 ID
     * @param epochVersion 纪元版本号
     * @param dslSha256 DSL 文本 SHA-256
     * @param topologyHash 拓扑结构哈希
     * @param debugBatchId 调试执行批次 ID
     * @param stepCount 执行步数
     * @return 签署完成的不可变凭单
     */
    public static WorkflowStudioReceipt create(
            String workflowId,
            int epochVersion,
            String dslSha256,
            String topologyHash,
            String debugBatchId,
            int stepCount
    ) {
        String receiptId = "STU_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        String signature = computeSignature(workflowId, epochVersion, dslSha256, topologyHash, debugBatchId, stepCount, now);
        return new WorkflowStudioReceipt(
                receiptId,
                workflowId,
                epochVersion,
                dslSha256,
                topologyHash,
                debugBatchId,
                stepCount,
                signature,
                now
        );
    }

    /**
     * 校验凭单的自签名完整性
     *
     * @return true 表示签名吻合且未被篡改
     */
    public boolean verifySignature() {
        String expected = computeSignature(workflowId, epochVersion, dslSha256, topologyHash, debugBatchId, stepCount, timestamp);
        return expected.equals(sha256Signature);
    }

    private static String computeSignature(
            String workflowId, int epochVersion, String dslSha256, String topologyHash,
            String debugBatchId, int stepCount, long timestamp
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = (workflowId == null ? "" : workflowId) + ":" +
                    epochVersion + ":" +
                    (dslSha256 == null ? "" : dslSha256) + ":" +
                    (topologyHash == null ? "" : topologyHash) + ":" +
                    (debugBatchId == null ? "" : debugBatchId) + ":" +
                    stepCount + ":" +
                    timestamp;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 算法", e);
        }
    }
}
