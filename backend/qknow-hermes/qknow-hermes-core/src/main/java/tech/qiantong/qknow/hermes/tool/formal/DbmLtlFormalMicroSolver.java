package tech.qiantong.qknow.hermes.tool.formal;

import java.util.*;

/**
 * 纯 Java 21 零外部依赖确定性形式化微求解器（DbmLtlFormalMicroSolver）。
 * <p>
 * 对应博士论文第 5 章定理 5.1：
 * 1. 基于 Pratt/Dill 区间差约束矩阵（Difference-Bound Matrix, DBM）与 Floyd-Warshall 最短路负权环检测，
 *    在 O(V^3)（微秒级、零 C++ JNI 崩溃风险）内完备判定线性差分不等式组可满足性，并沿最短负环提取极小冲突核 MUC；
 * 2. 结合有限轨迹 LTLf 先后序自动机与 S^1023 思考-行动一致性判定器，阻断“向上欺骗（Upward Deception）”。
 * </p>
 */
public class DbmLtlFormalMicroSolver {

    public static final String ZERO_ANCHOR = "__ZERO__";
    private static final double INF = 1e18;
    private static final double EPS = 1e-9;

    private final String hmacSecretKey;

    public DbmLtlFormalMicroSolver() {
        this("hermes-formal-microsolver-secret");
    }

    public DbmLtlFormalMicroSolver(String hmacSecretKey) {
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-formal-microsolver-secret"
                : hmacSecretKey;
    }

    /**
     * 对候选工具调用与 DeepSeek 思维链执行完备可判定验证，若违约则合成带极小冲突核（MUC）的尾部自纠错凭单。
     */
    public FormalCounterexampleReceipt verifyAndSynthesizeCounterexample(
            String executionId,
            String toolName,
            FormalConstraintFormula rootFormula,
            Map<String, Object> inputParams,
            List<String> executedToolHistory,
            String reasoningContent,
            float[] reasoningEmbedding1024,
            float[] actionEmbedding1024
    ) {
        Map<String, Object> safeParams = inputParams != null ? inputParams : Map.of();
        List<String> safeHistory = executedToolHistory != null ? executedToolHistory : List.of();
        String safeReasoning = reasoningContent != null ? reasoningContent : "";

        List<FormalConstraintFormula> atomicClauses = new ArrayList<>();
        flattenClauses(rootFormula, atomicClauses);

        List<FormalConstraintFormula.DifferenceBound> dbmBounds = new ArrayList<>();
        Set<String> muc = new LinkedHashSet<>();
        Map<String, Object> witness = new LinkedHashMap<>();

        for (FormalConstraintFormula clause : atomicClauses) {
            if (clause instanceof FormalConstraintFormula.DifferenceBound db) {
                dbmBounds.add(db);
            } else if (clause instanceof FormalConstraintFormula.EnumMembership em) {
                Object val = safeParams.get(em.varName());
                if (val == null || !em.allowedValues().contains(String.valueOf(val))) {
                    muc.add(em.clauseId());
                    witness.put(em.varName(), String.valueOf(val));
                }
            } else if (clause instanceof FormalConstraintFormula.LtlTemporalPrecedence ltl) {
                if (toolName.equals(ltl.targetTool()) && !safeHistory.contains(ltl.prerequisiteTool())) {
                    muc.add(ltl.clauseId());
                    witness.put("missingPrerequisiteTool", ltl.prerequisiteTool());
                }
            } else if (clause instanceof FormalConstraintFormula.ReasoningAlignmentImplication align) {
                boolean riskAcknowledgedInThought = false;
                String lowerThought = safeReasoning.toLowerCase(Locale.ROOT);
                for (String signal : align.reasoningRiskSignals()) {
                    if (signal != null && !signal.isBlank()
                            && lowerThought.contains(signal.toLowerCase(Locale.ROOT))) {
                        riskAcknowledgedInThought = true;
                        break;
                    }
                }
                double chordSq = computeSquaredChordIfPresent(reasoningEmbedding1024, actionEmbedding1024);
                boolean geometricDeception = chordSq > align.maxChordSqDeviation();
                boolean bypassFlagEnabled = Boolean.TRUE.equals(safeParams.get(align.forbiddenActionFlag()))
                        || "true".equalsIgnoreCase(String.valueOf(safeParams.get(align.forbiddenActionFlag())));

                if (bypassFlagEnabled && (riskAcknowledgedInThought || geometricDeception)) {
                    muc.add(align.clauseId());
                    witness.put("upwardDeceptionFlag", align.forbiddenActionFlag());
                    witness.put("thoughtActionChordSq", chordSq);
                }
            }
        }

        // 求解 DBM 差分不等式组：不等式 x - y <= c 映射为有向势边 y -> x (权重 c)
        if (!dbmBounds.isEmpty()) {
            solveDbmAndCollectMuc(dbmBounds, safeParams, muc, witness);
        }

        if (muc.isEmpty()) {
            return FormalCounterexampleReceipt.createSigned(
                    executionId,
                    toolName,
                    true,
                    List.of(),
                    Map.of(),
                    "FORMAL_UNSAT_VERIFIED: 工具调用与思考链完全满足形式化安全契约。",
                    hmacSecretKey
            );
        }

        List<String> mucList = List.copyOf(muc);
        String remediation = "[formal_cegar_counterexample: 工具 [" + toolName
                + "] 未通过形式化安全验证。极小不可满足核 MUC=" + mucList
                + "，冲突赋值见证=" + witness + "。请在下一轮修正参数或先执行缺失的前置只读工具，严禁绕过安全契约。]";
        return FormalCounterexampleReceipt.createSigned(
                executionId,
                toolName,
                false,
                mucList,
                witness,
                remediation,
                hmacSecretKey
        );
    }

