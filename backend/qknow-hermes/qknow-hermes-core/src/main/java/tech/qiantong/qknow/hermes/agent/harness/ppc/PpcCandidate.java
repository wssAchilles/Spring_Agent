package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.util.List;
import java.util.Objects;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.UsageStatus;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;

public record PpcCandidate(
        String candidateId,
        HermesHarnessVoucher voucher,
        String voucherFingerprint,
        List<Operator> operators,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double totalCostCny,
        boolean allUsageKnown,
        boolean costKnown
) {
    public PpcCandidate {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("candidateId is required");
        }
        voucher = Objects.requireNonNull(voucher, "voucher");
        if (!voucher.matchesFingerprint(voucherFingerprint)) {
            throw new IllegalArgumentException("voucherFingerprint mismatch");
        }
        operators = List.copyOf(operators);
    }

    public static PpcCandidate of(String candidateId, HermesHarnessVoucher voucher, List<Operator> operators) {
        return new PpcCandidate(candidateId, voucher, voucher.computeFingerprint(), operators,
                0L, 0L, 0L, 0.0D, false, false);
    }

    public PpcCandidate withResults(List<PpcProbeResult> results) {
        List<PpcProbeResult> own = results.stream().filter(result -> candidateId.equals(result.candidateId())).toList();
        return new PpcCandidate(candidateId, voucher, voucherFingerprint, operators,
                own.stream().mapToLong(PpcProbeResult::promptTokens).sum(),
                own.stream().mapToLong(PpcProbeResult::completionTokens).sum(),
                own.stream().mapToLong(PpcProbeResult::totalTokens).sum(),
                own.stream().mapToDouble(PpcProbeResult::actualCostCny).sum(),
                !own.isEmpty() && own.stream().allMatch(result -> result.usageStatus() == UsageStatus.KNOWN),
                !own.isEmpty() && own.stream().allMatch(PpcProbeResult::costKnown));
    }
}
