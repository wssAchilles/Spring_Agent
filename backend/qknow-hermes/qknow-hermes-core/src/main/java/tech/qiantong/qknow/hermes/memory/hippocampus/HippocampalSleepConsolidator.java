package tech.qiantong.qknow.hermes.memory.hippocampus;

import tech.qiantong.qknow.hermes.memory.bitemporal.MultiClusterSleepConsolidationReceipt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 海马体离线“睡眠期”图谱沉淀整合器 (Hippocampal Sleep Consolidator)
 * 模拟大脑皮层慢波睡眠时期的经验整合与突触修剪机制：
 * 1. 抽取当前在线缓冲区的全部情景经验快照；
 * 2. 执行超球面质心均值漂移 (Spherical Mean-Shift) 聚类，计算单位归一化质心；
 * 3. 统计共现高频事实，归纳提取跨会话持久不变量法则 (Invariants)；
 * 4. 签发带密码学签名的不可变图谱沉淀收据。
 */
public class HippocampalSleepConsolidator {

    private final String secretKey;
    private final Map<String, List<SleepConsolidationReceipt>> consolidationHistory = new ConcurrentHashMap<>();

    public HippocampalSleepConsolidator() {
        this("DEFAULT_SLEEP_CONSOLIDATION_KEY");
    }

    public HippocampalSleepConsolidator(String secretKey) {
        this.secretKey = secretKey == null ? "DEFAULT_SLEEP_KEY" : secretKey;
    }

    /**
     * 对指定会话的情境记录执行离线睡眠整合
     *
     * @param sessionId 会话唯一标识
     * @param turns     当前会话提取出的情景记录列表
     * @return SleepConsolidationReceipt 整合凭单
     */
    public SleepConsolidationReceipt consolidateSession(String sessionId, List<EpisodicTurnRecord> turns) {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        long startNanos = System.nanoTime();
        long timestamp = System.currentTimeMillis();
        String consolidationId = "sleep_csl_" + UUID.randomUUID().toString().replace("-", "");

        if (turns == null || turns.isEmpty()) {
            float[] zeroCentroid = new float[EpisodicTurnRecord.VECTOR_DIMENSION];
            long durationMicros = (System.nanoTime() - startNanos) / 1000;
            String sig = SleepConsolidationReceipt.calculateSignature(
                    consolidationId, sessionId, 0, 0, durationMicros, timestamp, secretKey);
            SleepConsolidationReceipt emptyReceipt = new SleepConsolidationReceipt(
                    consolidationId, sessionId, 0, 0, Collections.emptyList(), zeroCentroid,
                    durationMicros, timestamp, sig);
            recordReceipt(sessionId, emptyReceipt);
            return emptyReceipt;
        }

        // 步骤 1：超球面质心均值漂移聚类计算
        double[] sumVector = new double[EpisodicTurnRecord.VECTOR_DIMENSION];
        for (EpisodicTurnRecord turn : turns) {
            float[] vec = turn.sphericalVector();
            for (int d = 0; d < EpisodicTurnRecord.VECTOR_DIMENSION; d++) {
                sumVector[d] += vec[d];
            }
        }

        double normSq = 0.0;
        for (double val : sumVector) {
            normSq += val * val;
        }
        double norm = Math.sqrt(normSq);

        float[] centroid = new float[EpisodicTurnRecord.VECTOR_DIMENSION];
        if (norm > 1e-9) {
            for (int d = 0; d < EpisodicTurnRecord.VECTOR_DIMENSION; d++) {
                centroid[d] = (float) (sumVector[d] / norm);
            }
        }

        // 步骤 2：共现高频事实归纳提炼不变量法则 (Invariants)
        Map<String, Map<String, Integer>> factFrequencies = new HashMap<>();
        for (EpisodicTurnRecord turn : turns) {
            Map<String, String> facts = turn.keyFacts();
            if (facts != null) {
                for (Map.Entry<String, String> entry : facts.entrySet()) {
                    factFrequencies
                            .computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                            .merge(entry.getValue(), 1, Integer::sum);
                }
            }
        }

        List<String> invariantRules = new ArrayList<>();
        // 对事实按键字典序排序，保证确定性
        List<String> sortedKeys = new ArrayList<>(factFrequencies.keySet());
        Collections.sort(sortedKeys);

        for (String key : sortedKeys) {
            Map<String, Integer> valCounts = factFrequencies.get(key);
            String dominantVal = null;
            int maxCount = 0;
            for (Map.Entry<String, Integer> valEntry : valCounts.entrySet()) {
                if (valEntry.getValue() > maxCount) {
                    maxCount = valEntry.getValue();
                    dominantVal = valEntry.getKey();
                }
            }
            if (dominantVal != null) {
                // 生成法则契约字符串
                invariantRules.add(String.format("INVARIANT[%s=%s; occurrences=%d/%d]",
                        key, dominantVal, maxCount, turns.size()));
            }
        }

        long durationMicros = Math.max(1, (System.nanoTime() - startNanos) / 1000);
        String sig = SleepConsolidationReceipt.calculateSignature(
                consolidationId, sessionId, turns.size(), invariantRules.size(),
                durationMicros, timestamp, secretKey);

        SleepConsolidationReceipt receipt = new SleepConsolidationReceipt(
                consolidationId, sessionId, turns.size(), invariantRules.size(),
                invariantRules, centroid, durationMicros, timestamp, sig);

        recordReceipt(sessionId, receipt);
        return receipt;
    }

