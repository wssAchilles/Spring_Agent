package tech.qiantong.qknow.hermes.agent.swarm.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MoA 专家混合委员会共识调度器 (MoAExpertConsensusEngine)
 * 协调多个异构领域专家异步并发生成专业初答分片
 * 施加独立事实 RAG 检索与阿里千问 1536 维超球面测地散度双重验真
 * 具备一票否决专家群体从众伪共识能力 (FALSE_CONSENSUS_REJECTED)
 */
@Slf4j
@Component
public class MoAExpertConsensusEngine {

    public static final double GEODESIC_FALSE_CONSENSUS_THRESHOLD = 0.45; // 测地散度大于 0.45 一票否决
    public static final long DEFAULT_ASYNC_TIMEOUT_MS = 5000L;

    /**
     * 协调专家委员会并发初答、独立事实检索与测地验真综合共识
     */
    public MoAConsensusResultBO synthesizeConsensus(
            String consensusId,
            String taskPrompt,
            List<MoAExpert> experts,
            FactRetrievalService factRetrievalService) {

        long startTime = System.currentTimeMillis();

        if (consensusId == null || taskPrompt == null) {
            throw new IllegalArgumentException("ConsensusId and taskPrompt must not be null");
        }
        if (experts == null || experts.size() < 2) {
            throw new IllegalArgumentException("MoA 专家混合委员会至少需要 2 位独立专家参与决策");
        }
        if (factRetrievalService == null) {
            throw new IllegalArgumentException("必须提供独立事实检索服务以实施客观基准验真");
        }

        log.info("[MoA] 启动专家混合委员会共识流程: consensusId={}, 专家数={}", consensusId, experts.size());

        // 1. 异步并发收集专家初答与向量表征
        List<CompletableFuture<ExpertResponseBO>> futures = experts.stream()
                .map(expert -> CompletableFuture.supplyAsync(() -> {
                    long expertStart = System.currentTimeMillis();
                    String response = expert.generateResponse(taskPrompt);
                    float[] embedding = expert.getEmbedding(response);
                    return new ExpertResponseBO(
                            expert.getExpertId(),
                            expert.getDomain(),
                            response,
                            embedding,
                            System.currentTimeMillis() - expertStart
                    );
                }))
                .toList();

        List<ExpertResponseBO> responses;
        try {
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.get(DEFAULT_ASYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            responses = futures.stream().map(CompletableFuture::join).toList();
        } catch (Exception e) {
            log.error("[MoA] 专家异步初答超时或异常: {}", e.getMessage(), e);
            throw new IllegalStateException("专家异步初答执行失败: " + e.getMessage(), e);
        }

        List<String> expertIds = responses.stream().map(ExpertResponseBO::expertId).toList();

        // 2. 结构化综合意见汇聚 (Synthesis)
        StringBuilder synthesisBuilder = new StringBuilder();
        synthesisBuilder.append("【MoA 专家混合委员会综合意见】\n");
        for (ExpertResponseBO resp : responses) {
            synthesisBuilder.append(String.format("• [%s - %s]: %s\n", resp.domain(), resp.expertId(), resp.content()));
        }
        String aggregatedSynthesis = synthesisBuilder.toString().trim();

        // 3. 计算专家群体在阿里千问 1536 维超球面上的合成质心单位向量
        float[] centroidVector = computeNormalizedCentroid(responses.stream().map(ExpertResponseBO::embedding).toList());

        // 4. 独立事实 RAG 检索校验
        log.debug("[MoA] 调度独立事实检索服务验证专家综合断言...");
        String groundTruthFact = factRetrievalService.retrieveGroundTruth(taskPrompt, aggregatedSynthesis);
        float[] factVector = factRetrievalService.getEmbedding(groundTruthFact);

        // 5. 阿里千问 1536 维超球面测地大圆弧散度研判
        double geodesicDivergence = computeGeodesicDivergence(centroidVector, factVector);
        log.info("[MoA] 专家断言与客观事实基底测地散度计算: divergence={}, 判定门限={}",
                geodesicDivergence, GEODESIC_FALSE_CONSENSUS_THRESHOLD);

        long latencyMs = System.currentTimeMillis() - startTime;

        // 6. 一票否决防线：若测地距离超标，判定为从众伪共识 (FALSE_CONSENSUS_REJECTED)
        if (geodesicDivergence > GEODESIC_FALSE_CONSENSUS_THRESHOLD) {
            String rejectMsg = String.format("综合专家断言与客观事实基底测地散度超标 (%.4f > %.2f)，判定为从众伪共识，触发一票否决",
                    geodesicDivergence, GEODESIC_FALSE_CONSENSUS_THRESHOLD);
            log.warn("[MoA] 会话 {} 遭一票否决: {}", consensusId, rejectMsg);
            return new MoAConsensusResultBO(
                    consensusId,
                    false,
                    aggregatedSynthesis,
                    geodesicDivergence,
                    "FALSE_CONSENSUS_REJECTED",
                    rejectMsg,
                    expertIds,
                    latencyMs
            );
        }

        // 7. 事实检验通过，共识达成
        log.info("[MoA] 会话 {} 顺利达成高置信度客观共识 (耗时: {}ms)", consensusId, latencyMs);
        return new MoAConsensusResultBO(
                consensusId,
                true,
                aggregatedSynthesis,
                geodesicDivergence,
                "APPROVED",
                null,
                expertIds,
                latencyMs
        );
    }

    /**
     * 计算多向量合成质心并归一化为千问 1536 维超球面单位向量
     */
    private float[] computeNormalizedCentroid(List<float[]> embeddings) {
        float[] centroid = new float[1536];
        int count = 0;
        for (float[] emb : embeddings) {
            if (emb != null && emb.length == 1536) {
                count++;
                for (int i = 0; i < 1536; i++) {
                    centroid[i] += emb[i];
                }
            }
        }
        if (count == 0) {
            centroid[0] = 1.0f; // 默认单位基向量
            return centroid;
        }
        for (int i = 0; i < 1536; i++) {
            centroid[i] /= count;
        }
        // 归一化到 ||v|| = 1.0
        double normSq = 0.0;
        for (int i = 0; i < 1536; i++) {
            normSq += (double) centroid[i] * centroid[i];
        }
        double norm = Math.sqrt(normSq);
        if (norm > 1e-8) {
            for (int i = 0; i < 1536; i++) {
                centroid[i] = (float) (centroid[i] / norm);
            }
        } else {
            centroid[0] = 1.0f;
        }
        return centroid;
    }

    /**
     * 计算阿里千问 1536 维超球面测地大圆弧散度 [0.0, 1.0]
     * d_g = arccos(clamp(u · v, -1.0, 1.0)) / PI
     */
    private double computeGeodesicDivergence(float[] u, float[] v) {
        if (u == null || v == null || u.length != 1536 || v.length != 1536) {
            return 1.0;
        }
        double dot = 0.0;
        for (int i = 0; i < 1536; i++) {
            dot += (double) u[i] * v[i];
        }
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double geodAngle = Math.acos(dot);
        return geodAngle / Math.PI;
    }

    public interface MoAExpert {
        String getExpertId();
        String getDomain();
        String generateResponse(String taskPrompt);
        float[] getEmbedding(String text);
    }

    public interface FactRetrievalService {
        String retrieveGroundTruth(String taskPrompt, String claimsSummary);
        float[] getEmbedding(String text);
    }

    private record ExpertResponseBO(
            String expertId,
            String domain,
            String content,
            float[] embedding,
            long latencyMs
    ) {}

    public record MoAConsensusResultBO(
            String consensusId,
            boolean consensusApproved,
            String aggregatedSynthesis,
            double geodesicDivergenceToFact,
            String verdictStatus,
            String rejectionReason,
            List<String> participatingExperts,
            long latencyMs
    ) {}
}
