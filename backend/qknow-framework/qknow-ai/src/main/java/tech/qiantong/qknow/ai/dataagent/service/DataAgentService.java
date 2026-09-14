package tech.qiantong.qknow.ai.dataagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.qiantong.qknow.ai.dataagent.agent.QuerySelfHealingAgent;
import tech.qiantong.qknow.ai.dataagent.model.ChartSpecVO;
import tech.qiantong.qknow.ai.dataagent.model.QueryResultVO;
import tech.qiantong.qknow.ai.dataagent.security.AstSecurityException;
import tech.qiantong.qknow.ai.dataagent.security.CypherAstSecurityFilter;
import tech.qiantong.qknow.ai.dataagent.security.SqlAstSecurityFilter;

import java.sql.SQLException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 企业级异构数据智能分析智能体总协调服务 (DataAgent Service)
 * 编排全链路流转：
 * 1. 模式检索剪枝 (SchemaCatalogService)
 * 2. 初次生成 (DeepSeek API / 生成器函数)
 * 3. 多源 AST 只读防火墙与 LIMIT 1000 强制注入 (SqlAstSecurityFilter / CypherAstSecurityFilter)
 * 4. 受限只读沙箱试执行与 Reflexion 自愈闭环 (QuerySelfHealingAgent)
 * 5. 结果自适应图表推荐 (ChartRecommenderService)
 */
@Service
public class DataAgentService {

    private static final Logger log = LoggerFactory.getLogger(DataAgentService.class);

    private final SchemaCatalogService schemaCatalogService;
    private final SqlAstSecurityFilter sqlAstSecurityFilter;
    private final CypherAstSecurityFilter cypherAstSecurityFilter;
    private final QuerySelfHealingAgent selfHealingAgent;
    private final ChartRecommenderService chartRecommenderService;

    // 可扩展/可 Mock 的查询生成函数: (userPrompt, schemaContext) -> rawQuery
    private BiFunction<String, String, String> queryGenerator;
    // 可扩展/可 Mock 的物理执行器回调: (validatedQuery) -> QueryExecutionResponse
    private Function<String, QueryExecutionResponse> queryExecutor;
    // 可扩展/可 Mock 的自愈反思修复函数: (reflexionPrompt) -> repairedQuery
    private Function<String, String> repairModelFunction;

    public DataAgentService(
            SchemaCatalogService schemaCatalogService,
            SqlAstSecurityFilter sqlAstSecurityFilter,
            CypherAstSecurityFilter cypherAstSecurityFilter,
            QuerySelfHealingAgent selfHealingAgent,
            ChartRecommenderService chartRecommenderService) {
        this.schemaCatalogService = schemaCatalogService;
        this.sqlAstSecurityFilter = sqlAstSecurityFilter;
        this.cypherAstSecurityFilter = cypherAstSecurityFilter;
        this.selfHealingAgent = selfHealingAgent;
        this.chartRecommenderService = chartRecommenderService;

        // 默认实现
        this.queryGenerator = this::defaultQueryGenerator;
        this.queryExecutor = this::defaultQueryExecutor;
        this.repairModelFunction = this::defaultRepairModelFunction;
    }

    public void setQueryGenerator(BiFunction<String, String, String> queryGenerator) {
        this.queryGenerator = queryGenerator;
    }

