package tech.qiantong.qknow.hermes.agent.swarm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.swarm.dto.ContextSliceBO;
import tech.qiantong.qknow.hermes.agent.swarm.dto.MultiAgentDebateReceipt;
import tech.qiantong.qknow.hermes.agent.swarm.engine.MoAExpertConsensusEngine;
import tech.qiantong.qknow.hermes.agent.swarm.engine.StructuredDebateCoordinator;
import tech.qiantong.qknow.hermes.agent.swarm.engine.SwarmDynamicHandoffHub;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MultiAgentDebateIntegrationTest 多智能体协同编排全链路集成测试")
class MultiAgentDebateIntegrationTest {

    private SwarmDynamicHandoffHub handoffHub;
    private StructuredDebateCoordinator debateCoordinator;
    private MoAExpertConsensusEngine consensusEngine;

    @BeforeEach
    void setUp() {
        handoffHub = new SwarmDynamicHandoffHub();
        debateCoordinator = new StructuredDebateCoordinator();
        consensusEngine = new MoAExpertConsensusEngine();
    }

    private float[] createHypersphereVector(double angleRad) {
        float[] v = new float[1536];
        v[0] = (float) Math.cos(angleRad);
        v[1] = (float) Math.sin(angleRad);
        return v;
    }

    @Test
    @DisplayName("端到端闭环验证：Swarm 动态交接 -> 结构化对抗辩论 -> MoA 专家混合委员会客观验真")
    void fullEndToEndOrchestration_success() {
        String sessionId = "session-e2e-enterprise-001";
        long e2eStartTime = System.currentTimeMillis();

        // 阶段一：Swarm 动态去中心化上下文移交 (CustomerCare -> ComplianceAudit -> DebateOrchestrator)
        ContextSliceBO initialSlice = new ContextSliceBO(
                "审查跨国合资企业竞业限制与知识产权交叉许可方案",
                Map.of("dealValue", "50000000USD", "jurisdiction", "SG-CN"),
                "移交法务与风控进行争议对抗与深度合规审查",
                System.currentTimeMillis()
        );

        var handoff1 = handoffHub.transferToAgent(sessionId, "CustomerCareAgent", "ComplianceAuditAgent", initialSlice);
        assertTrue(handoff1.authorized());
        assertEquals(1, handoff1.currentDepth());

        ContextSliceBO auditSlice = new ContextSliceBO(
                "合规争议审查：锁定条款与违约赔偿存在重大分歧",
                Map.of("dealValue", "50000000USD", "disputedClause", "NonCompetePeriod"),
                "发起多智能体结构化对抗辩论",
                System.currentTimeMillis()
        );

        var handoff2 = handoffHub.transferToAgent(sessionId, "ComplianceAuditAgent", "DebateOrchestratorAgent", auditSlice);
        assertTrue(handoff2.authorized());
        assertEquals(2, handoff2.currentDepth());

        // 阶段二：结构化对抗辩论 (Proponent vs Opponent -> Judge Arbitrate)
        StructuredDebateCoordinator.AgentDebateParticipant proponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "CommercialProponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) {
                return "商业诉求：基于高额交易对价，竞业限制期限必须达到法定上限 24 个月，以保护核心研发资产。";
            }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) { return ""; }
            @Override
            public float[] getArgumentEmbedding(String text) {
                return createHypersphereVector(0.0);
            }
        };

        StructuredDebateCoordinator.AgentDebateParticipant opponent = new StructuredDebateCoordinator.AgentDebateParticipant() {
            @Override
            public String getAgentId() { return "RiskOpponent"; }
            @Override
            public String generateArgument(String topic, List<String> history, String oppLast) { return ""; }
            @Override
            public String generateRebuttal(String topic, List<String> history, String propLast) {
                return "风控抗辩：24 个月期限在司法实践中极易被认定为显失公平或过度限制劳动者自由，建议收敛至 12 个月并提高补偿金。";
            }
            @Override
            public float[] getArgumentEmbedding(String text) {
                return createHypersphereVector(0.7); // 散度约 0.22 > 0.15，保持两轮交锋
            }
        };

        StructuredDebateCoordinator.AgentDebateJudge judge = (topic, transcript) ->
                new StructuredDebateCoordinator.JudgeVerdictBO(
                        "仲裁方案：采纳折中 18 个月竞业禁止期，补偿金标准上调至离职前平均薪酬的 50%，设立季度履约申报",
                        0.95,
                        true
                );

        MultiAgentDebateReceipt debateReceipt = debateCoordinator.coordinateDebate(
                "debate-" + sessionId,
                "跨境合资公司竞业限制期限争议",
                proponent,
                opponent,
                judge,
                2
        );

        assertNotNull(debateReceipt);
        assertEquals(2, debateReceipt.totalRounds());
        assertTrue(debateReceipt.verifyIntegrity(), "辩论存证凭单签名验真必须通过");
        assertTrue(debateReceipt.finalVerdict().contains("18 个月"));

        // 阶段三：MoA 专家混合委员会最终交叉评审与独立客观事实检索验真
        MoAExpertConsensusEngine.MoAExpert laborLawExpert = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "LaborLawExpert"; }
            @Override
            public String getDomain() { return "LaborLaw"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "劳动法复核：18 个月竞业期限符合《劳动合同法》第二十四条第二款未超过二年之强行法规定。";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.02);
            }
        };

        MoAExpertConsensusEngine.MoAExpert taxFinancialExpert = new MoAExpertConsensusEngine.MoAExpert() {
            @Override
            public String getExpertId() { return "TaxAuditExpert"; }
            @Override
            public String getDomain() { return "TaxAudit"; }
            @Override
            public String generateResponse(String taskPrompt) {
                return "税务审计复核：50% 离职补偿金应按企业离职补偿金专项扣除与个税申报政策执行，合规性确认。";
            }
            @Override
            public float[] getEmbedding(String text) {
                return createHypersphereVector(0.04);
            }
        };

        // 权威独立事实检索库检索真实法规依据
        MoAExpertConsensusEngine.FactRetrievalService statutoryFactService = new MoAExpertConsensusEngine.FactRetrievalService() {
            @Override
            public String retrieveGroundTruth(String taskPrompt, String claimsSummary) {
                return "法规条文库依据：《中华人民共和国劳动合同法》第24条：在解除或者终止劳动合同后，前款规定的人员到与本单位生产或者经营同类产品、从事同类业务的有竞争关系的其他用人单位，或者自己开业生产或者经营同类产品、从事同类业务的竞业限制期限，不得超过二年。";
            }
            @Override
            public float[] getEmbedding(String text) {
                // 与专家断言高度对齐，角度 0.05 弧度
                return createHypersphereVector(0.05);
            }
        };

        var moaResult = consensusEngine.synthesizeConsensus(
                "moa-" + sessionId,
                "仲裁方案法律与财务客观合规性闭环复核",
                List.of(laborLawExpert, taxFinancialExpert),
                statutoryFactService
        );

        assertNotNull(moaResult);
        assertTrue(moaResult.consensusApproved(), "端到端经过独立事实校验，共识成功批准");
        assertEquals("APPROVED", moaResult.verdictStatus());
        assertTrue(moaResult.geodesicDivergenceToFact() <= 0.45);

        long totalElapsed = System.currentTimeMillis() - e2eStartTime;
        assertTrue(totalElapsed < 2000, "端到端协同全流程应具备高效吞吐，实测总耗时: " + totalElapsed + "ms");
    }
}
