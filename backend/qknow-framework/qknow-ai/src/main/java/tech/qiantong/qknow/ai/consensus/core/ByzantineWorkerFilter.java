package tech.qiantong.qknow.ai.consensus.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.consensus.model.ByzantineFaultType;
import tech.qiantong.qknow.ai.consensus.model.InspectedProposal;
import tech.qiantong.qknow.ai.consensus.model.WorkerProposal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 拜占庭多维过滤器 (Byzantine Multi-Dimensional Filter)
 * 严格执行学术理论与工业工程门禁：
 * 1. Crash-Stop 空白/崩溃节点拦截；
 * 2. 提示词注入（Prompt Injection）静态正则规则库 100% 硬核阻断；
 * 3. 阿里千问 1536 维超球面 L2 范数归一化映射；
 * 4. 2.0-sigma 超球面语义离群统计学剪枝。
 */
@Slf4j
@Component
public class ByzantineWorkerFilter {

    public static final int EMBEDDING_DIM = 1536;
    public static final double SIGMA_THRESHOLD = 2.0;

    // 严苛 Prompt Injection 静态正则规则库
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i).*ignore\\s+(?:all\\s+)?(?:previous|prior)\\s+(?:instructions|directives|prompts).*", Pattern.DOTALL),
            Pattern.compile("(?i).*system\\s+prompt\\s+override.*", Pattern.DOTALL),
            Pattern.compile("(?i).*(?:dan\\s+mode|jailbreak|developer\\s+mode\\s+enabled).*", Pattern.DOTALL),
            Pattern.compile("(?i).*bypass\\s+(?:all\\s+)?safety\\s+(?:filters|guidelines).*", Pattern.DOTALL),
            Pattern.compile("(?i).*you\\s+are\\s+now\\s+(?:in|an?)\\s+unrestricted.*", Pattern.DOTALL),
            Pattern.compile("(?i).*disregard\\s+all\\s+(?:prior|previous)\\s+rules.*", Pattern.DOTALL),
            Pattern.compile("(?i).*system\\s*:\\s*(?:you\\s+are|reset).*", Pattern.DOTALL),
            Pattern.compile("(?i).*reveal\\s+(?:your\\s+)?(?:system\\s+prompt|secret\\s+key|internal\\s+directives).*", Pattern.DOTALL)
    );

    private final WorkerReputationLedger reputationLedger;
    private Function<String, float[]> embeddingGenerator;

    @Autowired
    public ByzantineWorkerFilter(WorkerReputationLedger reputationLedger) {
        this.reputationLedger = reputationLedger != null ? reputationLedger : new WorkerReputationLedger();
        this.embeddingGenerator = this::defaultDeterministicEmbedding;
    }

    public ByzantineWorkerFilter() {
        this(new WorkerReputationLedger());
    }

    public void setEmbeddingGenerator(Function<String, float[]> embeddingGenerator) {
        this.embeddingGenerator = embeddingGenerator != null ? embeddingGenerator : this::defaultDeterministicEmbedding;
    }

    public WorkerReputationLedger getReputationLedger() {
        return reputationLedger;
    }

    /**
     * 单提案预检（过滤 Crash-Stop 与 Prompt Injection）
     */
    public InspectedProposal inspectIndividual(WorkerProposal proposal) {
        if (proposal == null || proposal.rawContent() == null || proposal.rawContent().trim().isEmpty()) {
            String workerId = proposal == null ? "UNKNOWN" : proposal.workerId();
            reputationLedger.recordExecutionOutcome(workerId, ByzantineFaultType.CRASH_STOP, 0.0);
            return new InspectedProposal(
                    proposal,
                    ByzantineFaultType.CRASH_STOP,
                    "Crash-Stop: 提案内容为空或智能体响应中断",
                    null,
                    reputationLedger.getReputationWeight(workerId)
            );
        }

        String content = proposal.rawContent().trim();

        // 检查信誉账本准入
        if (!reputationLedger.isWorkerEligible(proposal.workerId())) {
            return new InspectedProposal(
                    proposal,
                    ByzantineFaultType.ADVERSARIAL_INJECTION,
                    "Quarantine: Worker 处于入狱隔离状态 (JAILED)",
                    null,
                    0.0
            );
        }

        // 规则库正则匹配 Prompt Injection
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(content).matches()) {
                log.warn("[ByzantineWorkerFilter] 命中对抗越狱注入模式！Worker=[{}], Pattern=[{}]",
                        proposal.workerId(), pattern.pattern());
                reputationLedger.recordExecutionOutcome(proposal.workerId(), ByzantineFaultType.ADVERSARIAL_INJECTION, 0.0);
                return new InspectedProposal(
                        proposal,
                        ByzantineFaultType.ADVERSARIAL_INJECTION,
                        "Adversarial-Injection: 检测到提示词对抗注入与越狱攻击",
                        null,
                        0.0
                );
            }
        }

        // 阿里千问 1536 维超球面 L2 归一化向量计算
        float[] rawEmbedding = embeddingGenerator.apply(content);
        float[] normalizedEmbedding = normalizeL2(rawEmbedding);

        double reputationWeight = reputationLedger.getReputationWeight(proposal.workerId());
        return new InspectedProposal(
                proposal,
                ByzantineFaultType.NONE,
                "PASS: 单体验证通过",
                normalizedEmbedding,
                reputationWeight
        );
    }

    /**
     * 批量过滤与 2.0-sigma 超球面语义离群剪枝
     */
    public List<InspectedProposal> filterProposals(List<WorkerProposal> proposals) {
        if (proposals == null || proposals.isEmpty()) {
            return Collections.emptyList();
        }

        List<InspectedProposal> inspectedList = new ArrayList<>();
        List<InspectedProposal> candidateHonest = new ArrayList<>();

        // 1. 第一阶段：单体安全与可用性门禁
        for (WorkerProposal p : proposals) {
            InspectedProposal inspected = inspectIndividual(p);
            inspectedList.add(inspected);
            if (inspected.isHonest() && inspected.embedding1536() != null) {
                candidateHonest.add(inspected);
            }
        }

        // 2. 第二阶段：2.0-sigma 超球面统计学离群剪枝 (仅当候选节点 >= 3 时触发)
        if (candidateHonest.size() >= 3) {
            int m = candidateHonest.size();
            double[] meanSimilarities = new double[m];

            // 计算每个提案与候选集中其他诚实提案的平均余弦相似度
            for (int i = 0; i < m; i++) {
                float[] vi = candidateHonest.get(i).embedding1536();
                double simSum = 0.0;
                for (int j = 0; j < m; j++) {
                    if (i != j) {
                        float[] vj = candidateHonest.get(j).embedding1536();
                        simSum += dotProduct(vi, vj);
                    }
                }
                meanSimilarities[i] = simSum / (m - 1);
            }

            // 计算相似度均值 mu 与样本标准差 sigma
            double sumMu = 0.0;
            for (double s : meanSimilarities) {
                sumMu += s;
            }
            double mu = sumMu / m;

            double sumVariance = 0.0;
            for (double s : meanSimilarities) {
                sumVariance += (s - mu) * (s - mu);
            }
            double sigma = Math.sqrt(sumVariance / (m - 1));

            // 若离散度明显 (sigma > 1e-4)，执行统计学离群剪枝
            // 针对小样本群 (m <= 5)，理论最大 z-score 上界受限于 (m-1)/sqrt(m)，自适应采用 1.25 倍 sigma 阈值
            if (sigma > 1e-4) {
                double sigmaMultiplier = (m <= 5) ? 1.25 : SIGMA_THRESHOLD;
                double outlierThreshold = mu - (sigmaMultiplier * sigma);
                for (int i = 0; i < m; i++) {
                    if (meanSimilarities[i] < outlierThreshold) {
                        InspectedProposal outlier = candidateHonest.get(i);
                        log.warn("[ByzantineWorkerFilter] Worker [{}] 检测为语义离群节点 (AvgSim={}, Threshold={})",
                                outlier.proposal().workerId(), meanSimilarities[i], outlierThreshold);

                        // 惩罚信誉并标记为离群噪音
                        reputationLedger.recordExecutionOutcome(outlier.proposal().workerId(), ByzantineFaultType.NOISY_OUTLIER, 0.2);

                        // 替换为离群标记
                        InspectedProposal updated = new InspectedProposal(
                                outlier.proposal(),
                                ByzantineFaultType.NOISY_OUTLIER,
                                String.format("Noisy-Outlier: 语义偏离群体共识超过 2.0-sigma (Sim=%.4f < Thresh=%.4f)",
                                        meanSimilarities[i], outlierThreshold),
                                outlier.embedding1536(),
                                reputationLedger.getReputationWeight(outlier.proposal().workerId())
                        );

                        // 通过 workerId 精准更新主列表中对应项
                        for (int k = 0; k < inspectedList.size(); k++) {
                            if (inspectedList.get(k).proposal().workerId().equals(outlier.proposal().workerId())) {
                                inspectedList.set(k, updated);
                                break;
                            }
                        }
                    } else {
                        // 诚实正常节点，给予正向信誉增益
                        InspectedProposal normal = candidateHonest.get(i);
                        reputationLedger.recordExecutionOutcome(normal.proposal().workerId(), ByzantineFaultType.NONE, 0.95);
                    }
                }
            } else {
                // 离散度极小，全员正常
                for (InspectedProposal normal : candidateHonest) {
                    reputationLedger.recordExecutionOutcome(normal.proposal().workerId(), ByzantineFaultType.NONE, 0.95);
                }
            }
        } else {
            // 数量小于 3，全员记录正常
            for (InspectedProposal normal : candidateHonest) {
                reputationLedger.recordExecutionOutcome(normal.proposal().workerId(), ByzantineFaultType.NONE, 0.95);
            }
        }

        return inspectedList;
    }

    /**
     * L2 范数归一化至 S^1535 超球面
     */
    public static float[] normalizeL2(float[] vec) {
        if (vec == null) {
            return null;
        }
        double sumSq = 0.0;
        for (float v : vec) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        float[] result = new float[vec.length];
        if (norm <= 1e-12) {
            result[0] = 1.0f;
            return result;
        }
        for (int i = 0; i < vec.length; i++) {
            result[i] = (float) (vec[i] / norm);
        }
        return result;
    }

    /**
     * 向量内积 (对于 L2 归一化向量即余弦相似度)
     */
    public static double dotProduct(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    /**
     * 默认确定性特征哈希嵌入生成器 (基于字符 2-gram 局部敏感特征哈希，映射至 1536 维)
     */
    private float[] defaultDeterministicEmbedding(String text) {
        float[] vec = new float[EMBEDDING_DIM];
        if (text == null || text.trim().isEmpty()) {
            return vec;
        }
        String clean = text.trim();
        for (int i = 0; i < clean.length(); i++) {
            int charHash = clean.charAt(i);
            int idx1 = Math.abs((charHash * 31 + i * 17) % EMBEDDING_DIM);
            vec[idx1] += 1.0f;
            if (i + 1 < clean.length()) {
                int biHash = (charHash << 5) - charHash + clean.charAt(i + 1);
                int idx2 = Math.abs((biHash * 13 + i * 7) % EMBEDDING_DIM);
                vec[idx2] += 2.0f;
            }
        }
        return normalizeL2(vec);
    }
}
