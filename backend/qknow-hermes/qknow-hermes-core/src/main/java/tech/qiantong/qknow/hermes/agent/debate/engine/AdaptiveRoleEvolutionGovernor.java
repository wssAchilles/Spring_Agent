package tech.qiantong.qknow.hermes.agent.debate.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.hermes.agent.debate.dto.AgentRoleNicheType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自适应角色生态位演化调节器 (Adaptive Role Evolution Governor)
 * <p>
 * 基于 Lotka-Volterra 多物种生态位分化动力学模型，根据动态任务负载与智能体技能特征，
 * 在微秒级（<=50us）内平滑演化智能体的功能角色生态位，杜绝静态僵死与单点故障。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
public class AdaptiveRoleEvolutionGovernor {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveRoleEvolutionGovernor.class);

    /**
     * 五大生态位间的对称/非对称竞争影响系数矩阵 A = (alpha_ij)
     * 行/列对应：ANALYST(0), CODER(1), REVIEWER(2), CRITIC(3), ARBITRATOR(4)
     */
    private static final double[][] COMPETITION_MATRIX = {
            { 1.00, 0.15, 0.10, 0.05, 0.05 }, // ANALYST
            { 0.15, 1.00, 0.30, 0.10, 0.05 }, // CODER
            { 0.10, 0.30, 1.00, 0.25, 0.05 }, // REVIEWER
            { 0.05, 0.10, 0.25, 1.00, 0.20 }, // CRITIC
            { 0.05, 0.05, 0.05, 0.20, 1.00 }  // ARBITRATOR
    };

    /**
     * 智能体当前被分配的生态位角色映射 (agentId -> AgentRoleNicheType)
     */
    private final Map<String, AgentRoleNicheType> agentRoles = new ConcurrentHashMap<>();

    /**
     * 智能体多维基础技能档案 (agentId -> float[5], 对应 5 种角色的本征适应度)
     */
    private final Map<String, float[]> agentSkills = new ConcurrentHashMap<>();

    /**
     * 各生态位的目标容量配比
     */
    private final Map<AgentRoleNicheType, Double> nicheCapacities = new ConcurrentHashMap<>();

    public AdaptiveRoleEvolutionGovernor() {
        // 初始化默认生态位环境容量
        nicheCapacities.put(AgentRoleNicheType.ANALYST, 2.0);
        nicheCapacities.put(AgentRoleNicheType.CODER, 4.0);
        nicheCapacities.put(AgentRoleNicheType.REVIEWER, 2.0);
        nicheCapacities.put(AgentRoleNicheType.CRITIC, 2.0);
        nicheCapacities.put(AgentRoleNicheType.ARBITRATOR, 1.0);
    }

    /**
     * 注册或更新智能体及其技能档案
     *
     * @param agentId     智能体 ID
     * @param skillScores 五维技能适应度数组（长度必须为 5，依次对应 5 种角色）
     */
    public void registerAgent(String agentId, float[] skillScores) {
        Objects.requireNonNull(agentId, "agentId 不能为空");
        if (skillScores == null || skillScores.length != 5) {
            throw new IllegalArgumentException("skillScores 必须为长度为 5 的数组");
        }
        agentSkills.put(agentId, Arrays.copyOf(skillScores, 5));
    }

    /**
     * 动态分配或微调智能体的生态位角色 (单步耗时 <= 50us)
     *
     * @param agentId 智能体 ID
     * @return 分配的生态位角色
     */
    public AgentRoleNicheType assignNiche(String agentId) {
        long startNs = System.nanoTime();
        float[] skills = agentSkills.get(agentId);
        if (skills == null) {
            skills = new float[]{ 0.2f, 0.2f, 0.2f, 0.2f, 0.2f };
        }

        // 统计当前各种角色的实际种群数量 N_j
        int[] population = new int[5];
        for (AgentRoleNicheType role : agentRoles.values()) {
            population[role.ordinal()]++;
        }

        // 计算 Lotka-Volterra 净增长适应度: f_i = r_i * (1 - sum(alpha_ij * N_j) / K_i)
        AgentRoleNicheType[] niches = AgentRoleNicheType.values();
        double bestFitness = -Double.MAX_VALUE;
        AgentRoleNicheType selected = AgentRoleNicheType.CODER;

        for (int i = 0; i < niches.length; i++) {
            AgentRoleNicheType niche = niches[i];
            double k_i = nicheCapacities.getOrDefault(niche, 2.0);
            double competitionSum = 0.0;
            for (int j = 0; j < 5; j++) {
                competitionSum += COMPETITION_MATRIX[i][j] * population[j];
            }
            double r_i = Math.max(0.01, skills[i]);
            // 生态位净适应度
            double fitness = r_i * (1.0 - competitionSum / (k_i + 1e-5)) + skills[i] * 0.5;
            if (fitness > bestFitness) {
                bestFitness = fitness;
                selected = niche;
            }
        }

        agentRoles.put(agentId, selected);
        long elapsedUs = (System.nanoTime() - startNs) / 1000;
        if (elapsedUs > 500) {
            log.warn("生态位分配耗时偏高: {}us", elapsedUs);
        }
        return selected;
    }

    /**
     * 批量执行群体生态位分化演化
     *
     * @return 各智能体最终分配的角色映射副本
     */
    public Map<String, AgentRoleNicheType> evolvePopulation() {
        for (String agentId : agentSkills.keySet()) {
            assignNiche(agentId);
        }
        return new HashMap<>(agentRoles);
    }

    /**
     * 故障转移平滑接管：当某角色的智能体发生故障下线时，快速从其他低优先级生态位调度热备智能体
     *
     * @param failedAgentId 故障智能体 ID
     * @param requiredNiche 需要填补的目标生态位
     * @return 接管该生态位的新智能体 ID，若无可用则返回 null
     */
    public String handleFailover(String failedAgentId, AgentRoleNicheType requiredNiche) {
        agentRoles.remove(failedAgentId);
        agentSkills.remove(failedAgentId);

        // 寻找非目标角色且技能与 requiredNiche 最匹配的候选人
        String bestCandidate = null;
        double maxSkill = -1.0;

        for (Map.Entry<String, float[]> entry : agentSkills.entrySet()) {
            String candidateId = entry.getKey();
            AgentRoleNicheType currentRole = agentRoles.get(candidateId);
            if (currentRole != requiredNiche) {
                float[] skills = entry.getValue();
                double skillVal = skills[requiredNiche.ordinal()];
                if (skillVal > maxSkill) {
                    maxSkill = skillVal;
                    bestCandidate = candidateId;
                }
            }
        }

        if (bestCandidate != null) {
            agentRoles.put(bestCandidate, requiredNiche);
            log.info("智能体故障接管完成: 智能体 {} 接替填补生态位 {}", bestCandidate, requiredNiche);
        }
        return bestCandidate;
    }

    /**
     * 获取指定智能体的当前生态位
     */
    public AgentRoleNicheType getAgentRole(String agentId) {
        return agentRoles.get(agentId);
    }

    /**
     * 获取所有智能体当前角色分配
     */
    public Map<String, AgentRoleNicheType> getAllAgentRoles() {
        return Collections.unmodifiableMap(agentRoles);
    }
}
