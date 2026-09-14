package tech.qiantong.qknow.ai.dataagent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.qiantong.qknow.ai.dataagent.security.AstSecurityException;

import java.util.*;
import java.util.function.Function;

/**
 * 基于 Reflexion 范式与有限视界 MDP 的数据库执行反思自愈引擎
 * 严格遵从定理 3.1（Reflexion 有限视界自愈收敛界定理）
 */
@Component
public class QuerySelfHealingAgent {

    private static final Logger log = LoggerFactory.getLogger(QuerySelfHealingAgent.class);
    public static final int MAX_HEALING_ROUNDS = 3;

    // 可自愈的典型数据库错误状态码集合 (PostgreSQL & Cypher 模式/语法错误)
    private static final Set<String> HEALABLE_SQL_STATES = Set.of(
            "42P01", // undefined_table (表名不存在/幻觉)
            "42703", // undefined_column (列名不存在/拼写笔误)
            "42601", // syntax_error (语法结构错误)
            "42883", // undefined_function (函数不存在/传参错误)
            "42804"  // datatype_mismatch (数据类型不匹配)
    );

    // 致命不可自愈错误 (超时、连接中断、安全违规直接熔断)
    private static final Set<String> FATAL_SQL_STATES = Set.of(
            "57014", // query_canceled (查询超时中断)
            "08001", // unable_to_connect
            "08006"  // connection_failure
    );

    /**
     * 判断错误是否属于可自愈类别
     */
    public boolean isHealable(String sqlState, Throwable error) {
        if (sqlState != null) {
            if (FATAL_SQL_STATES.contains(sqlState)) return false;
            if (HEALABLE_SQL_STATES.contains(sqlState)) return true;
        }
        if (error instanceof AstSecurityException) {
            return false; // 安全注入违规直接拦截，禁止自愈重试
        }
        String msg = error != null ? error.getMessage() : "";
        return msg.contains("SyntaxError") || msg.contains("UnknownPropertyKey") || msg.contains("does not exist");
    }

    /**
     * 构建富错误诊断反思 Prompt 上下文 (包含错误定位与候选建议)
     */
    public String buildReflexionPrompt(String userPrompt, String failedQuery, String sqlState, String errorMsg, String schemaContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("【因果诊断与自愈任务】：请作为顶级数据库专家，分析前一次查询执行的失败根因并生成修正后的纯净查询。\n");
        sb.append("1. 用户原始自然语言需求: ").append(userPrompt).append("\n");
        sb.append("2. 失败的查询语句:\n```sql\n").append(failedQuery).append("\n```\n");
        sb.append("3. 数据库原生报错信息:\n");
        sb.append(" - SQLState 状态码: ").append(sqlState != null ? sqlState : "N/A").append("\n");
        sb.append(" - 详细异常日志: ").append(errorMsg).append("\n");
        sb.append("4. 真实数据库模式定义 (Schema Reference):\n").append(schemaContext).append("\n");
        sb.append("【反思与自愈要求】：\n");
        sb.append("- 明确指出错误原因（如指出哪个表名或列名在 Schema 中不存在，并找出真实对应的字段名）；\n");
        sb.append("- 输出修正后的只读语句，严禁使用非只读关键词，严禁多语句堆叠。\n");
        return sb.toString();
    }

    /**
     * 模拟或调用外部推理模型执行自愈闭环推导
     *
     * @param userPrompt     用户问题
     * @param initialQuery   初次生成但执行失败的 Query
     * @param sqlState       数据库原生错误码
     * @param errorMsg       异常消息
     * @param schemaContext  模式上下文
     * @param executorMock   试执行测试回调: (candidateQuery) -> (null if success, else Throwable)
     * @param repairModelFn  模型修复回调: (reflexionPrompt) -> repairedQuery
     * @return 包含自愈结果的数据结构 (最终语句, 经历轮次, 是否成功, 反思日志)
     */
    public HealingResult runHealingLoop(
            String userPrompt,
            String initialQuery,
            String sqlState,
            String errorMsg,
            String schemaContext,
            Function<String, Throwable> executorMock,
            Function<String, String> repairModelFn) {

        List<String> healingLogs = new ArrayList<>();
        String currentQuery = initialQuery;
        String currentSqlState = sqlState;
        String currentErrorMsg = errorMsg;

        if (!isHealable(currentSqlState, new RuntimeException(currentErrorMsg))) {
            log.warn("[QuerySelfHealingAgent] 遇到致命不可自愈错误/超时: SQLState={}, 立即熔断", currentSqlState);
            healingLogs.add("FATAL_ERROR_HALT: " + currentSqlState + " - " + currentErrorMsg);
            return new HealingResult(currentQuery, 0, false, healingLogs, "不可恢复错误立即熔断: " + currentSqlState);
        }

        for (int round = 1; round <= MAX_HEALING_ROUNDS; round++) {
            healingLogs.add(String.format("Round %d: 开始基于错误 [%s] 执行反思推导", round, currentSqlState));

            String reflexionPrompt = buildReflexionPrompt(userPrompt, currentQuery, currentSqlState, currentErrorMsg, schemaContext);
            String repairedQuery = repairModelFn.apply(reflexionPrompt);

            healingLogs.add(String.format("Round %d: 模型推导出修正语句 -> %s", round, repairedQuery));

            // 在沙箱中试执行修正后的语句
            Throwable execErr = executorMock.apply(repairedQuery);
            if (execErr == null) {
                healingLogs.add(String.format("Round %d: 试执行成功，达成正确执行收敛态！", round));
                return new HealingResult(repairedQuery, round, true, healingLogs, null);
            } else {
                currentQuery = repairedQuery;
                currentErrorMsg = execErr.getMessage();
                // 提取错误中的状态码 (若有)
                currentSqlState = extractSqlState(execErr);
                healingLogs.add(String.format("Round %d: 试执行依然报错 -> %s", round, currentErrorMsg));

                if (!isHealable(currentSqlState, execErr)) {
                    healingLogs.add("中途遇到不可自愈错误，提前终止反思循环");
                    break;
                }
            }
        }

        return new HealingResult(currentQuery, MAX_HEALING_ROUNDS, false, healingLogs, "已达最大自愈轮次(" + MAX_HEALING_ROUNDS + ")仍未收敛");
    }

    private String extractSqlState(Throwable t) {
        if (t instanceof java.sql.SQLException se) {
            return se.getSQLState();
        }
        return "UNKNOWN";
    }

    public static record HealingResult(
            String finalQuery,
            int rounds,
            boolean success,
            List<String> logs,
            String failureReason
    ) {}
}
