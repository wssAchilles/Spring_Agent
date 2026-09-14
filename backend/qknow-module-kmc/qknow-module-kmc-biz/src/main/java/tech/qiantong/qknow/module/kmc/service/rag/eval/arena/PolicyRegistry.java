package tech.qiantong.qknow.module.kmc.service.rag.eval.arena;

import org.springframework.stereotype.Service;
import tech.qiantong.qknow.module.kmc.service.rag.eval.arena.dto.ArenaPolicyDO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 竞技场策略注册表与轻量内存仓储
 *
 * @author qknow
 */
@Service
public class PolicyRegistry {
    private final Map<String, ArenaPolicyDO> policyStore = new ConcurrentHashMap<>();

    public void registerPolicy(ArenaPolicyDO policy) {
        if (policy == null || policy.getPolicyId() == null) {
            throw new IllegalArgumentException("策略实体及 policyId 不能为空");
        }
        policyStore.put(policy.getPolicyId(), policy);
    }

    public ArenaPolicyDO getPolicy(String policyId) {
        return policyStore.get(policyId);
    }

    public List<ArenaPolicyDO> listPolicies() {
        return new ArrayList<>(policyStore.values());
    }

    public List<ArenaPolicyDO> listActivePolicies() {
        return policyStore.values().stream()
                .filter(p -> p.getStatus() == ArenaPolicyDO.PolicyStatus.ACTIVE || p.getStatus() == ArenaPolicyDO.PolicyStatus.CANDIDATE)
                .collect(Collectors.toList());
    }

    public void updatePolicyRating(String policyId, double newRating, boolean isWin, boolean isLoss, boolean isTie) {
        ArenaPolicyDO policy = policyStore.get(policyId);
        if (policy != null) {
            policy.setEloRating(newRating);
            policy.setMatchCount(policy.getMatchCount() + 1);
            if (isWin) {
                policy.setWinCount(policy.getWinCount() + 1);
            }
            if (isLoss) {
                policy.setLossCount(policy.getLossCount() + 1);
            }
            if (isTie) {
                policy.setTieCount(policy.getTieCount() + 1);
            }
            policy.setLastUpdated(System.currentTimeMillis());
        }
    }

    public void updateStatus(String policyId, ArenaPolicyDO.PolicyStatus status) {
        ArenaPolicyDO policy = policyStore.get(policyId);
        if (policy != null) {
            policy.setStatus(status);
            policy.setLastUpdated(System.currentTimeMillis());
        }
    }

    public void clear() {
        policyStore.clear();
    }
}
