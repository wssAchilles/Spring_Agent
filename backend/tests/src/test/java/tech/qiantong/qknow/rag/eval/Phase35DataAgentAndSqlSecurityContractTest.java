package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.dataagent.agent.QuerySelfHealingAgent;
import tech.qiantong.qknow.ai.dataagent.model.ChartSpecVO;
import tech.qiantong.qknow.ai.dataagent.model.QueryResultVO;
import tech.qiantong.qknow.ai.dataagent.model.SchemaCard;
import tech.qiantong.qknow.ai.dataagent.security.AstSecurityException;
import tech.qiantong.qknow.ai.dataagent.security.CypherAstSecurityFilter;
import tech.qiantong.qknow.ai.dataagent.security.SqlAstSecurityFilter;
import tech.qiantong.qknow.ai.dataagent.service.ChartRecommenderService;
import tech.qiantong.qknow.ai.dataagent.service.DataAgentService;
import tech.qiantong.qknow.ai.dataagent.service.SchemaCatalogService;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 35 专属自动化契约测试套件：企业级异构数据智能分析智能体与符号因果自验证
 * 覆盖 10 项核心契约：
 * 1. contract01: 动态模式索引与超球面向量粗筛及 4KB 上下文压缩契约
 * 2. contract02: 外键传递闭包与 Steiner 树关系依赖自动恢复契约 (定理 1.1)
 * 3. contract03: JSqlParser 深度遍历与纯只读 PlainSelect 强制断言契约 (定理 2.1)
 * 4. contract04: 堆叠多语句 (Semicolon Stacked Queries) 与多根 AST 100% 阻断契约
 * 5. contract05: LIMIT 1000 安全阈值自动检测与强制 AST 重写注入契约
 * 6. contract06: Cypher 读写分离预检与变异子句 (CREATE/MERGE/DELETE) 拦截契约
 * 7. contract07: 基于 Reflexion 的 PostgreSQL 精确错误码自愈闭环契约 (定理 3.1)
 * 8. contract08: 受限执行沙箱超时 (3000ms) 与不可恢复错误立即熔断契约
 * 9. contract09: 自适应 ECharts 图表推荐 (维度/度量识别与类型单射) 契约 (定理 4.1)
 * 10. contract10: 端到端 DataAgent 查询服务与单色钛金毛玻璃 VO 输出契约
 */
public class Phase35DataAgentAndSqlSecurityContractTest {

    private SchemaCatalogService schemaCatalogService;
    private SqlAstSecurityFilter sqlAstSecurityFilter;
    private CypherAstSecurityFilter cypherAstSecurityFilter;
    private QuerySelfHealingAgent selfHealingAgent;
    private ChartRecommenderService chartRecommenderService;
    private DataAgentService dataAgentService;

    @BeforeEach
    void setUp() {
        schemaCatalogService = new SchemaCatalogService();
        sqlAstSecurityFilter = new SqlAstSecurityFilter();
        cypherAstSecurityFilter = new CypherAstSecurityFilter();
        selfHealingAgent = new QuerySelfHealingAgent();
        chartRecommenderService = new ChartRecommenderService();
        dataAgentService = new DataAgentService(
                schemaCatalogService,
                sqlAstSecurityFilter,
                cypherAstSecurityFilter,
                selfHealingAgent,
                chartRecommenderService
        );
    }

    @Test
    @DisplayName("契约01: 动态模式索引与超球面向量粗筛及 4KB 上下文压缩契约")
    void contract01_DynamicSchemaIndex_HyperSphereVector_CompressionWithin4KB() {
        String dsId = "ds_enterprise_100";
        int totalTables = 100;
        int totalUnprunedBytes = 0;

        // 构建模拟百表千列元数据卡片 (总体积约 40KB ~ 60KB)
        for (int i = 1; i <= totalTables; i++) {
            String tableName = "biz_table_" + i;
            String comment = "企业核心业务表第" + i + "分卷，记录各类业务明细流水";
            Map<String, String> cols = new LinkedHashMap<>();
            for (int c = 1; c <= 10; c++) {
                cols.put("col_" + c, "VARCHAR(64) COMMENT '字段" + c + "描述说明'");
            }
            Map<String, String> fks = new HashMap<>();
            if (i > 1) {
                fks.put("parent_id", "biz_table_" + (i - 1) + ".id");
            }
            SchemaCard card = new SchemaCard(tableName, comment, cols, fks, List.of("示例样本A", "示例样本B"), null);
            schemaCatalogService.registerSchemaCard(dsId, card);
            totalUnprunedBytes += card.toCompactDescription().length();
        }

        assertTrue(totalUnprunedBytes > 25000, "全量百表 Schema 描述应大于 25KB，实际: " + totalUnprunedBytes);

        // 执行针对特定业务主题的检索剪枝
        String userQuery = "查询 biz_table_50 中近期的各类业务明细流水";
        String prunedSchema = schemaCatalogService.retrievePrunedSchema(dsId, userQuery, null, 3);

        assertNotNull(prunedSchema);
        assertTrue(prunedSchema.length() <= SchemaCatalogService.MAX_SCHEMA_PROMPT_BYTES,
                "剪枝后的上下文必须严格 <= 4096 字节，实际: " + prunedSchema.length());
        assertTrue(prunedSchema.contains("biz_table_50"), "剪枝结果必须准确包含相关核心表");

        double compressionRate = 1.0 - ((double) prunedSchema.length() / totalUnprunedBytes);
        assertTrue(compressionRate >= 0.88, "模式上下文压缩率必须 >= 88%，实际: " + (compressionRate * 100) + "%");
    }

