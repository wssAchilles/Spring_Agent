package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.federated.dp.DifferentialPrivacyPerturber;
import tech.qiantong.qknow.ai.federated.dto.FederatedTaskResultVO;
import tech.qiantong.qknow.ai.federated.dto.HomomorphicCiphertextDTO;
import tech.qiantong.qknow.ai.federated.dto.PsiIntersectionResultDTO;
import tech.qiantong.qknow.ai.federated.engine.FederatedAgentCoordinator;
import tech.qiantong.qknow.ai.federated.homomorphic.PaillierHomomorphicEngine;
import tech.qiantong.qknow.ai.federated.psi.PsiProtocolEngine;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 43 契约测试：隐私计算多方安全求交与联邦 Agent 跨域知识共享网络
 */
public class Phase43FederatedPrivacyPreservingContractTest {

    private PsiProtocolEngine psiEngine;
    private PaillierHomomorphicEngine paillierEngine;
    private DifferentialPrivacyPerturber dpPerturber;
    private FederatedAgentCoordinator coordinator;

    @BeforeEach
    public void setUp() {
        this.psiEngine = new PsiProtocolEngine();
        this.paillierEngine = new PaillierHomomorphicEngine();
        this.dpPerturber = new DifferentialPrivacyPerturber();
        this.coordinator = new FederatedAgentCoordinator(psiEngine, paillierEngine, dpPerturber);
    }

    @Test
    @DisplayName("契约 1: DH-PSI 隐私求交交集匹配准确率 100% (定理 1.1)")
    public void test01_PsiZeroKnowledgeIntersectionCorrectness() {
        List<String> aliceItems = Arrays.asList("cust_101", "cust_102", "cust_103", "cust_104", "cust_105");
        List<String> bobItems = Arrays.asList("cust_103", "cust_105", "cust_106", "cust_107");

        BigInteger kA = psiEngine.generatePrivateKey();
        BigInteger kB = psiEngine.generatePrivateKey();

        // 阶段 1: Alice 与 Bob 分别本地一阶盲化
        Map<String, BigInteger> aliceStage1 = psiEngine.blindLocalSet(aliceItems, kA);
        Map<String, BigInteger> bobStage1 = psiEngine.blindLocalSet(bobItems, kB);

        // 阶段 2: 交叉二次盲化
        // Alice 对本地一阶盲化施加 kB: x_i -> H(x_i)^(kA*kB)
        Map<String, BigInteger> aliceDoubleBlindedMap = new LinkedHashMap<>();
        for (Map.Entry<String, BigInteger> entry : aliceStage1.entrySet()) {
            aliceDoubleBlindedMap.put(entry.getKey(), entry.getValue().modPow(kB, PsiProtocolEngine.DEFAULT_PRIME_P));
        }

        // Bob 的一阶盲化结果被 Alice 施加 kA 进行二次盲化: y_j -> H(y_j)^(kB*kA)
        List<BigInteger> bobDoubleBlindedValues = psiEngine.blindRemoteSet(bobStage1.values(), kA);

        // 求交判定: 比较 H(x_i)^(kA*kB) == H(y_j)^(kB*kA)
        PsiIntersectionResultDTO result = psiEngine.computeIntersection(aliceDoubleBlindedMap, bobDoubleBlindedValues);

        assertNotNull(result);
        assertEquals(2, result.getIntersectionSize(), "共有交集大小应严格为 2");
        assertTrue(result.getMatchedEntityIds().contains("cust_103"), "应包含 cust_103");
        assertTrue(result.getMatchedEntityIds().contains("cust_105"), "应包含 cust_105");
        assertFalse(result.getMatchedEntityIds().contains("cust_101"), "不应包含 cust_101");
        assertTrue(result.isZeroLeakageVerified(), "应标记零知识泄露审计验证通过");
    }

    @Test
    @DisplayName("契约 2: DH-PSI 不相交集合零假阳性与零泄露 (定理 1.1)")
    public void test02_PsiDisjointSetsZeroLeakage() {
        List<String> aliceItems = Arrays.asList("entity_A1", "entity_A2", "entity_A3");
        List<String> bobItems = Arrays.asList("entity_B1", "entity_B2", "entity_B3");

        BigInteger kA = psiEngine.generatePrivateKey();
        BigInteger kB = psiEngine.generatePrivateKey();

        Map<String, BigInteger> aliceStage1 = psiEngine.blindLocalSet(aliceItems, kA);
        Map<String, BigInteger> bobStage1 = psiEngine.blindLocalSet(bobItems, kB);

        Map<String, BigInteger> aliceDoubleBlindedMap = new LinkedHashMap<>();
        for (Map.Entry<String, BigInteger> entry : aliceStage1.entrySet()) {
            aliceDoubleBlindedMap.put(entry.getKey(), entry.getValue().modPow(kB, PsiProtocolEngine.DEFAULT_PRIME_P));
        }

        List<BigInteger> bobDoubleBlindedValues = psiEngine.blindRemoteSet(bobStage1.values(), kA);

        PsiIntersectionResultDTO result = psiEngine.computeIntersection(aliceDoubleBlindedMap, bobDoubleBlindedValues);
        assertEquals(0, result.getIntersectionSize(), "完全不相交集合交集大小严格为 0");
        assertTrue(result.getMatchedEntityIds().isEmpty(), "交集列表应为空");
        assertTrue(result.isZeroLeakageVerified());
    }

