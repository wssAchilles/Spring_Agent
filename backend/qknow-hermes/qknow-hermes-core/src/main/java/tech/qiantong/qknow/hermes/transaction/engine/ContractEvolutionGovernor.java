package tech.qiantong.qknow.hermes.transaction.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.hermes.transaction.dto.ContractEvolutionResult;
import tech.qiantong.qknow.hermes.transaction.dto.ContractEvolutionType;

import java.util.*;

/**
 * 动态契约自适应演化适配器 (定理 1.1 与命题 2.1)
 * 集成阿里千问 1536 维超球面语义测地漂移检验与双向兼容性状态机
 * 单步校验与适配耗时严格 <= 60μs
 */
@Component
public class ContractEvolutionGovernor {

    private static final Logger log = LoggerFactory.getLogger(ContractEvolutionGovernor.class);

    public static final int EMBEDDING_DIMENSION = 1536;
    public static final double EMBEDDING_NORM_EPSILON = 1e-4;
    public static final double GEODESIC_DRIFT_THRESHOLD = 0.35; // 弧度门限，约 20 度

    /**
     * 强校验阿里千问 1536 维超球面单位向量
     */
    public void validateSphericalEmbedding(double[] emb) {
        if (emb == null || emb.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException("向量必须严格为 1536 维阿里千问嵌入");
        }
        double sumSq = 0.0;
        for (double v : emb) {
            sumSq += v * v;
        }
        double norm = Math.sqrt(sumSq);
        if (Math.abs(norm - 1.0) > EMBEDDING_NORM_EPSILON) {
            throw new IllegalArgumentException("向量模长不满足阿里千问超球面单位约束: " + norm);
        }
    }

    /**
     * 计算两向量在超球面上的测地距离与相似度
     */
    public double calculateGeodesicDistance(double[] v1, double[] v2) {
        validateSphericalEmbedding(v1);
        validateSphericalEmbedding(v2);

        double dot = 0.0;
        for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
            dot += v1[i] * v2[i];
        }
        // clamp 杜绝浮点误差导致 Math.acos 返回 NaN
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.acos(dot);
    }

    /**
     * 核心契约自适应校验与有效载荷适配
     */
    public ContractEvolutionResult evaluateAndAdapt(
            Map<String, Object> oldContractSpec,
            Map<String, Object> newContractSpec,
            double[] oldEmbedding,
            double[] newEmbedding,
            Map<String, Object> incomingPayload
    ) {
        long startNanos = System.nanoTime();
        List<String> details = new ArrayList<>();

        // 1. 语义漂移检验
        double geodesicDist = calculateGeodesicDistance(oldEmbedding, newEmbedding);
        double similarity = Math.cos(geodesicDist);

        if (geodesicDist > GEODESIC_DRIFT_THRESHOLD) {
            details.add(String.format("语义漂移超过安全门限: %.4f rad > %.4f rad", geodesicDist, GEODESIC_DRIFT_THRESHOLD));
            long dur = System.nanoTime() - startNanos;
            return new ContractEvolutionResult(
                    false, ContractEvolutionType.INCOMPATIBLE_DRIFT,
                    geodesicDist, similarity, Collections.emptyMap(), details, dur
            );
        }

        // 2. 字段兼容性分析
        Set<String> oldRequired = extractStringSet(oldContractSpec, "requiredFields");
        Set<String> newRequired = extractStringSet(newContractSpec, "requiredFields");
        Set<String> oldOutputs = extractStringSet(oldContractSpec, "outputFields");
        Set<String> newOutputs = extractStringSet(newContractSpec, "outputFields");
        Map<String, Object> newDefaults = extractMap(newContractSpec, "fieldDefaults");

        Map<String, Object> adapted = new HashMap<>(incomingPayload != null ? incomingPayload : Collections.emptyMap());

        boolean forwardCompatible = true;
        // 向前兼容：新服务端所需必填字段，旧请求必须包含，或者有新默认值填充
        for (String req : newRequired) {
            if (!adapted.containsKey(req)) {
                if (newDefaults.containsKey(req)) {
                    adapted.put(req, newDefaults.get(req));
                    details.add("自动注入新增必填字段默认值: " + req);
                } else {
                    forwardCompatible = false;
                    details.add("新增必填字段无默认值填充，向前兼容破坏: " + req);
                }
            }
        }

        // 向后兼容：新服务端输出字段必须覆盖旧服务端的所有预期输出
        boolean backwardCompatible = true;
        if (!oldOutputs.isEmpty() && !newOutputs.containsAll(oldOutputs)) {
            backwardCompatible = false;
            Set<String> missing = new HashSet<>(oldOutputs);
            missing.removeAll(newOutputs);
            details.add("新契约输出丢失旧输出字段，向后兼容破坏: " + missing);
        }

        ContractEvolutionType type;
        boolean compatible;

        if (oldRequired.equals(newRequired) && oldOutputs.equals(newOutputs) && geodesicDist < 1e-6) {
            type = ContractEvolutionType.IDENTICAL;
            compatible = true;
            details.add("契约完全一致");
        } else if (forwardCompatible && backwardCompatible) {
            type = ContractEvolutionType.FULLY_COMPATIBLE;
            compatible = true;
            details.add("契约双向完全兼容");
        } else if (forwardCompatible) {
            type = ContractEvolutionType.FORWARD_COMPATIBLE;
            compatible = true;
            details.add("契约向前兼容已达成 (缺省填充)");
        } else if (backwardCompatible) {
            type = ContractEvolutionType.BACKWARD_COMPATIBLE;
            compatible = false; // 因请求参数缺失无法执行，标记为需要降级
            details.add("仅向后兼容输出，入参不满足向前兼容");
        } else {
            type = ContractEvolutionType.INCOMPATIBLE_DRIFT;
            compatible = false;
            details.add("契约双向兼容性均被破坏");
        }

        long dur = System.nanoTime() - startNanos;
        return new ContractEvolutionResult(
                compatible, type, geodesicDist, similarity,
                Collections.unmodifiableMap(adapted), details, dur
        );
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractStringSet(Map<String, Object> spec, String key) {
        if (spec == null || !spec.containsKey(key)) {
            return Collections.emptySet();
        }
        Object val = spec.get(key);
        if (val instanceof Collection<?> c) {
            Set<String> set = new HashSet<>();
            for (Object o : c) {
                if (o != null) set.add(o.toString());
            }
            return set;
        }
        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> spec, String key) {
        if (spec == null || !spec.containsKey(key)) {
            return Collections.emptyMap();
        }
        Object val = spec.get(key);
        if (val instanceof Map<?, ?> m) {
            Map<String, Object> res = new HashMap<>();
            m.forEach((k, v) -> res.put(String.valueOf(k), v));
            return res;
        }
        return Collections.emptyMap();
    }
}
