package tech.qiantong.qknow.hermes.agent.harness.offline;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 离线证据确定性回放验证器。
 *
 * <p>在不连接外部 LLM 和网络的环境下，以恒定时间和确定性代数算法
 * 验证历史沉淀的不可变证据链签名指纹防篡改性，确保离线 PPC 闭环的客观证据具备 100% 确定性回放能力。</p>
 */
public final class OfflineEvidenceReplayer {

    private OfflineEvidenceReplayer() {}

    /**
     * 回放验证汇总结果凭单。
     */
    public record ReplayVerificationResult(
            boolean allVerified,
            int totalRecords,
            int validCount,
            List<String> failedEvidenceIds
    ) {}

    /**
     * 确定性回放并验证整组证据凭单的防篡改签名与代数一致性。
     */
    public static ReplayVerificationResult replayAndVerify(List<HarnessEvidenceRecord> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return new ReplayVerificationResult(true, 0, 0, List.of());
        }

        List<String> failures = new ArrayList<>();
        int valid = 0;

        for (HarnessEvidenceRecord record : evidenceList) {
            Objects.requireNonNull(record, "record 不能为 null");
            String recomputedDigest = record.computeDigest();
            if (record.verifyDigest(recomputedDigest)) {
                valid++;
            } else {
                failures.add(record.evidenceId());
            }
        }

        return new ReplayVerificationResult(
                failures.isEmpty(),
                evidenceList.size(),
                valid,
                List.copyOf(failures)
        );
    }
}