    private static void solveDbmAndCollectMuc(
            List<FormalConstraintFormula.DifferenceBound> bounds,
            Map<String, Object> params,
            Set<String> mucOut,
            Map<String, Object> witnessOut
    ) {
        LinkedHashSet<String> vars = new LinkedHashSet<>();
        vars.add(ZERO_ANCHOR);
        for (FormalConstraintFormula.DifferenceBound b : bounds) {
            vars.add(b.varX());
            vars.add(b.varY());
        }

        List<String> varList = new ArrayList<>(vars);
        int n = varList.size();
        Map<String, Integer> idxMap = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            idxMap.put(varList.get(i), i);
        }

        double[][] dist = new double[n][n];
        @SuppressWarnings("unchecked")
        Set<String>[][] clauses = new Set[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
            dist[i][i] = 0.0;
            for (int j = 0; j < n; j++) {
                clauses[i][j] = new LinkedHashSet<>();
            }
        }

        // 1. 注入规约不等式边：varX - varY <= upperBound  <==>  边 varY -> varX (权 upperBound)
        for (FormalConstraintFormula.DifferenceBound b : bounds) {
            int u = idxMap.get(b.varY());
            int v = idxMap.get(b.varX());
            if (b.upperBound() < dist[u][v]) {
                dist[u][v] = b.upperBound();
                clauses[u][v] = new LinkedHashSet<>(List.of(b.clauseId()));
            }
        }

        // 2. 注入具体数值参数绑定：var == val  <==>  var - ZERO <= val 且 ZERO - var <= -val
        int zeroIdx = idxMap.get(ZERO_ANCHOR);
        for (Map.Entry<String, Integer> entry : idxMap.entrySet()) {
            String varName = entry.getKey();
            if (ZERO_ANCHOR.equals(varName)) {
                continue;
            }
            Object rawVal = params.get(varName);
            if (rawVal instanceof Number num) {
                double val = num.doubleValue();
                int vIdx = entry.getValue();
                // ZERO -> var (权 +val): 若已有更紧规约则保留冲突检测所需的双步路径，因此用独立虚拟锚或松弛检查
                if (val < dist[zeroIdx][vIdx]) {
                    dist[zeroIdx][vIdx] = val;
                    clauses[zeroIdx][vIdx] = new LinkedHashSet<>();
                } else if (dist[zeroIdx][vIdx] < val - EPS) {
                    // 规约上界本身小于实参值（例如 maxPool <= 200 而实参 maxPool = 300）
                    mucOut.addAll(clauses[zeroIdx][vIdx]);
                    witnessOut.put(varName, val);
                }

                if (-val < dist[vIdx][zeroIdx]) {
                    dist[vIdx][zeroIdx] = -val;
                    clauses[vIdx][zeroIdx] = new LinkedHashSet<>();
                } else if (dist[vIdx][zeroIdx] < -val - EPS) {
                    // 规约下界大于实参值（例如 ZERO - minPool <= -10 即 minPool >= 10，而实参 minPool = 3）
                    mucOut.addAll(clauses[vIdx][zeroIdx]);
                    witnessOut.put(varName, val);
                }
            }
        }

        // 3. Floyd-Warshall 全源最短路负权环检测，沿负对角线提取极小冲突核 MUC
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                if (dist[i][k] >= INF / 2) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    if (dist[k][j] >= INF / 2) {
                        continue;
                    }
                    double cand = dist[i][k] + dist[k][j];
                    if (cand < dist[i][j] - EPS) {
                        dist[i][j] = cand;
                        Set<String> merged = new LinkedHashSet<>(clauses[i][k]);
                        merged.addAll(clauses[k][j]);
                        clauses[i][j] = merged;
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            if (dist[i][i] < -EPS) {
                mucOut.addAll(clauses[i][i]);
                witnessOut.put("negativeCycleMagnitude", dist[i][i]);
            }
        }
    }

    private static void flattenClauses(FormalConstraintFormula formula, List<FormalConstraintFormula> out) {
        if (formula == null) {
            return;
        }
        if (formula instanceof FormalConstraintFormula.Conjunction conj) {
            for (FormalConstraintFormula child : conj.clauses()) {
                flattenClauses(child, out);
            }
        } else {
            out.add(formula);
        }
    }

    private static double computeSquaredChordIfPresent(float[] u, float[] v) {
        if (u == null || v == null || u.length == 0 || u.length != v.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < u.length; i++) {
            dot += (double) u[i] * v[i];
        }
        double clamped = Math.max(-1.0, Math.min(1.0, dot));
        return Math.max(0.0, 2.0 * (1.0 - clamped));
    }
}
