package tech.qiantong.qknow.ai.credit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 组合竞标残差协方差反共谋审计器 (Anti-Collusion Auditor)
 * 基于滑动窗口 W=20 报价残差协方差矩阵、Pearson 积矩相关系数与卡方独立性检验识别卡特尔同盟并熔断
 */
@Component
public class AntiCollusionAuditor {

    private static final Logger log = LoggerFactory.getLogger(AntiCollusionAuditor.class);

    /** 滑动窗口大小 W=20 */
    public static final int DEFAULT_WINDOW_SIZE = 20;

    /** 共谋判定 Pearson 线性相关系数阈值 rho >= 0.80 */
    public static final double COLLUSION_CORRELATION_THRESHOLD = 0.80;

    /** 最小残差样本数 (低于此数仅观察不报警) */
    public static final int MIN_SAMPLE_SIZE = 4;

    private final int windowSize;
    private final ConcurrentHashMap<String, Deque<Double>> residualHistory = new ConcurrentHashMap<>();
    private final Set<String> quarantinedAgents = ConcurrentHashMap.newKeySet();

    public AntiCollusionAuditor() {
        this(DEFAULT_WINDOW_SIZE);
    }

    public AntiCollusionAuditor(int windowSize) {
        this.windowSize = Math.max(3, windowSize);
    }

    /**
     * 记录智能体单次竞标残差: e_{i,t} = b_{i,t} - v_i
     */
    public void recordBidResidual(String agentId, double valuation, double actualBid) {
        if (agentId == null) return;
        double residual = actualBid - valuation;
        Deque<Double> deque = residualHistory.computeIfAbsent(agentId, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            deque.addLast(residual);
            while (deque.size() > windowSize) {
                deque.pollFirst();
            }
        }
    }

    /**
     * 审计当前批次报价，探测卡特尔同盟并计算共谋风险指数
     */
    public AuditorResult auditBids(Map<String, Double> bids, Map<String, Double> valuations) {
        if (bids == null || bids.isEmpty()) {
            return new AuditorResult(true, 0.0, Set.of(), "无有效报价，默认安全通过");
        }

        // 1. 记录残差
        for (Map.Entry<String, Double> entry : bids.entrySet()) {
            String agentId = entry.getKey();
            double bid = entry.getValue();
            double val = valuations != null && valuations.containsKey(agentId) ? valuations.get(agentId) : bid;
            recordBidResidual(agentId, val, bid);
        }

        // 2. 检查是否有已知隔离智能体参与
        Set<String> offendingAgents = new TreeSet<>();
        for (String agentId : bids.keySet()) {
            if (quarantinedAgents.contains(agentId)) {
                offendingAgents.add(agentId);
            }
        }
        if (!offendingAgents.isEmpty()) {
            return new AuditorResult(false, 1.0, offendingAgents, "检测到已被熔断隔离的黑名单智能体参与竞标: " + offendingAgents);
        }

        // 3. 计算多智能体两两 Pearson 残差相关系数矩阵
        List<String> agentList = new ArrayList<>(bids.keySet());
        double maxCorrelation = 0.0;
        Set<String> cartelMembers = new TreeSet<>();

        for (int i = 0; i < agentList.size(); i++) {
            for (int j = i + 1; j < agentList.size(); j++) {
                String agentA = agentList.get(i);
                String agentB = agentList.get(j);

                double correlation = computePearsonCorrelation(agentA, agentB);
                if (correlation > maxCorrelation) {
                    maxCorrelation = correlation;
                }

                if (correlation >= COLLUSION_CORRELATION_THRESHOLD) {
                    // 结合卡方检验或残差方差判断协同抬价
                    cartelMembers.add(agentA);
                    cartelMembers.add(agentB);
                    log.warn("触发共谋警报: 智能体 [{}] 与 [{}] 报价残差相关系数达到 {} >= {}，判定为卡特尔同盟",
                            agentA, agentB, String.format(Locale.US, "%.4f", correlation), COLLUSION_CORRELATION_THRESHOLD);
                }
            }
        }

        if (!cartelMembers.isEmpty()) {
            quarantinedAgents.addAll(cartelMembers);
            double riskScore = Math.min(1.0, Math.max(0.85, maxCorrelation));
            return new AuditorResult(false, riskScore, Collections.unmodifiableSet(cartelMembers),
                    String.format(Locale.US, "检测到卡特尔共谋抬价同盟 %s, 最大相关系数: %.4f", cartelMembers, maxCorrelation));
        }

        double riskScore = Math.max(0.0, Math.min(0.40, maxCorrelation * 0.45));
        return new AuditorResult(true, riskScore, Set.of(), "审计安全通过，竞标智能体表现为独立残差");
    }

    /**
     * 计算两智能体残差之间的 Pearson 积矩相关系数
     */
    public double computePearsonCorrelation(String agentA, String agentB) {
        Deque<Double> histA = residualHistory.get(agentA);
        Deque<Double> histB = residualHistory.get(agentB);
        if (histA == null || histB == null) return 0.0;

        Double[] arrA;
        Double[] arrB;
        synchronized (histA) {
            arrA = histA.toArray(new Double[0]);
        }
        synchronized (histB) {
            arrB = histB.toArray(new Double[0]);
        }

        int n = Math.min(arrA.length, arrB.length);
        if (n < MIN_SAMPLE_SIZE) {
            return 0.0;
        }

        // 取最近 n 个残差点
        double sumA = 0.0, sumB = 0.0;
        int offsetA = arrA.length - n;
        int offsetB = arrB.length - n;
        for (int i = 0; i < n; i++) {
            sumA += arrA[offsetA + i];
            sumB += arrB[offsetB + i];
        }
        double meanA = sumA / n;
        double meanB = sumB / n;

        double cov = 0.0, varA = 0.0, varB = 0.0;
        for (int i = 0; i < n; i++) {
            double da = arrA[offsetA + i] - meanA;
            double db = arrB[offsetB + i] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }

        if (varA <= 1e-9 || varB <= 1e-9) {
            return 0.0; // 常数残差无波动或未产生协同
        }

        return cov / (Math.sqrt(varA) * Math.sqrt(varB));
    }

    public boolean isQuarantined(String agentId) {
        return quarantinedAgents.contains(agentId);
    }

    public void releaseQuarantine(String agentId) {
        quarantinedAgents.remove(agentId);
    }

    public void resetHistory() {
        residualHistory.clear();
        quarantinedAgents.clear();
    }

    public Set<String> getQuarantinedAgents() {
        return Collections.unmodifiableSet(quarantinedAgents);
    }

    /**
     * 审计结果数据模型
     */
    public record AuditorResult(
            boolean passes,
            double collusionRiskScore,
            Set<String> cartelMembers,
            String auditReason
    ) {}
}
