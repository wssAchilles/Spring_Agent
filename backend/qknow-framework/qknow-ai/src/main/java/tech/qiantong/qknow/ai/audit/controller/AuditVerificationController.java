package tech.qiantong.qknow.ai.audit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.audit.merkle.MerkleTreeEngine;

import java.util.List;
import java.util.Map;

/**
 * 密码学不可篡改审计对外免密验真控制器 (AuditVerificationController)
 *
 * 提供客户端 < 1ms 的零明文泄露独立密码学验真 API。
 *
 * @author qknow
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditVerificationController {

    private final MerkleTreeEngine merkleTreeEngine;

    @Autowired
    public AuditVerificationController(MerkleTreeEngine merkleTreeEngine) {
        this.merkleTreeEngine = merkleTreeEngine;
    }

    @PostMapping("/verify-proof")
    public ResponseEntity<Map<String, Object>> verifyProof(@RequestBody VerifyProofRequest request) {
        long startNano = System.nanoTime();
        boolean valid = merkleTreeEngine.verifyInclusionProof(
                request.merkleRoot(),
                request.leafHash(),
                request.proofPath()
        );
        long elapsedMicros = (System.nanoTime() - startNano) / 1000;

        return ResponseEntity.ok(Map.of(
                "verified", valid,
                "merkleRoot", request.merkleRoot(),
                "leafHash", request.leafHash(),
                "elapsedMicroseconds", elapsedMicros,
                "message", valid ? "密码学证据链完整无篡改" : "证据哈希破损或根校验失败，存在数据篡改风险"
        ));
    }

    public record VerifyProofRequest(
            String merkleRoot,
            String leafHash,
            List<MerkleProof.ProofElement> proofPath
    ) {}
}
