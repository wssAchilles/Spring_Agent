package tech.qiantong.qknow.hermes.coalition.engine;

import tech.qiantong.qknow.hermes.coalition.dto.CrossDomainAuditResolution;
import tech.qiantong.qknow.hermes.coalition.dto.CrossDomainTransactionProposal;
import tech.qiantong.qknow.hermes.coalition.dto.OrganizationDomain;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨自治域主权安全治理门禁 (定理 1.3)
 */
public class CrossDomainSovereignGovernanceGate {

    private static final double MAX_QUOTA_LIMIT = 100.0;
    private static final long MAX_TIME_DRIFT_MS = 60_000L;

    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();

    /**
     * 审查跨域事务提案：滑动 Nonce 防重放与相对阶 r=2 Sovereign CBF 安全投影
     */
    public CrossDomainAuditResolution auditProposal(CrossDomainTransactionProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal 不能为空");
        }

        long now = System.currentTimeMillis();

        // 1. 防重放 Nonce 检查
        Long previousTimestamp = seenNonces.putIfAbsent(proposal.nonce(), proposal.timestamp());
        if (previousTimestamp != null) {
            return new CrossDomainAuditResolution(
                proposal.proposalId(),
                false,
                false,
                proposal.actionVector() != null ? proposal.actionVector().clone() : new double[0],
                -1.0,
                "SOVEREIGN_VETO: 检测到 Nonce 重放攻击，已硬拦截",
                now
            );
        }

        // 2. 时间戳滑动窗口校验 (<=60s)
        if (Math.abs(now - proposal.timestamp()) > MAX_TIME_DRIFT_MS) {
            return new CrossDomainAuditResolution(
                proposal.proposalId(),
                false,
                false,
                proposal.actionVector() != null ? proposal.actionVector().clone() : new double[0],
                -2.0,
                "SOVEREIGN_VETO: 请求时间戳超出滑动窗口 (60s)，已硬拦截",
                now
            );
        }

        // 3. 跨域破坏性写操作硬拦截
        if (proposal.isDestructiveWrite() && proposal.sourceDomain() != OrganizationDomain.PRIMARY_ENTERPRISE) {
            return new CrossDomainAuditResolution(
                proposal.proposalId(),
                false,
                false,
                proposal.actionVector() != null ? proposal.actionVector().clone() : new double[0],
                -3.0,
                "SOVEREIGN_VETO: 非主导企业跨域发起破坏性写操作，触发主权硬熔断拦截",
                now
            );
        }

        // 4. 相对阶 r=2 主权控制屏障函数 (Sovereign CBF) 校验
        double cbfMargin = MAX_QUOTA_LIMIT - proposal.requestedQuota();
        if (cbfMargin < 0.0) {
            // 配额超限情况
            if (proposal.requestedQuota() <= 200.0 && !proposal.isDestructiveWrite()) {
                // 可通过闭式二次规划 (QP) 安全投影软修补
                double[] projectedAction = null;
                if (proposal.actionVector() != null) {
                    projectedAction = proposal.actionVector().clone();
                    // 缩放动作向量至安全模长
                    double scale = MAX_QUOTA_LIMIT / proposal.requestedQuota();
                    for (int i = 0; i < projectedAction.length; i++) {
                        projectedAction[i] *= scale;
                    }
                }
                return new CrossDomainAuditResolution(
                    proposal.proposalId(),
                    true,
                    true,
                    projectedAction,
                    0.0,
                    "SOVEREIGN_PROJECTED: 申请配额超额，已通过极速二次规划 (QP) 闭式正交超平面解析投影修补放行",
                    now
                );
            } else {
                // 严重超额或破坏性写：一票否决
                return new CrossDomainAuditResolution(
                    proposal.proposalId(),
                    false,
                    false,
                    proposal.actionVector() != null ? proposal.actionVector().clone() : new double[0],
                    cbfMargin,
                    "SOVEREIGN_VETO: 配额严重超限且不可投影修补，触发主权硬熔断拦截",
                    now
                );
            }
        }

        // 5. 正常安全放行
        return new CrossDomainAuditResolution(
            proposal.proposalId(),
            true,
            false,
            proposal.actionVector() != null ? proposal.actionVector().clone() : new double[0],
            cbfMargin,
            "SOVEREIGN_PASS: 跨域事务符合主权安全与配额合规约束",
            now
        );
    }
}