    private void recordReceipt(String sessionId, SleepConsolidationReceipt receipt) {
        consolidationHistory.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>())).add(receipt);
    }

    public List<SleepConsolidationReceipt> getHistory(String sessionId) {
        return consolidationHistory.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * S^1023 多簇球面 k-Means 谱整合与 Wiki / Error-Book 编译（博士论文第 4 章定理 4.1）。
     * <p>
     * 消除旧版将全部异构话题坍缩为单一全局均值质心的失真缺陷，严格计算每簇合成长度 R_k、
     * 精确平方弦重构失真 2*(1 - R_k) 与本征测地 Fréchet 风险球冠紧上界。
     * </p>
     */
    public MultiClusterSleepConsolidationReceipt consolidateMultiClusterWithWiki(
            String sessionId,
            List<EpisodicTurnRecord> turns,
            int maxClusters
    ) {
        Objects.requireNonNull(sessionId, "sessionId 不能为空");
        long startNs = System.nanoTime();
        String cid = "mc_sleep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (turns == null || turns.isEmpty()) {
            return new MultiClusterSleepConsolidationReceipt(cid, sessionId, List.of(), 0.0, 0.0, List.of(), List.of(), 1L);
        }

        int n = turns.size();
        int dim = EpisodicTurnRecord.VECTOR_DIMENSION;
        int k = Math.max(1, Math.min(maxClusters, n));

        // 1. 计算单一全局质心坍缩基线失真 2 * (1 - R_global)
        double[] globalMean = new double[dim];
        for (EpisodicTurnRecord t : turns) {
            float[] v = t.sphericalVector();
            for (int d = 0; d < dim; d++) {
                globalMean[d] += v[d] / (double) n;
            }
        }
        double rGlobal = Math.min(1.0, l2Norm(globalMean));
        double singleCollapseDistortion = 2.0 * (1.0 - rGlobal);

        // 2. Farthest-First（最远测地角优先）确定性初始化 K 个球面中心
        float[][] centroids = new float[k][dim];
        centroids[0] = turns.get(0).sphericalVector().clone();
        for (int cIdx = 1; cIdx < k; cIdx++) {
            int farthestTurn = 0;
            double minMaxCos = 2.0;
            for (int i = 0; i < n; i++) {
                double bestCos = -2.0;
                for (int prev = 0; prev < cIdx; prev++) {
                    bestCos = Math.max(bestCos, dot(turns.get(i).sphericalVector(), centroids[prev]));
                }
                if (bestCos < minMaxCos) {
                    minMaxCos = bestCos;
                    farthestTurn = i;
                }
            }
            centroids[cIdx] = turns.get(farthestTurn).sphericalVector().clone();
        }

        // 3. 球面 k-Means 迭代收敛（最大化簇内余弦内积和，等价于极小化平方弦失真）
        int[] assignments = new int[n];
        for (int iter = 0; iter < 12; iter++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int bestCluster = 0;
                double bestCos = -2.0;
                for (int cIdx = 0; cIdx < k; cIdx++) {
                    double cos = dot(turns.get(i).sphericalVector(), centroids[cIdx]);
                    if (cos > bestCos) {
                        bestCos = cos;
                        bestCluster = cIdx;
                    }
                }
                if (assignments[i] != bestCluster) {
                    assignments[i] = bestCluster;
                    changed = true;
                }
            }
            for (int cIdx = 0; cIdx < k; cIdx++) {
                double[] sum = new double[dim];
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (assignments[i] == cIdx) {
                        float[] v = turns.get(i).sphericalVector();
                        for (int d = 0; d < dim; d++) {
                            sum[d] += v[d];
                        }
                        count++;
                    }
                }
                if (count > 0) {
                    double norm = l2Norm(sum);
                    if (norm > 1e-12) {
                        for (int d = 0; d < dim; d++) {
                            centroids[cIdx][d] = (float) (sum[d] / norm);
                        }
                    }
                }
            }
            if (!changed) {
                break;
            }
        }

        // 4. 逐簇计算精确弦失真 2*(1-R_k)、本征测地失真与球冠紧上界，并编译 Wiki 页与 Error-Book 笔记
        List<MultiClusterSleepConsolidationReceipt.SphericalClusterSummary> summaries = new ArrayList<>();
        List<String> wikiPages = new ArrayList<>();
        List<String> errorNotes = new ArrayList<>();
        double weightedTotalChordDistortion = 0.0;

        for (int cIdx = 0; cIdx < k; cIdx++) {
            List<EpisodicTurnRecord> members = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (assignments[i] == cIdx) {
                    members.add(turns.get(i));
                }
            }
            if (members.isEmpty()) {
                continue;
            }
            int mSize = members.size();
            double[] meanVec = new double[dim];
            for (EpisodicTurnRecord t : members) {
                float[] v = t.sphericalVector();
                for (int d = 0; d < dim; d++) {
                    meanVec[d] += v[d] / (double) mSize;
                }
            }
            double rK = Math.min(1.0, l2Norm(meanVec));
            float[] unitMu = new float[dim];
            if (rK > 1e-12) {
                for (int d = 0; d < dim; d++) {
                    unitMu[d] = (float) (meanVec[d] / rK);
                }
            } else {
                unitMu[0] = 1.0f;
            }

            double maxTheta = 0.0;
            double geoDistortion = 0.0;
            StringBuilder wikiContent = new StringBuilder("## Wiki Cluster #" + cIdx + "\n");
            for (EpisodicTurnRecord t : members) {
                double cos = Math.max(-1.0, Math.min(1.0, dot(t.sphericalVector(), unitMu)));
                double theta = Math.acos(cos);
                maxTheta = Math.max(maxTheta, theta);
                geoDistortion += (theta * theta) / mSize;
                wikiContent.append("- ").append(t.content()).append('\n');

                if (t.keyFacts() != null && t.keyFacts().containsKey("errorTrap")) {
                    errorNotes.add("ERROR_BOOK[" + sessionId + "]: " + t.keyFacts().get("errorTrap"));
                }
            }

            double chordDistortion = 2.0 * (1.0 - rK);
            double halfTheta = Math.min(Math.PI / 2.0 - 1e-4, maxTheta / 2.0);
            double ratio = (halfTheta <= 1e-7) ? 1.0 : (halfTheta / Math.sin(halfTheta));
            double geoUpperBound = (ratio * ratio) * chordDistortion;

            weightedTotalChordDistortion += ((double) mSize / n) * chordDistortion;
            wikiPages.add(wikiContent.toString());
            summaries.add(new MultiClusterSleepConsolidationReceipt.SphericalClusterSummary(
                    cIdx, mSize, unitMu, rK, maxTheta, chordDistortion, geoDistortion, geoUpperBound
            ));
        }

        long elapsedUs = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        return new MultiClusterSleepConsolidationReceipt(
                cid, sessionId, summaries, weightedTotalChordDistortion, singleCollapseDistortion,
                wikiPages, errorNotes, elapsedUs
        );
    }

    private static double dot(float[] a, float[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) {
            s += (double) a[i] * b[i];
        }
        return s;
    }

    private static double l2Norm(double[] v) {
        double s = 0.0;
        for (double x : v) {
            s += x * x;
        }
        return Math.sqrt(s);
    }
}
