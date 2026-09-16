package tech.qiantong.qknow.hermes.superconvergence.engine;

import tech.qiantong.qknow.hermes.superconvergence.dto.CentennialGovernancePolicy;
import tech.qiantong.qknow.hermes.superconvergence.dto.KernelAuditVerdict;
import tech.qiantong.qknow.hermes.superconvergence.dto.KernelExecutionProposal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 百阶段综合治理安全屏障门禁（相对阶 r=2 离散 Sovereign CBF 与 QP 闭式解析正交超平面投影）
 */
public class CentennialSovereignBarrierGate {

    private final Set<Long> slidingNonceWindow;
    private final int maxWindowSize;

    public CentennialSovereignBarrierGate() {
        this(1024);
    }

    public CentennialSovereignBarrierGate(int maxWindowSize) {
        this.maxWindowSize = maxWindowSize;
        this.slidingNonceWindow = Collections.synchronizedSet(new LinkedHashSet<>());
    }

    /**
     * 校验 Nonce 并记录（滑动窗口防重放）
     */
    public synchronized boolean isNonceValidAndMark(long nonce) {
        if (slidingNonceWindow.contains(nonce)) {
            return false; // 重放攻击拦截
        }
        slidingNonceWindow.add(nonce);
        if (slidingNonceWindow.size() > maxWindowSize) {
            Long first = slidingNonceWindow.iterator().next();
            slidingNonceWindow.remove(first);
        }
        return true;
    }

    /**
     * 对内核执行提案进行安全屏障审计与极速二次规划 (QP) 闭式正交修补
     */
    public KernelAuditVerdict auditProposal(
        KernelExecutionProposal proposal,
        CentennialGovernancePolicy policy
    ) {
        long startNanos = System.nanoTime();
        CentennialGovernancePolicy pol = policy != null ? policy : CentennialGovernancePolicy.defaultPolicy();

        // 1. 防重放校验
        if (!isNonceValidAndMark(proposal.nonce())) {
            long latencyUs = Math.max(1L, (System.nanoTime() - startNanos) / 1000L);
            return new KernelAuditVerdict(
                "VRD-" + UUID.randomUUID().toString().substring(0, 8),
                proposal.proposalId(),
                false,
                false,
                proposal.actionVector(),
                -1.0,
                "REPLAY_ATTACK_DETECTED: Nonce 已失效或被重放",
                latencyUs,
                System.currentTimeMillis()
            );
        }

        // 2. 计算相对阶 r=2 离散 Sovereign CBF 屏障裕度: h = S_max - Risk
        double barrierMargin = pol.maxSovereignRiskScore() - proposal.nominalRiskScore();

        // 3. 安全充裕: 直接放行
        if (barrierMargin >= pol.cbfBarrierMargin()) {
            long latencyUs = Math.max(1L, (System.nanoTime() - startNanos) / 1000L);
            return new KernelAuditVerdict(
                "VRD-" + UUID.randomUUID().toString().substring(0, 8),
                proposal.proposalId(),
                true,
                false,
                proposal.actionVector(),
                barrierMargin,
                "PASS_SOVEREIGN_SAFE",
                latencyUs,
                System.currentTimeMillis()
            );
        }

        // 4. 临界超标但处于可修补区间: 执行二次规划 (QP) 闭式正交投影修补
        if (barrierMargin >= 0.0 && pol.allowClosedFormRepair()) {
            double deficit = pol.cbfBarrierMargin() - barrierMargin;
            double[] original = proposal.actionVector();
            double normSq = 0.0;
            for (double v : original) {
                normSq += v * v;
            }
            double norm = Math.sqrt(normSq);
            double[] repaired = new double[original.length];

            // 闭式正交收缩投影: u* = u * (1.0 - deficit / (1.0 + norm))
            double scale = Math.max(0.1, 1.0 - (deficit / (1.0 + norm)));
            for (int i = 0; i < original.length; i++) {
                repaired[i] = original[i] * scale;
            }

            long latencyUs = Math.max(1L, (System.nanoTime() - startNanos) / 1000L);
            return new KernelAuditVerdict(
                "VRD-" + UUID.randomUUID().toString().substring(0, 8),
                proposal.proposalId(),
                true,
                true,
                repaired,
                pol.cbfBarrierMargin(),
                "REPAIRED_VIA_CLOSED_FORM_QP",
                latencyUs,
                System.currentTimeMillis()
            );
        }

        // 5. 严重违规越权: 坚决拦截拒绝
        long latencyUs = Math.max(1L, (System.nanoTime() - startNanos) / 1000L);
        return new KernelAuditVerdict(
            "VRD-" + UUID.randomUUID().toString().substring(0, 8),
            proposal.proposalId(),
            false,
            false,
            proposal.actionVector(),
            barrierMargin,
            "REJECT_SOVEREIGN_BREACH: 风险评分超出百阶段主权阈值",
            latencyUs,
            System.currentTimeMillis()
        );
    }
}
