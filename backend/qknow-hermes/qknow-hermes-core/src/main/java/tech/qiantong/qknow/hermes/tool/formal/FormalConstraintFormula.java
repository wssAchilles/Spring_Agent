package tech.qiantong.qknow.hermes.tool.formal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 纯 Java 21 零外部依赖可判定形式化约束公式代数数据类型（对应博士论文第 5 章定义 4.1 & 4.2）。
 * <p>
 * 覆盖无量词差分逻辑（{@code QF_RDL / QF_IDL} 区间差约束）、枚举域约束、有限轨迹线性时序逻辑（{@code LTL_f}）
 * 以及连接 DeepSeek {@code reasoning_content} 与工具参数的双向反欺骗蕴涵约束（{@code phi_align}）。
 * </p>
 */
public sealed interface FormalConstraintFormula
        permits FormalConstraintFormula.DifferenceBound,
                FormalConstraintFormula.EnumMembership,
                FormalConstraintFormula.LtlTemporalPrecedence,
                FormalConstraintFormula.ReasoningAlignmentImplication,
                FormalConstraintFormula.Conjunction {

    /**
     * 区间差约束：表示不等式 {@code varX - varY <= upperBound}。
     * 当 {@code varY} 取 {@code ZERO_ANCHOR} 时表示上界 {@code varX <= upperBound}；
     * 当 {@code varX} 取 {@code ZERO_ANCHOR} 时表示下界 {@code varY >= -upperBound}。
     */
    record DifferenceBound(String clauseId, String varX, String varY, double upperBound)
            implements FormalConstraintFormula {
        public DifferenceBound {
            Objects.requireNonNull(clauseId, "clauseId 不能为空");
            Objects.requireNonNull(varX, "varX 不能为空");
            Objects.requireNonNull(varY, "varY 不能为空");
        }
    }

    /** 有限离散集合归属约束：{@code varName in allowedValues} */
    record EnumMembership(String clauseId, String varName, Set<String> allowedValues)
            implements FormalConstraintFormula {
        public EnumMembership {
            Objects.requireNonNull(clauseId, "clauseId 不能为空");
            Objects.requireNonNull(varName, "varName 不能为空");
            allowedValues = Set.copyOf(allowedValues != null ? allowedValues : Set.of());
        }
    }

    /** 有限轨迹线性时序逻辑先后约束（LTLf）：调用 {@code targetTool} 前历史轨迹中必须已成功执行 {@code prerequisiteTool} */
    record LtlTemporalPrecedence(String clauseId, String prerequisiteTool, String targetTool)
            implements FormalConstraintFormula {
        public LtlTemporalPrecedence {
            Objects.requireNonNull(clauseId, "clauseId 不能为空");
            Objects.requireNonNull(prerequisiteTool, "prerequisiteTool 不能为空");
            Objects.requireNonNull(targetTool, "targetTool 不能为空");
        }
    }

    /**
     * 双向形式化思维（BFoT）反向上欺骗约束（定义 4.2）：
     * 若 {@code reasoning_content} 已推导出风险信号（{@code b_risk = 1}）或思考意图与工具描述在 S^1023 上平方弦偏离超阈值，
     * 则外显工具调用严禁开启高危绕过/执行标志 {@code forbiddenActionFlag}。
     */
    record ReasoningAlignmentImplication(
            String clauseId,
            List<String> reasoningRiskSignals,
            String forbiddenActionFlag,
            double maxChordSqDeviation
    ) implements FormalConstraintFormula {
        public ReasoningAlignmentImplication {
            Objects.requireNonNull(clauseId, "clauseId 不能为空");
            reasoningRiskSignals = List.copyOf(reasoningRiskSignals != null ? reasoningRiskSignals : List.of());
            Objects.requireNonNull(forbiddenActionFlag, "forbiddenActionFlag 不能为空");
        }
    }

    /** 合取范式组合约束 */
    record Conjunction(List<FormalConstraintFormula> clauses)
            implements FormalConstraintFormula {
        public Conjunction {
            clauses = List.copyOf(clauses != null ? clauses : List.of());
        }
    }
}
