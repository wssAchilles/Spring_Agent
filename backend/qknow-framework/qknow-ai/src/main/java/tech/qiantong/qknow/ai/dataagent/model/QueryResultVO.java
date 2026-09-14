package tech.qiantong.qknow.ai.dataagent.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能数据分析端到端查询结果视图对象
 */
public class QueryResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 查询 Trace 标识 */
    private String traceId;
    /** 用户原始问题 */
    private String userPrompt;
    /** 最终安全执行的 SQL 或 Cypher 语句 (包含 LIMIT 1000 重写) */
    private String executedQuery;
    /** 语言类别: SQL / CYPHER */
    private String queryType;
    /** 执行状态: SUCCESS, BLOCKED_BY_AST_GUARD, EXECUTION_ERROR, TIMEOUT */
    private String status;
    /** 错误信息 (若有) */
    private String errorMessage;
    /** 经历的 Reflexion 自愈轮次 (0 表示首次直接成功) */
    private int healingRounds = 0;
    /** 自愈反思链路日志列表 */
    private List<String> healingLogs = new ArrayList<>();
    /** 执行总耗时 (毫秒) */
    private long executionTimeMs = 0;
    /** 结果集列名列表 */
    private List<String> columns = new ArrayList<>();
    /** 结果集记录列表 (每行对应 Map<列名, 值>) */
    private List<Map<String, Object>> rows = new ArrayList<>();
    /** 自适应推荐的图表规格 */
    private ChartSpecVO chartSpec;

    public QueryResultVO() {}

    public static QueryResultVO failure(String traceId, String userPrompt, String status, String errorMessage) {
        QueryResultVO vo = new QueryResultVO();
        vo.setTraceId(traceId);
        vo.setUserPrompt(userPrompt);
        vo.setStatus(status);
        vo.setErrorMessage(errorMessage);
        return vo;
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }
    public String getExecutedQuery() { return executedQuery; }
    public void setExecutedQuery(String executedQuery) { this.executedQuery = executedQuery; }
    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getHealingRounds() { return healingRounds; }
    public void setHealingRounds(int healingRounds) { this.healingRounds = healingRounds; }
    public List<String> getHealingLogs() { return healingLogs; }
    public void setHealingLogs(List<String> healingLogs) { this.healingLogs = healingLogs; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public List<String> getColumns() { return columns; }
    public void setColumns(List<String> columns) { this.columns = columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) { this.rows = rows; }
    public ChartSpecVO getChartSpec() { return chartSpec; }
    public void setChartSpec(ChartSpecVO chartSpec) { this.chartSpec = chartSpec; }
}