    @Test
    @DisplayName("契约02: 外键传递闭包与 Steiner 树关系依赖自动恢复契约 (定理 1.1)")
    void contract02_ForeignKeyTransitiveClosure_SteinerTreeBridgeRecovery() {
        String dsId = "ds_ecommerce";

        // 构建拓扑: users <-(user_id)- orders <-(order_id)- order_items -(product_id)-> products
        // users 与 products 无直接外键，中间依赖 orders 与 order_items 桥接
        SchemaCard usersCard = new SchemaCard("users", "用户表", Map.of("id", "BIGINT", "name", "VARCHAR"), Map.of(), List.of("张三"), null);
        SchemaCard ordersCard = new SchemaCard("orders", "订单主表", Map.of("id", "BIGINT", "user_id", "BIGINT", "amount", "DECIMAL"), Map.of("user_id", "users.id"), List.of(), null);
        SchemaCard orderItemsCard = new SchemaCard("order_items", "订单明细表", Map.of("id", "BIGINT", "order_id", "BIGINT", "product_id", "BIGINT"),
                Map.of("order_id", "orders.id", "product_id", "products.id"), List.of(), null);
        SchemaCard productsCard = new SchemaCard("products", "商品信息表", Map.of("id", "BIGINT", "product_name", "VARCHAR"), Map.of(), List.of("笔记本电脑"), null);

        schemaCatalogService.registerBatch(dsId, List.of(usersCard, ordersCard, orderItemsCard, productsCard));

        // 用户问题直接命中端点表 users 与 products
        String prompt = "统计每个 users 购买的 products 名称与总价";
        String pruned = schemaCatalogService.retrievePrunedSchema(dsId, prompt, null, 2);

        // 验证: 尽管初始 topK 仅选择 2 张表，外键传递闭包自动通过 Steiner 最小树将桥接表 orders 与 order_items 全部并入
        assertTrue(pruned.contains("users"), "必须包含端点核心表 users");
        assertTrue(pruned.contains("products"), "必须包含端点核心表 products");
        assertTrue(pruned.contains("orders"), "必须自动桥接推导出依赖表 orders (定理 1.1)");
        assertTrue(pruned.contains("order_items"), "必须自动桥接推导出依赖表 order_items (定理 1.1)");
    }

