package tech.qiantong.qknow.hermes.flow.expression;

import java.util.Map;

/**
 * 表达式引擎接口
 * 用于评估条件表达式
 */
public interface ExpressionEngine {

    /**
     * 评估表达式
     *
     * @param expression 表达式字符串
     * @param context    上下文变量
     * @return 评估结果
     */
    boolean evaluate(String expression, Map<String, Object> context);
}
