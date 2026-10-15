package tech.qiantong.qknow.hermes.memory.hippocampus;

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
}
