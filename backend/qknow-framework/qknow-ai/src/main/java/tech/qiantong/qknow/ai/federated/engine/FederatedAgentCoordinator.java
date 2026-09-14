package tech.qiantong.qknow.ai.federated.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.federated.dp.DifferentialPrivacyPerturber;
import tech.qiantong.qknow.ai.federated.dto.FederatedTaskResultVO;
import tech.qiantong.qknow.ai.federated.dto.HomomorphicCiphertextDTO;
import tech.qiantong.qknow.ai.federated.dto.PsiIntersectionResultDTO;
import tech.qiantong.qknow.ai.federated.homomorphic.PaillierHomomorphicEngine;
import tech.qiantong.qknow.ai.federated.psi.PsiProtocolEngine;

import java.math.BigInteger;
import java.util.*;

/**
 * 跨域联邦多智能体知识协同调度总控器
 * 
 * 统筹握手、隐私集合求交 (DH-PSI)、局部差分隐私特征交互与 Paillier 同态密文加权聚合。
 */
@Component
public class FederatedAgentCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FederatedAgentCoordinator.class);

    private final PsiProtocolEngine psiEngine;
    private final PaillierHomomorphicEngine paillierEngine;
    private final DifferentialPrivacyPerturber dpPerturber;

    public FederatedAgentCoordinator(PsiProtocolEngine psiEngine,
                                     PaillierHomomorphicEngine paillierEngine,
                                     DifferentialPrivacyPerturber dpPerturber) {
        this.psiEngine = psiEngine;
        this.paillierEngine = paillierEngine;
        this.dpPerturber = dpPerturber;
    }

    /**
     * 执行跨机构多智能体联合协作闭环任务
     *
     * @param taskId 任务标识
     * @param aliceEntities Alice 私有实体集
     * @param bobEntities Bob 私有实体集
     * @param partyConfidences 各参与方提交的明文置信度标量 (如 0~1000)
     * @param partyWeights 各参与方信誉权重
     * @param sharedEmbedding 共有知识特征向量 (用于差分隐私验证)
     * @return 联邦协作任务结果
     */
    public FederatedTaskResultVO coordinateFederatedCollaboration(
            String taskId,
            List<String> aliceEntities,
            List<String> bobEntities,
            Map<String, Long> partyConfidences,
            Map<String, Long> partyWeights,
            double[] sharedEmbedding) {

        long startTime = System.currentTimeMillis();
        log.info("开始执行跨域联邦知识协作任务: taskId={}", taskId);

        // 1. DH-PSI 隐私求交阶段
        BigInteger kA = psiEngine.generatePrivateKey();
        BigInteger kB = psiEngine.generatePrivateKey();

        // 阶段 1: Alice 与 Bob 本地一阶盲化
        Map<String, BigInteger> aliceStage1 = psiEngine.blindLocalSet(aliceEntities, kA);
        Map<String, BigInteger> bobStage1 = psiEngine.blindLocalSet(bobEntities, kB);

        // 阶段 2: 交叉二次盲化
        // Alice 对本地实体施加 kB 形成判定基准: x_i -> H(x_i)^(kA*kB)
        Map<String, BigInteger> aliceDoubleBlindedMap = new LinkedHashMap<>();
        for (Map.Entry<String, BigInteger> entry : aliceStage1.entrySet()) {
            aliceDoubleBlindedMap.put(entry.getKey(), entry.getValue().modPow(kB, PsiProtocolEngine.DEFAULT_PRIME_P));
        }

        // Alice 将 Bob 的一阶盲化集用 kA 盲化，得到 Bob 元素的二次盲化集 B2: y_j -> H(y_j)^(kB*kA)
        List<BigInteger> bobDoubleBlindedValues = psiEngine.blindRemoteSet(bobStage1.values(), kA);

        // 求交: 匹配 H(x_i)^(kA*kB) == H(y_j)^(kB*kA)
        PsiIntersectionResultDTO psiResult = psiEngine.computeIntersection(aliceDoubleBlindedMap, bobDoubleBlindedValues);
        List<String> sharedEntities = psiResult.getMatchedEntityIds();

        // 2. 局部差分隐私特征扰动验证
        boolean dpCompliant = true;
        if (sharedEmbedding != null && sharedEmbedding.length > 0) {
            double[] perturbed = dpPerturber.perturbAndProject(sharedEmbedding, DifferentialPrivacyPerturber.DEFAULT_EPSILON);
            double similarity = dpPerturber.cosineSimilarity(sharedEmbedding, perturbed);
            double norm = dpPerturber.computeL2Norm(perturbed);
            if (similarity < 0.85 || Math.abs(norm - 1.0) > 1e-4) {
                dpCompliant = false;
                log.warn("差分隐私指标未达标: similarity={}, norm={}", similarity, norm);
            }
        }

        // 3. Paillier 密文态加权置信度聚合
        PaillierHomomorphicEngine.PaillierKeyPair keyPair = paillierEngine.generateKeyPair(1024);

        List<BigInteger> ciphertexts = new ArrayList<>();
        List<Long> weights = new ArrayList<>();
        long totalWeight = 0;

        for (Map.Entry<String, Long> entry : partyConfidences.entrySet()) {
            String party = entry.getKey();
            long conf = entry.getValue();
            long weight = partyWeights.getOrDefault(party, 1L);

            HomomorphicCiphertextDTO cipherDTO = paillierEngine.encrypt(conf, keyPair.getN(), keyPair.getG(), party, "CONFIDENCE");
            ciphertexts.add(cipherDTO.getCiphertext());
            weights.add(weight);
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            totalWeight = 1;
        }

        // 协调器在密文态下加权聚合
        BigInteger cAgg = paillierEngine.aggregateWeightedCiphertexts(ciphertexts, weights, keyPair.getNSquared());

        // 解密加权和并计算最终加权平均
        long decryptedSum = paillierEngine.decrypt(cAgg, keyPair);
        double aggregatedConfidence = (double) decryptedSum / (double) totalWeight;

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("联邦知识协作任务完成: taskId={}, 共有实体数={}, 聚合置信度={}, 耗时={}ms",
                taskId, sharedEntities.size(), aggregatedConfidence, elapsedMs);

        return new FederatedTaskResultVO(
                taskId,
                sharedEntities,
                aggregatedConfidence,
                dpCompliant,
                elapsedMs,
                new ArrayList<>(partyConfidences.keySet())
        );
    }
}
