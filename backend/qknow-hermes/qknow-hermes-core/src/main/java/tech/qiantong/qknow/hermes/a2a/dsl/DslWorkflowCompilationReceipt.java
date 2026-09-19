package tech.qiantong.qknow.hermes.a2a.dsl;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 声明式工作流不可变编译凭单（封装三阶门禁报告与 SHA-256 自签名）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DslWorkflowCompilationReceipt(
        String receiptId,
        String workflowId,
        int version,
        String astDigest,
        String topologyHash,
        List<String> gateReports,
        String sha256Signature,
        long compiledTimestamp
) {
    public DslWorkflowCompilationReceipt {
        if (gateReports == null) gateReports = List.of();
        else gateReports = List.copyOf(gateReports);
    }

    /**
     * 创建并自动签署不可变编译凭单
     */
    public static DslWorkflowCompilationReceipt create(
            String workflowId,
            int version,
            String astDigest,
            String topologyHash,
            List<String> gateReports
    ) {
        String receiptId = "RCP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();
        String signature = computeSignature(workflowId, version, astDigest, topologyHash, now);
        return new DslWorkflowCompilationReceipt(
                receiptId,
                workflowId,
                version,
                astDigest,
                topologyHash,
                gateReports,
                signature,
                now
        );
    }

    /**
     * 校验凭单自签名完整性
     */
    public boolean verifySignature() {
        String expected = computeSignature(workflowId, version, astDigest, topologyHash, compiledTimestamp);
        return expected.equals(sha256Signature);
    }

    private static String computeSignature(String workflowId, int version, String astDigest, String topologyHash, long timestamp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = workflowId + ":" + version + ":" + astDigest + ":" + topologyHash + ":" + timestamp;
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 算法", e);
        }
    }
}
