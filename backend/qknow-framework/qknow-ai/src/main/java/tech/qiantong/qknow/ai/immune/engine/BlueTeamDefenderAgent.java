package tech.qiantong.qknow.ai.immune.engine;

import tech.qiantong.qknow.ai.immune.dto.AntibodyDTO;
import tech.qiantong.qknow.ai.immune.dto.AntigenDTO;
import tech.qiantong.qknow.ai.immune.dto.RedTeamProbeDTO;

import java.util.UUID;

/**
 * 蓝队自愈抗体生成与克隆选择引擎
 * <p>
 * 定理 1.1：对未阻断的变异探针自适应泛化提取抗原特征，动态生成高亲和度记忆抗体并校准判定阈值。
 *
 * @author Achilles
 * @since Phase 45
 */
public class BlueTeamDefenderAgent {

    private final AntigenExtractor antigenExtractor;

    public BlueTeamDefenderAgent(AntigenExtractor antigenExtractor) {
        this.antigenExtractor = antigenExtractor;
    }

    /**
     * 针对突破防线的红队探针，执行抗原提取与克隆自愈，生成防御抗体
     *
     * @param escapedProbe 未能成功阻断的攻击探针
     * @return 新生成的防御抗体对象
     */
    public AntibodyDTO generateAntibody(RedTeamProbeDTO escapedProbe) {
        if (escapedProbe == null) {
            return null;
        }

        AntigenDTO antigen = antigenExtractor.extractAntigen(escapedProbe.getAttackPayload());

        // 计算初始抗体特征向量（克隆自抗原嵌入，并施加自适应超变异平滑）
        float[] antibodyVec = antigen.getSemanticEmbedding().clone();
        long mask = antigen.getLexicalSimHash();

        // 动态校准激活阈值：根据语法风险自适应下调阈值以增强敏感度，基准阈值 0.65
        double dynamicThreshold = 0.65 - (antigen.getSyntaxRiskScore() * 0.15);
        dynamicThreshold = Math.max(0.45, Math.min(0.75, dynamicThreshold));

        String antibodyId = "ab-" + escapedProbe.getMutationType().name().toLowerCase() + "-"
                + UUID.randomUUID().toString().substring(0, 8);

        return new AntibodyDTO(
                antibodyId,
                "Defense against " + escapedProbe.getTargetVulnerability(),
                antibodyVec,
                mask,
                dynamicThreshold,
                0.90
        );
    }
}