    @Test
    @DisplayName("契约03: JSqlParser 深度遍历与纯只读 PlainSelect 强制断言契约 (定理 2.1)")
    void contract03_JSqlParser_PlainSelectReadOnly_Enforcement_BlocksMutations() {
        // 1. 验证各种写操作与 DDL 操作均被 100% 拦截
        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("INSERT INTO users (id, name) VALUES (1, 'alice')"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("UPDATE users SET name='bob' WHERE id=1"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("DELETE FROM users WHERE id=1"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("DROP TABLE orders CASCADE"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("ALTER TABLE users ADD COLUMN phone VARCHAR(20)"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("TRUNCATE TABLE logs"));

        assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite("CREATE TABLE dummy (id int)"));

        // 2. 验证合法只读 SELECT 正常放行
        String safeSql = "SELECT id, name FROM users WHERE status = 1";
        String rewritten = sqlAstSecurityFilter.validateAndRewrite(safeSql);
        assertNotNull(rewritten);
        assertTrue(rewritten.toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("契约04: 堆叠多语句 (Semicolon Stacked Queries) 与多根 AST 100% 阻断契约")
    void contract04_SemicolonStackedQueries_MultiStatement_100PercentBlocked() {
        // 攻击场景 1: 分号堆叠写操作与删表
        String attackSql1 = "SELECT * FROM users; DROP TABLE orders; --";
        AstSecurityException ex1 = assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite(attackSql1));
        assertEquals("STACKED_QUERIES_DETECTED", ex1.getErrorCode());

        // 攻击场景 2: 分号堆叠只读多语句探活 (如 sleep / benchmark)
        String attackSql2 = "SELECT 1; SELECT pg_sleep(5);";
        AstSecurityException ex2 = assertThrows(AstSecurityException.class, () ->
                sqlAstSecurityFilter.validateAndRewrite(attackSql2));
        assertEquals("STACKED_QUERIES_DETECTED", ex2.getErrorCode());

        // 正常场景: 单条查询末尾携带合规分号，JSqlParser 解析为单一 Statement，应正常放行
        String normalWithSemicolon = "SELECT id, name FROM users WHERE id > 0;";
        String rewritten = sqlAstSecurityFilter.validateAndRewrite(normalWithSemicolon);
        assertNotNull(rewritten);
        assertTrue(rewritten.contains("LIMIT 1000"));
    }

    @Test
    @DisplayName("契约05: LIMIT 1000 安全阈值自动检测与强制 AST 重写注入契约")
    void contract05_Limit1000_EnforcementAndAstRewrite() {
        // 场景 1: 未指定 LIMIT，强制在 AST 层面注入 LIMIT 1000
        String sqlNoLimit = "SELECT id, username, email FROM users ORDER BY id DESC";
        String rewritten1 = sqlAstSecurityFilter.validateAndRewrite(sqlNoLimit);
        assertTrue(rewritten1.contains("LIMIT 1000"), "未指定 LIMIT 时必须注入 LIMIT 1000: " + rewritten1);

        // 场景 2: 指定超过 1000 的大 LIMIT (如 LIMIT 50000)，强制截断重写为 LIMIT 1000
        String sqlExcessiveLimit = "SELECT id, username FROM users LIMIT 50000";
        String rewritten2 = sqlAstSecurityFilter.validateAndRewrite(sqlExcessiveLimit);
        assertTrue(rewritten2.contains("LIMIT 1000"), "超大 LIMIT 必须强制截断为 LIMIT 1000: " + rewritten2);
        assertFalse(rewritten2.contains("50000"), "原有的 50000 必须被清除");

        // 场景 3: 指定安全范围内的合理小 LIMIT (如 LIMIT 20)，保持不变
        String sqlSafeLimit = "SELECT id, username FROM users LIMIT 20";
        String rewritten3 = sqlAstSecurityFilter.validateAndRewrite(sqlSafeLimit);
        assertTrue(rewritten3.contains("LIMIT 20"), "安全合理的小 LIMIT 保持生效: " + rewritten3);
    }

    @Test
    @DisplayName("契约06: Cypher 读写分离预检与变异子句 (CREATE/MERGE/DELETE) 拦截契约")
    void contract06_Cypher_ReadOnlyValidation_BlocksMutationsAndDangerousCalls() {
        // 1. 合法只读 Cypher 校验放行并注入 LIMIT
        String safeCypher = "MATCH (u:User)-[:PLACED]->(o:Order) RETURN u.name, o.amount";
        String rewrittenSafe = cypherAstSecurityFilter.validateAndRewrite(safeCypher);
        assertTrue(rewrittenSafe.contains("LIMIT 1000"), "Cypher 必须自动补齐 LIMIT 1000");

        // 2. 拦截写操作关键字 (CREATE, MERGE, DELETE, SET, DETACH DELETE)
        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("CREATE (n:User {name: 'hacker'})"));

        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MATCH (n:User) DELETE n"));

        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MATCH (n:User) DETACH DELETE n"));

        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MATCH (n:User {id: 1}) SET n.role = 'admin'"));

        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MERGE (n:User {id: 100}) RETURN n"));

        // 3. 拦截高危 APOC 过程
        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MATCH (n) CALL apoc.export.csv.all('out.csv', {}) RETURN n"));

        // 4. 拦截分号多语句注入
        assertThrows(AstSecurityException.class, () ->
                cypherAstSecurityFilter.validateAndRewrite("MATCH (u:User) RETURN u; MATCH (o:Order) DELETE o"));
    }

    @Test
    @DisplayName("契约07: 基于 Reflexion 的 PostgreSQL 精确错误码自愈闭环契约 (定理 3.1)")
    void contract07_Reflexion_PostgreSqlErrorCode_SelfHealingConvergence() {
        String prompt = "查询所有用户的订单总额";
        String initialBadQuery = "SELECT usr_name, order_tot FROM t_orders_mistake";
        String sqlState = "42P01"; // relation "t_orders_mistake" does not exist
        String errorMsg = "relation \"t_orders_mistake\" does not exist";
        String schemaContext = "TABLE orders(id BIGINT, user_name VARCHAR, total_amount DECIMAL);";

        AtomicInteger roundCounter = new AtomicInteger(0);

        // 模拟外部 DeepSeek-R1 链式推理自愈函数：根据错误反思，在第 1 轮返回正确表名与字段
        QuerySelfHealingAgent.HealingResult result = selfHealingAgent.runHealingLoop(
                prompt,
                initialBadQuery,
                sqlState,
                errorMsg,
                schemaContext,
                candidateQuery -> {
                    roundCounter.incrementAndGet();
                    if (candidateQuery.contains("orders") && candidateQuery.contains("total_amount")) {
                        return null; // 试执行成功，收敛！
                    }
                    return new SQLException("column still error", "42703");
                },
                reflexionPrompt -> {
                    assertTrue(reflexionPrompt.contains("42P01"), "反思 Prompt 中必须注入精准 SQLState 错误码");
                    assertTrue(reflexionPrompt.contains("t_orders_mistake"), "反思 Prompt 中必须注入失败查询关键信息");
                    return "SELECT user_name, total_amount FROM orders LIMIT 1000";
                }
        );

        assertTrue(result.success(), "Reflexion 闭环必须自愈收敛成功");
        assertEquals(1, result.rounds(), "本场景应在第 1 轮内精准收敛");
        assertTrue(result.finalQuery().contains("orders"));
        assertFalse(result.logs().isEmpty());
    }

    @Test
    @DisplayName("契约08: 受限执行沙箱超时 (3000ms) 与不可恢复错误立即熔断契约")
    void contract08_ExecutionSandbox_Timeout57014_FatalErrorImmediateHalt() {
        // 1. 验证致命错误与超时的不可自愈性判定
        assertTrue(selfHealingAgent.isHealable("42P01", null));
        assertTrue(selfHealingAgent.isHealable("42703", null));
        assertTrue(selfHealingAgent.isHealable("42601", null));

        // 57014 (query_canceled / statement timeout) 判定为不可自愈
        assertFalse(selfHealingAgent.isHealable("57014", new SQLException("canceling statement due to statement timeout", "57014")));
        assertFalse(selfHealingAgent.isHealable("08001", new SQLException("connection refused", "08001")));
        assertFalse(selfHealingAgent.isHealable(null, new AstSecurityException("NON_READONLY", "write blocked", "")));

        // 2. 模拟超时发生时，自愈循环立即熔断退出，禁止浪费 Token
        QuerySelfHealingAgent.HealingResult timeoutResult = selfHealingAgent.runHealingLoop(
                "全表笛卡尔积计算",
                "SELECT * FROM big_table a, big_table b",
                "57014",
                "canceling statement due to statement timeout",
                "schema",
                q -> null,
                p -> "SELECT 1"
        );

        assertFalse(timeoutResult.success(), "超时错误必须判定自愈失败");
        assertEquals(0, timeoutResult.rounds(), "超时错误必须立即熔断，不执行任何反思轮次");
        assertTrue(timeoutResult.failureReason().contains("熔断"));
    }

    @Test
    @DisplayName("契约09: 自适应 ECharts 图表推荐 (维度/度量识别与类型单射) 契约 (定理 4.1)")
    void contract09_AdaptiveEchartsRecommendation_WilkinsonMackinlayRules() {
        // 场景 1: 单行单数值 -> 指标卡
        ChartSpecVO metricCard = chartRecommenderService.recommendChart(
                List.of("total_revenue"),
                List.of(Map.of("total_revenue", 1258000.50))
        );
        assertEquals("metric-card", metricCard.getChartType());
        assertTrue(metricCard.getEchartsOption().containsKey("value"));

        // 场景 2: 时序字段 + 数值度量 -> 折线图 (Line)
        ChartSpecVO lineChart = chartRecommenderService.recommendChart(
                List.of("created_date", "daily_orders"),
                List.of(
                        Map.of("created_date", "2026-09-01", "daily_orders", 120),
                        Map.of("created_date", "2026-09-02", "daily_orders", 150),
                        Map.of("created_date", "2026-09-03", "daily_orders", 180)
                )
        );
        assertEquals("line", lineChart.getChartType());
        assertTrue(lineChart.getEchartsOption().containsKey("xAxis"));
        assertTrue(lineChart.getEchartsOption().containsKey("series"));

        // 场景 3: 分类维度 + 度量 (行数 <= 7) -> 饼图/环形图 (Pie)
        ChartSpecVO pieChart = chartRecommenderService.recommendChart(
                List.of("category", "gmv"),
                List.of(
                        Map.of("category", "电子数码", "gmv", 50000),
                        Map.of("category", "家用电器", "gmv", 30000),
                        Map.of("category", "服装鞋帽", "gmv", 20000)
                )
        );
        assertEquals("pie", pieChart.getChartType());

        // 场景 4: 分类维度 + 度量 (行数 > 7 且 <= 30) -> 柱状图 (Bar)
        List<Map<String, Object>> barRows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            barRows.add(Map.of("dept_name", "部门" + i, "headcount", i * 5));
        }
        ChartSpecVO barChart = chartRecommenderService.recommendChart(List.of("dept_name", "headcount"), barRows);
        assertEquals("bar", barChart.getChartType());

        // 场景 5: 多维大结果集 -> 表格 (Table)
        ChartSpecVO tableChart = chartRecommenderService.recommendChart(
                List.of("id", "name", "city", "manager", "status"),
                List.of(Map.of("id", 1, "name", "A", "city", "北京", "manager", "张", "status", "OK"))
        );
        assertEquals("table", tableChart.getChartType());
    }

    @Test
    @DisplayName("契约10: 端到端 DataAgent 查询服务与单色钛金毛玻璃 VO 输出契约")
    void contract10_EndToEndDataAgentService_CompleteLifecycleAndVO() {
        String dsId = "ds_analytics";
        SchemaCard card = new SchemaCard("sales_summary", "销售汇总表",
                Map.of("dept", "VARCHAR", "revenue", "DECIMAL"), Map.of(), List.of("华东大区"), null);
        schemaCatalogService.registerSchemaCard(dsId, card);

        // 1. 正常业务查询：初次生成 -> AST 防火墙 (注入 LIMIT 1000) -> 执行 -> 自适应图表推荐 (Bar)
        dataAgentService.setQueryGenerator((prompt, schema) -> "SELECT dept, revenue FROM sales_summary");
        dataAgentService.setQueryExecutor(sql -> {
            assertTrue(sql.contains("LIMIT 1000"), "执行器必须接收到已注入 LIMIT 1000 的安全 SQL");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                rows.add(Map.of("dept", "大区" + i, "revenue", i * 10000));
            }
            return new DataAgentService.QueryExecutionResponse(List.of("dept", "revenue"), rows);
        });

        QueryResultVO vo1 = dataAgentService.query("统计各大区销售额", dsId);
        assertNotNull(vo1);
        assertEquals("SUCCESS", vo1.getStatus());
        assertEquals("bar", vo1.getChartSpec().getChartType());
        assertEquals(8, vo1.getRows().size());
        assertEquals(0, vo1.getHealingRounds());
        assertTrue(vo1.getExecutionTimeMs() >= 0);

        // 2. 注入攻击查询：大模型生成包含分号删表语句 -> AST 防火墙拦截
        dataAgentService.setQueryGenerator((prompt, schema) -> "SELECT * FROM sales_summary; DROP TABLE sales_summary; --");
        QueryResultVO vo2 = dataAgentService.query("恶意越权测试", dsId);
        assertEquals("BLOCKED_BY_AST_GUARD", vo2.getStatus());
        assertNull(vo2.getChartSpec());

        // 3. 自愈查询：初始生成列名笔误 (SQLState 42703) -> Reflexion 触发自愈修正 -> 成功输出
        AtomicInteger execCount = new AtomicInteger(0);
        dataAgentService.setQueryGenerator((prompt, schema) -> "SELECT dept_err, revenue FROM sales_summary");
        dataAgentService.setQueryExecutor(sql -> {
            if (execCount.incrementAndGet() == 1) {
                throw new RuntimeException(new SQLException("column dept_err does not exist", "42703"));
            }
            return new DataAgentService.QueryExecutionResponse(
                    List.of("dept", "revenue"),
                    List.of(Map.of("dept", "全国总汇", "revenue", 888888))
            );
        });
        dataAgentService.setRepairModelFunction(prompt -> "SELECT dept, revenue FROM sales_summary LIMIT 1000");

        QueryResultVO vo3 = dataAgentService.query("自动修复笔误查询", dsId);
        assertEquals("SUCCESS", vo3.getStatus());
        assertEquals(1, vo3.getHealingRounds(), "必须经历 1 轮自愈");
        assertFalse(vo3.getHealingLogs().isEmpty());
        assertEquals("metric-card", vo3.getChartSpec().getChartType(), "单行数据应自适应映射为指标卡");
    }
}