    public void setQueryExecutor(Function<String, QueryExecutionResponse> queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    public void setRepairModelFunction(Function<String, String> repairModelFunction) {
        this.repairModelFunction = repairModelFunction;
    }

    /**
     * 智能数据分析入口
     *
     * @param userPrompt   用户自然语言提问
     * @param datasourceId 数据源标识
     * @return 结构化查询与图表结果 VO
     */
    public QueryResultVO query(String userPrompt, String datasourceId) {
        return query(userPrompt, datasourceId, "SQL", null);
    }

    /**
     * 智能数据分析入口 (支持指定查询类型 SQL / CYPHER)
     */
    public QueryResultVO query(String userPrompt, String datasourceId, String queryType) {
        return query(userPrompt, datasourceId, queryType, null);
    }

    /**
     * 智能数据分析端到端全链路执行入口
     *
     * @param userPrompt   用户自然语言提问
     * @param datasourceId 数据源标识
     * @param queryType    查询类别 (SQL / CYPHER)
     * @param queryVector  用户提问向量嵌入 (阿里千问 1536 维，可为 null)
     * @return 端到端分析结果视图对象
     */
    public QueryResultVO query(String userPrompt, String datasourceId, String queryType, float[] queryVector) {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();
        String finalQueryType = (queryType == null || queryType.isBlank()) ? "SQL" : queryType.toUpperCase().trim();

        log.info("[DataAgentService] 开始处理数据分析请求: traceId={}, datasource={}, type={}, prompt={}",
                traceId, datasourceId, finalQueryType, userPrompt);

        // 1. 动态模式检索与外键传递闭包剪枝 (严格保证 <= 4KB)
        String prunedSchema = schemaCatalogService.retrievePrunedSchema(datasourceId, userPrompt, queryVector, 3);

        // 2. 初次生成候选查询
        String rawQuery;
        try {
            rawQuery = this.queryGenerator.apply(userPrompt, prunedSchema);
        } catch (Exception e) {
            log.error("[DataAgentService] 初次生成失败: {}", e.getMessage(), e);
            return QueryResultVO.failure(traceId, userPrompt, "GENERATION_ERROR", "查询生成异常: " + e.getMessage());
        }

        // 3. 多源 AST 只读防火墙检测与 LIMIT 1000 强制重写注入
        String validatedQuery;
        try {
            if ("CYPHER".equals(finalQueryType)) {
                validatedQuery = cypherAstSecurityFilter.validateAndRewrite(rawQuery);
            } else {
                validatedQuery = sqlAstSecurityFilter.validateAndRewrite(rawQuery);
            }
        } catch (AstSecurityException e) {
            log.warn("[DataAgentService] 拦截非安全 AST 变异/多语句注入: {}", e.getMessage());
            QueryResultVO vo = QueryResultVO.failure(traceId, userPrompt, "BLOCKED_BY_AST_GUARD", e.getMessage());
            vo.setQueryType(finalQueryType);
            vo.setExecutedQuery(rawQuery);
            vo.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            return vo;
        } catch (Exception e) {
            log.warn("[DataAgentService] 语法解析校验异常: {}", e.getMessage());
            QueryResultVO vo = QueryResultVO.failure(traceId, userPrompt, "SYNTAX_ERROR", e.getMessage());
            vo.setQueryType(finalQueryType);
            vo.setExecutedQuery(rawQuery);
            vo.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            return vo;
        }

        // 4. 受限只读沙箱试执行与 Reflexion 自愈闭环
        QueryExecutionResponse execResp;
        int healingRounds = 0;
        List<String> healingLogs = new ArrayList<>();
        String currentQuery = validatedQuery;

        try {
            execResp = this.queryExecutor.apply(currentQuery);
        } catch (Throwable ex) {
            String sqlState = extractSqlState(ex);
            log.warn("[DataAgentService] 初次执行抛出异常: SQLState={}, msg={}", sqlState, ex.getMessage());

            // 致命不可自愈错误 (超时 / 连接故障) 立即熔断
            if (!selfHealingAgent.isHealable(sqlState, ex)) {
                String status = ("57014".equals(sqlState) || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout")))
                        ? "TIMEOUT" : "EXECUTION_ERROR";
                QueryResultVO vo = QueryResultVO.failure(traceId, userPrompt, status, ex.getMessage());
                vo.setQueryType(finalQueryType);
                vo.setExecutedQuery(currentQuery);
                vo.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                return vo;
            }

            // 进入 Reflexion 闭环自愈
            final String capturedType = finalQueryType;
            QuerySelfHealingAgent.HealingResult healingResult = selfHealingAgent.runHealingLoop(
                    userPrompt,
                    currentQuery,
                    sqlState,
                    ex.getMessage(),
                    prunedSchema,
                    candidateQuery -> {
                        try {
                            String checked = "CYPHER".equals(capturedType)
                                    ? cypherAstSecurityFilter.validateAndRewrite(candidateQuery)
                                    : sqlAstSecurityFilter.validateAndRewrite(candidateQuery);
                            queryExecutor.apply(checked);
                            return null;
                        } catch (Throwable t) {
                            return t;
                        }
                    },
                    this.repairModelFunction
            );

            healingRounds = healingResult.rounds();
            healingLogs.addAll(healingResult.logs());

            if (!healingResult.success()) {
                QueryResultVO vo = QueryResultVO.failure(traceId, userPrompt, "EXECUTION_ERROR", healingResult.failureReason());
                vo.setQueryType(finalQueryType);
                vo.setExecutedQuery(healingResult.finalQuery());
                vo.setHealingRounds(healingRounds);
                vo.setHealingLogs(healingLogs);
                vo.setExecutionTimeMs(System.currentTimeMillis() - startTime);
                return vo;
            }

            // 自愈成功，重写最终安全 SQL 并重新执行获取结果集
            currentQuery = "CYPHER".equals(capturedType)
                    ? cypherAstSecurityFilter.validateAndRewrite(healingResult.finalQuery())
                    : sqlAstSecurityFilter.validateAndRewrite(healingResult.finalQuery());
            execResp = this.queryExecutor.apply(currentQuery);
        }

        // 5. 自适应图表规格推荐
        List<String> columns = (execResp != null && execResp.columns() != null) ? execResp.columns() : Collections.emptyList();
        List<Map<String, Object>> rows = (execResp != null && execResp.rows() != null) ? execResp.rows() : Collections.emptyList();
        ChartSpecVO chartSpec = chartRecommenderService.recommendChart(columns, rows);

        // 6. 组装最终结果 VO
        QueryResultVO resultVO = new QueryResultVO();
        resultVO.setTraceId(traceId);
        resultVO.setUserPrompt(userPrompt);
        resultVO.setExecutedQuery(currentQuery);
        resultVO.setQueryType(finalQueryType);
        resultVO.setStatus("SUCCESS");
        resultVO.setHealingRounds(healingRounds);
        resultVO.setHealingLogs(healingLogs);
        resultVO.setColumns(columns);
        resultVO.setRows(rows);
        resultVO.setChartSpec(chartSpec);
        resultVO.setExecutionTimeMs(System.currentTimeMillis() - startTime);

        log.info("[DataAgentService] 数据分析成功完成: traceId={}, rows={}, chartType={}, rounds={}, cost={}ms",
                traceId, rows.size(), chartSpec.getChartType(), healingRounds, resultVO.getExecutionTimeMs());

        return resultVO;
    }

    private String extractSqlState(Throwable t) {
        if (t instanceof SQLException se) {
            return se.getSQLState();
        }
        if (t.getCause() instanceof SQLException se) {
            return se.getSQLState();
        }
        if (t.getMessage() != null) {
            if (t.getMessage().contains("57014")) return "57014";
            if (t.getMessage().contains("42P01")) return "42P01";
            if (t.getMessage().contains("42703")) return "42703";
            if (t.getMessage().contains("42601")) return "42601";
        }
        return "UNKNOWN";
    }

    private String defaultQueryGenerator(String prompt, String schema) {
        // 默认极简启发式生成
        return "SELECT 1 AS placeholder";
    }

    private QueryExecutionResponse defaultQueryExecutor(String query) {
        // 默认内存结果响应
        return new QueryExecutionResponse(List.of("result"), List.of(Map.of("result", 1)));
    }

    private String defaultRepairModelFunction(String reflexionPrompt) {
        return "SELECT 1 AS repaired_placeholder";
    }

    /**
     * 查询执行数据传输对象
     */
    public record QueryExecutionResponse(
            List<String> columns,
            List<Map<String, Object>> rows
    ) {}
}
