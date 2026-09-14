package tech.qiantong.qknow.ai.immune.engine;

import tech.qiantong.qknow.ai.immune.dto.AntibodyDTO;
import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;
import tech.qiantong.qknow.ai.immune.dto.ImmuneDefenseReportVO;
import tech.qiantong.qknow.ai.immune.dto.RedTeamProbeDTO;
import tech.qiantong.qknow.ai.immune.enums.ImmuneAction;

import java.util.ArrayList;
import java.util.List;

/**
 * 企业级自进化攻防演练与主动免疫总控协调器
 * <p>
 * 驱动红蓝两队多轮交替演练博弈，实现未知变异样本自适应捕获、抗体动态成熟与记忆库热加载。
 * 遵循定理 2.1 极小极大演化博弈收敛定理与 Fail-Open 优雅降级保护。
 *
 * @author Achilles
 * @since Phase 45
 */
public class SelfEvolvingImmuneCoordinator {

    private final AntigenExtractor antigenExtractor;
    private final ImmuneMemoryLedger immuneMemoryLedger;
    private final RedTeamAdversaryAgent redTeamAgent;
    private final BlueTeamDefenderAgent blueTeamDefenderAgent;

    public SelfEvolvingImmuneCoordinator(AntigenExtractor antigenExtractor,
                                         ImmuneMemoryLedger immuneMemoryLedger,
                                         RedTeamAdversaryAgent redTeamAgent,
                                         BlueTeamDefenderAgent blueTeamDefenderAgent) {
        this.antigenExtractor = antigenExtractor;
        this.immuneMemoryLedger = immuneMemoryLedger;
        this.redTeamAgent = redTeamAgent;
        this.blueTeamDefenderAgent = blueTeamDefenderAgent;
    }

    /**
     * 前台在线实时请求安全免疫检查门禁 (MTTC <= 1ms)
     *
     * @param userPayload 用户输入载荷或检索切片
     * @return 免疫判定动作
     */
    public ImmuneAction inspectRequest(String userPayload) {
        try {
            if (userPayload == null || userPayload.trim().isEmpty()) {
                return ImmuneAction.SAFE_PASS;
            }
            AntigenDTO antigen = antigenExtractor.extractAntigen(userPayload);
            ImmuneMemoryLedger.ImmuneCheckResult result = immuneMemoryLedger.matchImmunity(antigen);
            return result.getAction();
        } catch (Exception e) {
            // Fail-Open 容灾降级：异常时不阻塞核心业务流
            return ImmuneAction.SAFE_PASS;
        }
    }

    /**
     * 运行端到端红蓝对抗攻防演练闭环
     *
     * @param seedPayload 演练种子攻击语义
     * @param rounds      演练轮次 (建议 2~3 轮)
     * @return 演练综合态势分析 VO
     */
    public ImmuneDefenseReportVO runAdversarialExercise(String seedPayload, int rounds) {
        long startTime = System.currentTimeMillis();
        ImmuneDefenseReportVO report = new ImmuneDefenseReportVO();
        List<String> newAntibodies = new ArrayList<>();

        int totalProbes = 0;
        int totalBlocked = 0;

        for (int r = 1; r <= rounds; r++) {
            // 1. 红队生成变异探针集合
            List<RedTeamProbeDTO> probes = redTeamAgent.generateProbes(seedPayload);
            totalProbes += probes.size();

            // 2. 蓝队实时防御检测
            for (RedTeamProbeDTO probe : probes) {
                AntigenDTO antigen = antigenExtractor.extractAntigen(probe.getAttackPayload());
                ImmuneMemoryLedger.ImmuneCheckResult checkResult = immuneMemoryLedger.matchImmunity(antigen);

                if (checkResult.getAction() == ImmuneAction.BLOCK_AND_ISOLATE) {
                    totalBlocked++;
                } else {
                    // 3. 突破防线：蓝队触发自愈免疫，生成新型抗体并注册
                    AntibodyDTO antibody = blueTeamDefenderAgent.generateAntibody(probe);
                    if (antibody != null) {
                        immuneMemoryLedger.registerAntibody(antibody);
                        newAntibodies.add(antibody.getAntibodyId());
                    }
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        double avgLatency = totalProbes > 0 ? (double) duration / totalProbes : 0.0;
        double defenseRate = totalProbes > 0 ? (double) totalBlocked / totalProbes : 1.0;

        report.setTotalProbesGenerated(totalProbes);
        report.setTotalBlocked(totalBlocked);
        report.setTotalAntibodiesActive(immuneMemoryLedger.size());
        report.setDefenseSuccessRate(defenseRate);
        report.setAverageLatencyMs(avgLatency);
        report.setEvolvedAntibodyIds(newAntibodies);
        report.setNashEquilibriumReached(defenseRate >= 0.70 || immuneMemoryLedger.size() >= 4);

        return report;
    }
}
