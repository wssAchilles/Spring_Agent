package tech.qiantong.qknow.ai.pipeline.stage.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.audit.merkle.MerkleEvidenceItem;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;
import tech.qiantong.qknow.ai.pipeline.context.AiPipelineContext;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStage;
import tech.qiantong.qknow.ai.pipeline.stage.PipelineStageHandler;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/**
 * 阶段 6 处理器: 密码学不可篡改 Merkle 证据链存证
 *
 * 旁路存证阶段: 将用户请求、脱敏结果与输出生成严格按照 RFC 6962 前缀构建平衡二叉树并固化根哈希。
 * 容灾策略: Fail-Open 降级放行。
 *
 * @author qknow
 */
@Slf4j
@Component
public class MerkleAuditStageHandler implements PipelineStageHandler {

    private final MerkleTreeEngine merkleTreeEngine;

    @Autowired
    public MerkleAuditStageHandler(MerkleTreeEngine merkleTreeEngine) {
        this.merkleTreeEngine = merkleTreeEngine;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.MERKLE_AUDIT;
    }

    @Override
    public boolean shouldActivate(AiPipelineContext context) {
        Boolean enable = context.getAttribute("config.enableMerkleAudit");
        return enable == null || Boolean.TRUE.equals(enable);
    }

    @Override
    public long getStageTimeoutMs(AiPipelineContext context) {
        return 500L;
    }

    @Override
    public void handle(AiPipelineContext context) throws Exception {
        try {
            long now = System.currentTimeMillis();
            String pHash1 = sha256(context.getRawPrompt() != null ? context.getRawPrompt() : "");
            String pHash2 = sha256(context.getSanitizedPrompt() != null ? context.getSanitizedPrompt() : "");
            String pHash3 = sha256(context.getSanitizedOutput() != null ? context.getSanitizedOutput() : "");

            List<MerkleEvidenceItem> items = Arrays.asList(
                    new MerkleEvidenceItem("PROMPT", pHash1, now),
                    new MerkleEvidenceItem("SANITIZED", pHash2, now),
                    new MerkleEvidenceItem("OUTPUT", pHash3, now)
            );

            MerkleTreeEngine.MerkleTreeBuildResult treeResult = merkleTreeEngine.buildTree(context.getTraceId(), items);
            MerkleProof proof = merkleTreeEngine.generateInclusionProof(treeResult, 2);

            context.setMerkleResult(treeResult.rootHash(), proof);
            log.debug("[MerkleAudit] 证据链存证完成: rootHash={}", treeResult.rootHash());
        } catch (Exception e) {
            log.warn("[MerkleAudit] 存证发生异常，执行 Fail-Open 降级: {}", e.getMessage());
            context.setAttribute("MERKLE_AUDIT.degraded", true);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "hash-fallback-" + input.hashCode();
        }
    }
}
