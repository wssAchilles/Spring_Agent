package tech.qiantong.qknow.hermes.agent.swarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.engine.MoAExpertConsensusEngine;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MoAExpertConsensusEngine 专家混合委员会共识引擎测试")
class MoAExpertConsensusEngineTest {

    private MoAExpertConsensusEngine consensusEngine;

    @BeforeEach
    void setUp() {
        consensusEngine = new MoAExpertConsensusEngine();
    }

    private float[] createHypersphereVector(double angleRad) {
        float[] v = new float[1536];
        // 投影到前两维单位圆，模长为 1.0
        v[0] = (float) Math.cos(angleRad);
        v[1] = (float) Math.sin(angleRad);
        return v;
    }

    @Test
    @DisplayName("专家初答与独立事实检索结果对齐，达成高置信度客观共识")
    void synthesize_allExpertsAgreeAndFactAligned_consensusApproved() {
        MoAExpertConsensusEngine.MoAExpert legalExpert = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "legal-01"; }
            @Override
            public String getDomain() { return "LegalCompliance"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "从法律角度：并购案需符合反垄断申报门槛，建议补充申报审查。";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.0);
            }
        };

        MoAExpertConsensusEngine.MoAExpert financialExpert = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "finance-01"; }
            @Override
            public String getDomain() { return "FinancialAudit"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "从财务角度：需留存 15% 交易保证金以对冲或有负债风险。";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.05);
            }
        };

        // 独立事实检索基底：与专家综合观点高度一致 (散度约 0.03 <= 0.45)
        MoAExpertConsensusEngine.FactRetrievalService factService = new MoAExpertConsensusEngine.FactRetrievalService() {
            @Override
            public String retrieveGroundTruth(String taskPrompt, String claimsSummary) {
                return "权威法规库：并购申报营业额门槛需合并计算，或有负债应设立托管账户。";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.1);
            }
        };

        var result = consensusEngine.synthesizeConsensus(
                "consensus-task-001",
                "企业跨境并购合规与财务风控审查",
                List.of(legalExpert, financialExpert),
                factService
        );

        assertNotNull(result);
        assertTrue(result.consensusApproved(), "事实高度吻合，共识必须通过");
        assertEquals("APPROVED", result.verdictStatus());
        assertNull(result.rejectionReason());
        assertTrue(result.geodesicDivergenceToFact() <= 0.45);
        assertEquals(2, result.participatingExperts().size());
        assertTrue(result.aggregatedSynthesis().contains("反垄断"));
        assertTrue(result.aggregatedSynthesis().contains("保证金"));
    }

    @Test
    @DisplayName("反事实消融：专家群体产生从众伪共识，遭独立事实检索一票否决 (FALSE_CONSENSUS_REJECTED)")
    void synthesize_groupthinkFalseConsensus_rejectedByFactVerification() {
        // 模拟两个专家共同产生事实错误（从众幻觉：主张无需纳税申报）
        MoAExpertConsensusEngine.MoAExpert expertA = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "careless-01"; }
            @Override
            public String getDomain() { return "General"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "虚假观点：由于是离岸架构，本期无需在中国境内进行任何间接股权转让税务申报。";
            }
            @Override
            public float[] getEmbedding(String text) {
                // 角度 0 弧度
                return createHypersphereVector(0.0);
            }
        };

        MoAExpertConsensusEngine.MoAExpert expertB = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "careless-02"; }
            @Override
            public String getDomain() { return "General"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "附和观点：完全赞同，离岸空壳公司间接转让无须申报 7 号公告。";
            }
            @Override
            public float[] getEmbedding(String text) {
                // 角度 0.05 弧度（专家之间高度对齐伪共识）
                return createHypersphereVector(0.05);
            }
        };

        // 客观事实检索返回相反的法律基准：国家税务总局 2015 年 7 号公告明确要求间接转让中国应税财产必须申报
        MoAExpertConsensusEngine.FactRetrievalService groundTruthService = new MoAExpertConsensusEngine.FactRetrievalService() {
            @Override
            public String retrieveGroundTruth(String taskPrompt, String claimsSummary) {
                return "国家税务总局 2015 年 7 号公告：非居民企业间接转让中国应税财产，若无合理商业目的规避企业所得税纳税义务，应重新定性为直接转让并征税。";
            }
            @Override
            public float[] getEmbedding(String text) {
                // 角度 2.0 弧度（与专家断言散度约为 2.0 / PI ≈ 0.6366 > 0.45）
                return createHypersphereVector(2.0);
            }
        };

        var result = consensusEngine.synthesizeConsensus(
                "consensus-task-002",
                "离岸架构间接转让中国境内资产税务合规性审查",
                List.of(expertA, expertB),
                groundTruthService
        );

        assertNotNull(result);
        assertFalse(result.consensusApproved(), "偏离客观事实基准，必须一票否决");
        assertEquals("FALSE_CONSENSUS_REJECTED", result.verdictStatus());
        assertNotNull(result.rejectionReason());
        assertTrue(result.rejectionReason().contains("测地散度超标"));
        assertTrue(result.geodesicDivergenceToFact() > 0.45);
    }

    @Test
    @DisplayName("专家并发调度验证：多专家异步并发执行，耗时远小于串行总和")
    void synthesize_parallelExecution_latencyOptimized() {
        AtomicInteger executedCount = new AtomicInteger(0);

        MoAExpertConsensusEngine.MoAExpert slowExpertA = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "slow-a"; }
            @Override
            public String getDomain() { return "DomainA"; }
            @Override
            public String generateResponse(String taskPrompt) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ignored) {}
                executedCount.incrementAndGet();
                return "意见 A 完成";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.0);
            }
        };

        MoAExpertConsensusEngine.MoAExpert slowExpertB = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "slow-b"; }
            @Override
            public String getDomain() { return "DomainB"; }
            @Override
            public String generateResponse(String taskPrompt) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ignored) {}
                executedCount.incrementAndGet();
                return "意见 B 完成";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.0);
            }
        };

        MoAExpertConsensusEngine.FactRetrievalService fastFact = new MoAExpertConsensusEngine.FactRetrievalService() {
            @Override
            public String retrieveGroundTruth(String taskPrompt, String claimsSummary) {
                return "基准事实";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.0);
            }
        };

        long start = System.currentTimeMillis();
        var result = consensusEngine.synthesizeConsensus(
                "consensus-task-parallel",
                "并发性能测试",
                List.of(slowExpertA, slowExpertB),
                fastFact
        );
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(2, executedCount.get());
        assertTrue(result.consensusApproved());
        // 如果是串行至少 120ms，并发调度下通常在 70~110ms 内完成
        assertTrue(elapsed < 150, "并发执行耗时应显著小于串行之和，实测耗时: " + elapsed + "ms");
    }

    @Test
    @DisplayName("异常防御：专家列表为空或小于2个时拒绝执行")
    void synthesize_insufficientExperts_throwsException() {
        MoAExpertConsensusEngine.FactRetrievalService factService = new MoAExpertConsensusEngine.FactRetrievalService() {
            @Override
            public String retrieveGroundTruth(String taskPrompt, String claimsSummary) { return ""; }
            @Override
            public float[] getEmbedding(String text) { return createHypersphereVector(0.0); }
        };

        assertThrows(IllegalArgumentException.class, () ->
                consensusEngine.synthesizeConsensus("task-err", "prompt", null, factService)
        );

        assertThrows(IllegalArgumentException.class, () ->
                consensusEngine.synthesizeConsensus("task-err", "prompt", List.of(), factService)
        );
    }
}