    @Test
    @DisplayName("契约 3: Paillier 密文加法同态运算严格代数一致性 (定理 2.1)")
    public void test03_PaillierHomomorphicAdditionExactness() {
        PaillierHomomorphicEngine.PaillierKeyPair keyPair = paillierEngine.generateKeyPair(1024);

        long m1 = 350;
        long m2 = 420;

        HomomorphicCiphertextDTO c1 = paillierEngine.encrypt(m1, keyPair.getN(), keyPair.getG(), "PartyA", "Score1");
        HomomorphicCiphertextDTO c2 = paillierEngine.encrypt(m2, keyPair.getN(), keyPair.getG(), "PartyB", "Score2");

        // 同态加法: c_sum = c1 * c2 mod n^2
        BigInteger cSum = paillierEngine.homomorphicAdd(c1.getCiphertext(), c2.getCiphertext(), keyPair.getNSquared());
        long decryptedSum = paillierEngine.decrypt(cSum, keyPair);

        assertEquals(m1 + m2, decryptedSum, "同态加法解密结果必须严格等于明文相加 (350+420=770)");
    }

    @Test
    @DisplayName("契约 4: Paillier 密文标量乘法同态运算严格代数一致性 (定理 2.1)")
    public void test04_PaillierHomomorphicScalarMultiplicationExactness() {
        PaillierHomomorphicEngine.PaillierKeyPair keyPair = paillierEngine.generateKeyPair(1024);

        long m = 180;
        long scalar = 4;

        HomomorphicCiphertextDTO c = paillierEngine.encrypt(m, keyPair.getN(), keyPair.getG(), "PartyA", "BaseScore");

        // 同态标量乘法: c_prod = c^scalar mod n^2
        BigInteger cProd = paillierEngine.homomorphicMultiplyScalar(c.getCiphertext(), scalar, keyPair.getNSquared());
        long decryptedProd = paillierEngine.decrypt(cProd, keyPair);

        assertEquals(m * scalar, decryptedProd, "同态标量乘法解密结果必须严格等于明文乘以标量 (180*4=720)");
    }

    @Test
    @DisplayName("契约 5: 多智能体置信度与权重密文加权聚合无偏收敛 (定理 2.1)")
    public void test05_PaillierFederatedWeightedConfidenceAggregation() {
        PaillierHomomorphicEngine.PaillierKeyPair keyPair = paillierEngine.generateKeyPair(1024);

        HomomorphicCiphertextDTO cA = paillierEngine.encrypt(850, keyPair.getN(), keyPair.getG(), "AgentA", "CONF");
        HomomorphicCiphertextDTO cB = paillierEngine.encrypt(920, keyPair.getN(), keyPair.getG(), "AgentB", "CONF");
        HomomorphicCiphertextDTO cC = paillierEngine.encrypt(780, keyPair.getN(), keyPair.getG(), "AgentC", "CONF");

        List<BigInteger> ciphertexts = Arrays.asList(cA.getCiphertext(), cB.getCiphertext(), cC.getCiphertext());
        List<Long> weights = Arrays.asList(3L, 5L, 2L);

        BigInteger cAgg = paillierEngine.aggregateWeightedCiphertexts(ciphertexts, weights, keyPair.getNSquared());
        long decryptedSum = paillierEngine.decrypt(cAgg, keyPair);

        assertEquals(8710L, decryptedSum, "密文聚合解密加权和应严格等于 8710");
        double average = (double) decryptedSum / 10.0;
        assertEquals(871.0, average, 1e-6, "无偏加权平均应精确为 871.0");
    }

