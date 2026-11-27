package tech.qiantong.qknow.mcp.core.codemode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Code-Mode MCP 受限无环工具编排抽象语法树（纯 Java 21 Sealed Interface）。
 * <p>
 * 将传统的 O(K) 轮串行 LLM 工具调用折叠为 O(1) 单轮虚拟线程沙箱并发流水线，
 * 同时杜绝任意图灵完备脚本带来的无限死循环与系统调用逃逸风险。
 * </p>
 */
public sealed interface CodeModePlanAst
        permits CodeModePlanAst.InvokeToolStep,
                CodeModePlanAst.ParallelMapStep,
                CodeModePlanAst.FilterProjectStep,
                CodeModePlanAst.RawScriptStep,
                CodeModePlanAst.SequenceBlock {

    /** 单工具调用步骤，支持引用前序步骤变量表达式（如 {@code $s1.items} 或 {@code $item}） */
    record InvokeToolStep(String stepVar, String toolId, Map<String, String> argExpressions)
            implements CodeModePlanAst {
        public InvokeToolStep {
            Objects.requireNonNull(stepVar, "stepVar 不能为空");
            Objects.requireNonNull(toolId, "toolId 不能为空");
            argExpressions = Map.copyOf(argExpressions != null ? argExpressions : Map.of());
        }
    }

    /** 有界虚拟线程并发映射步骤（强制集合上限 <= 50） */
    record ParallelMapStep(String stepVar, String sourceListExpr, int maxConcurrency, InvokeToolStep itemAction)
            implements CodeModePlanAst {
        public ParallelMapStep {
            Objects.requireNonNull(stepVar, "stepVar 不能为空");
            Objects.requireNonNull(sourceListExpr, "sourceListExpr 不能为空");
            Objects.requireNonNull(itemAction, "itemAction 不能为空");
        }
    }

    /** L3 原始冗余报文过滤与字段裁剪投影步骤（保护上下文窗口不被大 JSON 污染） */
    record FilterProjectStep(
            String stepVar,
            String sourceExpr,
            String requiredValueSubstring,
            List<String> retainFields,
            int maxItems
    ) implements CodeModePlanAst {
        public FilterProjectStep {
            Objects.requireNonNull(stepVar, "stepVar 不能为空");
            Objects.requireNonNull(sourceExpr, "sourceExpr 不能为空");
            retainFields = List.copyOf(retainFields != null ? retainFields : List.of());
        }
    }

    /** 受静态 AST 白名单审查保护的内联数据处理脚本步骤 */
    record RawScriptStep(String stepVar, String language, String code)
            implements CodeModePlanAst {
        public RawScriptStep {
            Objects.requireNonNull(stepVar, "stepVar 不能为空");
            Objects.requireNonNull(language, "language 不能为空");
            Objects.requireNonNull(code, "code 不能为空");
        }
    }

    /** 顺序无环组合计划块 */
    record SequenceBlock(List<CodeModePlanAst> steps, String returnSummaryVar)
            implements CodeModePlanAst {
        public SequenceBlock {
            steps = List.copyOf(steps != null ? steps : List.of());
            Objects.requireNonNull(returnSummaryVar, "returnSummaryVar 不能为空");
        }
    }
}
