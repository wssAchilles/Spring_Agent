package tech.qiantong.qknow.hermes.causal.dto;

import java.util.List;

/**
 * 时序反事实推演沙盘多分支推演结果集
 *
 * @param sessionId     会话标识
 * @param totalBranches 分支总数
 * @param branches      推演分支列表
 * @param optimalBranch 综合风险与效用评估后的最优候选分支
 * @param elapsedNanos  沙盘分支展开总耗时 (纳秒)
 */
public record SandboxSimulationResult(
        String sessionId,
        int totalBranches,
        List<CounterfactualSandboxBranch> branches,
        CounterfactualSandboxBranch optimalBranch,
        long elapsedNanos
) {
    public SandboxSimulationResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (branches == null) {
            throw new IllegalArgumentException("branches 不能为空");
        }
    }
}