    @Test
    @DisplayName("契约 6: 同态模数防溢出与明文边界校验防御")
    public void test06_PaillierOverflowDefenseAndInputValidation() {
        PaillierHomomorphicEngine.PaillierKeyPair keyPair = paillierEngine.generateKeyPair(1024);

        assertThrows(IllegalArgumentException.class, () -> {
            paillierEngine.encrypt(PaillierHomomorphicEngine.MAX_PLAINTEXT_SCALAR + 1, keyPair.getN(), keyPair.getG(), "BadAgent", "TEST");
        }, "超出明文上限必须抛出 IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> {
            paillierEngine.encrypt(-1, keyPair.getN(), keyPair.getG(), "BadAgent", "TEST");
        }, "负数明文必须抛出 IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> {
            paillierEngine.homomorphicAdd(null, BigInteger.ONE, keyPair.getNSquared());
        }, "空参数必须拦截");
    }

    @Test
    @DisplayName("契约 7: 1536 维向量局部差分隐私拉普拉斯加噪与超球面保模 (定理 3.1)")
    public void test07_DifferentialPrivacyLaplaceNoiseAndNormConservation() {
        int dim = DifferentialPrivacyPerturber.EMBEDDING_DIM;
        double[] original = new double[dim];
        Random rand = new Random(42);
        double sumSq = 0.0;
        for (int i = 0; i < dim; i++) {
            original[i] = rand.nextGaussian();
            sumSq += original[i] * original[i];
        }
        double norm = Math.sqrt(sumSq);
        for (int i = 0; i < dim; i++) {
            original[i] /= norm;
        }

        double[] perturbed = dpPerturber.perturbAndProject(original, DifferentialPrivacyPerturber.DEFAULT_EPSILON);

        // 验证保模重投影后的模长等于 1.0
        double perturbedNorm = dpPerturber.computeL2Norm(perturbed);
        assertEquals(1.0, perturbedNorm, 1e-6, "扰动后超球面重投影向量 L2 模长必须精确等于 1.0");

        // 验证余弦相似度保真度 >= 0.85
        double cosine = dpPerturber.cosineSimilarity(original, perturbed);
        assertTrue(cosine >= 0.85, "局部差分隐私特征在 epsilon=2.0 下余弦相似度保真度应 >= 0.85，实际=" + cosine);
    }

    @Test
    @DisplayName("契约 8: 差分隐私抗向量反演重构有效位移 (定理 3.1)")
    public void test08_DifferentialPrivacyResistanceAgainstInversion() {
        int dim = DifferentialPrivacyPerturber.EMBEDDING_DIM;
        double[] original = new double[dim];
        Arrays.fill(original, 1.0 / Math.sqrt(dim)); // 均匀分布的单位向量

        double[] perturbed = dpPerturber.perturbAndProject(original, 1.0); // 强隐私 epsilon = 1.0
        double distance = dpPerturber.computeEuclideanDistance(original, perturbed);

        // 强隐私下向量应有明显欧氏位移，有效阻断直接反向嗅探
        assertTrue(distance > 0.01, "强隐私扰动下必须产生显著欧氏位移，实际距离=" + distance);
    }

    @Test
    @DisplayName("契约 9: 跨域 Agent 握手、求交、差分交换与密文打分端到端闭环")
    public void test09_FederatedAgentEndToEndWorkflow() {
        List<String> aliceEntities = Arrays.asList("KB_E1", "KB_E2", "KB_E3", "KB_E4");
        List<String> bobEntities = Arrays.asList("KB_E3", "KB_E4", "KB_E5", "KB_E6");

        Map<String, Long> confidences = new HashMap<>();
        confidences.put("AgentAlice", 900L);
        confidences.put("AgentBob", 800L);

        Map<String, Long> weights = new HashMap<>();
        weights.put("AgentAlice", 1L);
        weights.put("AgentBob", 1L);

        double[] dummyVector = new double[1536];
        Arrays.fill(dummyVector, 1.0 / Math.sqrt(1536));

        FederatedTaskResultVO result = coordinator.coordinateFederatedCollaboration(
                "FED-TASK-001",
                aliceEntities,
                bobEntities,
                confidences,
                weights,
                dummyVector
        );

        assertNotNull(result);
        assertEquals("FED-TASK-001", result.getTaskId());
        assertEquals(2, result.getSharedEntities().size(), "共有知识实体交集大小应为 2 (KB_E3, KB_E4)");
        assertTrue(result.getSharedEntities().contains("KB_E3"));
        assertTrue(result.getSharedEntities().contains("KB_E4"));
        assertEquals(850.0, result.getAggregatedConfidence(), 1e-6, "加权平均置信度应为 850.0");
        assertTrue(result.isDifferentialPrivacyCompliant(), "差分隐私审计标记应通过");
    }

    @Test
    @DisplayName("契约 10: 百级实体与多方参与端到端耗时 MTTC <= 1000ms")
    public void test10_FederatedExecutionLatencyUnderOneSecond() {
        int n = 100;
        List<String> aliceEntities = new ArrayList<>();
        List<String> bobEntities = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            aliceEntities.add("doc_entity_alice_" + i);
            bobEntities.add("doc_entity_bob_" + (i + 50)); // 50 个重叠实体
        }

        Map<String, Long> confidences = new HashMap<>();
        confidences.put("AgentA", 860L);
        confidences.put("AgentB", 910L);
        confidences.put("AgentC", 880L);

        Map<String, Long> weights = new HashMap<>();
        weights.put("AgentA", 2L);
        weights.put("AgentB", 2L);
        weights.put("AgentC", 1L);

        double[] dummyVector = new double[1536];
        Arrays.fill(dummyVector, 1.0 / Math.sqrt(1536));

        long start = System.currentTimeMillis();
        FederatedTaskResultVO result = coordinator.coordinateFederatedCollaboration(
                "BENCHMARK-TASK-001",
                aliceEntities,
                bobEntities,
                confidences,
                weights,
                dummyVector
        );
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(result);
        assertTrue(elapsed <= 1000, "端到端联邦协同调度耗时必须 <= 1000ms，实际耗时=" + elapsed + "ms");
        assertTrue(result.getTotalDurationMs() <= 1000);
    }
}
