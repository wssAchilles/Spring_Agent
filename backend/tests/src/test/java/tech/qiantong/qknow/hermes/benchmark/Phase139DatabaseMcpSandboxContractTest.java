package tech.qiantong.qknow.hermes.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.tool.mcp.db.BiDirectionalTransactionAligner;
import tech.qiantong.qknow.hermes.tool.mcp.db.BiDirectionalTransactionAligner.RollbackResult;
import tech.qiantong.qknow.hermes.tool.mcp.db.BiDirectionalTransactionAligner.UndoLogEntry;
import tech.qiantong.qknow.hermes.tool.mcp.db.DatabaseMcpSqlSandboxGovernor;
import tech.qiantong.qknow.hermes.tool.mcp.db.DatabaseMcpSqlSandboxGovernor.SqlAuditResult;
import tech.qiantong.qknow.hermes.tool.mcp.db.DatabaseMcpTransactionReceipt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 139 核心契约测试套件：
 * 企业级异构数据库与中间件动态 MCP 运行时沙箱安全隔离与双向事务对齐中枢
 * (Enterprise Heterogeneous Database & Middleware Dynamic MCP Runtime
 * Sandboxed Isolation & Bi-Directional Transaction Alignment Metacenter)
 *
 * 核心验证范围：
 * 1. 高危 DDL (DROP/ALTER/TRUNCATE) 100% 拦截 (TC-139-1)
 * 2. 无 WHERE 谓词危险 DML 强制拦截 (TC-139-2)
 * 3. 多租户条件自动注入与 LIMIT 安全改写 <= 1.0ms (TC-139-3)
 * 4. 底层系统敏感字典库非法探测硬拦截 (TC-139-4)
 * 5. 双向事务前置镜像捕获与 Undo Log 自动生成 <= 0.5ms (TC-139-5)
 * 6. LIFO 逆向幂等补偿回滚成功率 100% 与耗时 <= 15ms (TC-139-6)
 * 7. 纯 Java 21 Record 凭单不可变性与 SHA-256 常量时间自验真 (TC-139-7)
 * 8. 端到端全链路闭环集成与防篡改签发 (TC-139-8)
 *
 * @author Achilles
 * @since 2026-09-25
 */
public class Phase139DatabaseMcpSandboxContractTest {

    private final DatabaseMcpSqlSandboxGovernor sandboxGovernor = new DatabaseMcpSqlSandboxGovernor();
    private final BiDirectionalTransactionAligner txAligner = new BiDirectionalTransactionAligner();

    // =========================================================================
    // TC-139-1: 高危 DDL 100% 拦截
    // =========================================================================
    @Test
    @DisplayName("TC-139-1: 对 DROP TABLE, ALTER TABLE, TRUNCATE 等高危 DDL 100% 拦截，抛出 SECURITY_SANDBOX_REJECTED")
    void testSqlSandbox_highRiskDdlInterception() {
        List<String> ddlAttacks = List.of(
                "DROP TABLE t_enterprise_order;",
                "ALTER TABLE t_user DROP COLUMN balance",
                "TRUNCATE TABLE t_system_audit_log",
                "create table malicious_backdoor (id int)",
                "GRANT ALL PRIVILEGES ON *.* TO 'attacker'@'%'"
        );

        for (String ddl : ddlAttacks) {
            SqlAuditResult result = sandboxGovernor.auditAndRewriteSql(ddl, "tenant_test_01", true);
            assertFalse(result.allowed(), "高危 DDL 必须被严格拒绝: " + ddl);
            assertTrue(result.rejectionReason().contains("SECURITY_SANDBOX_REJECTED"),
                    "拒绝原因必须包含 SECURITY_SANDBOX_REJECTED，实测: " + result.rejectionReason());
            assertTrue(result.latencyMs() <= 1.0, "沙箱校验耗时必须 <= 1.0ms");
        }
    }

    // =========================================================================
    // TC-139-2: 无 WHERE 谓词危险 DML 强制拦截
    // =========================================================================
    @Test
    @DisplayName("TC-139-2: 对无 WHERE 条件的危险 DELETE 和 UPDATE 语法实现 100% 拦截，防止全表误删")
    void testSqlSandbox_dangerousDmlWithoutWhereRejection() {
        List<String> dangerousDmls = List.of(
                "DELETE FROM t_user_workspace_draft",
                "delete from orders;",
                "UPDATE t_customer SET status = 'INACTIVE'",
                "update t_account set balance = 0"
        );

        for (String dml : dangerousDmls) {
            SqlAuditResult result = sandboxGovernor.auditAndRewriteSql(dml, "tenant_test_01", true);
            assertFalse(result.allowed(), "无 WHERE 条件的 DML 必须被严格拦截: " + dml);
            assertTrue(result.rejectionReason().contains("无 WHERE 条件"),
                    "错误信息必须明确提示无 WHERE 条件");
        }

        // 带合法 WHERE 条件的 DML 允许放行
        String safeDml = "UPDATE t_customer SET status = 'ACTIVE' WHERE customer_id = 'C1001'";
        SqlAuditResult safeResult = sandboxGovernor.auditAndRewriteSql(safeDml, "tenant_test_01", true);
        assertTrue(safeResult.allowed(), "带 WHERE 条件的合法 DML 应被允许");
    }

    // =========================================================================
    // TC-139-3: 多租户条件自动注入与 LIMIT 安全改写
    // =========================================================================
    @Test
    @DisplayName("TC-139-3: 对合法 SELECT 查询自动重写追加 LIMIT 上限与 tenant_id 过滤条件，改写耗时 <= 1.0ms")
    void testSqlSandbox_multiTenantInjectionAndLimitRewrite() {
        String originalSql = "SELECT order_id, amount FROM t_order WHERE amount > 5000 ORDER BY create_time DESC";
        String tenantId = "tenant_enterprise_finance";

        long start = System.nanoTime();
        SqlAuditResult result = sandboxGovernor.auditAndRewriteSql(originalSql, tenantId, false);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertTrue(result.allowed());
        assertTrue(elapsedMs <= 1.0, "单条 SQL 审计与改写耗时必须 <= 1.0ms，实测: " + elapsedMs + "ms");

        String rewritten = result.rewrittenSql();
        assertNotNull(rewritten);
        // 验证多租户条件注入
        assertTrue(rewritten.contains("tenant_id = 'tenant_enterprise_finance'"),
                "重写后的 SQL 必须强制包含多租户条件，实测: " + rewritten);
        // 验证自动追加默认 LIMIT 1000
        assertTrue(rewritten.contains("LIMIT 1000"), "重写后的 SQL 必须强制包含 LIMIT 1000");

        // 验证已有超大 LIMIT 的 SQL 被强制钳位
        String hugeLimitSql = "SELECT * FROM t_log WHERE level = 'ERROR' LIMIT 50000";
        SqlAuditResult clampedResult = sandboxGovernor.auditAndRewriteSql(hugeLimitSql, tenantId, false);
        assertTrue(clampedResult.rewrittenSql().contains("LIMIT 1000"), "超大 LIMIT 必须被自动钳位至 1000");
        assertFalse(clampedResult.rewrittenSql().contains("50000"));
    }

    // =========================================================================
    // TC-139-4: 底层系统敏感字典库非法探测硬拦截
    // =========================================================================
    @Test
    @DisplayName("TC-139-4: 拦截针对 information_schema, mysql.*, pg_catalog 等系统底层字典表的非法探测")
    void testSystemTableAccess_hardRejection() {
        List<String> systemProbes = List.of(
                "SELECT * FROM information_schema.tables",
                "SELECT table_name FROM mysql.innodb_table_stats",
                "select * from pg_catalog.pg_database;",
                "SELECT * FROM sys.version"
        );

        for (String probe : systemProbes) {
            SqlAuditResult result = sandboxGovernor.auditAndRewriteSql(probe, "tenant_attacker", false);
            assertFalse(result.allowed(), "系统底层字典库探测必须被拦截: " + probe);
            assertTrue(result.rejectionReason().contains("系统字典库"), "拒绝原因必须说明系统字典库");
        }
    }

    // =========================================================================
    // TC-139-5: 双向事务前置镜像捕获与 Undo Log 自动生成
    // =========================================================================
    @Test
    @DisplayName("TC-139-5: 正向写操作原子生成反向 Undo Log，生成耗时 <= 0.5ms")
    void testBiDirectionalTransaction_undoLogGeneration() {
        String txId = "TX-PAYMENT-SYNC-001";
        txAligner.beginTransaction(txId);

        // 预热消除首次类加载抖动
        txAligner.recordInsert(txId, "t_warmup", "id", "0", "INSERT");

        // 1. 记录 INSERT 操作
        long start = System.nanoTime();
        UndoLogEntry insertUndo = txAligner.recordInsert(
                txId, "t_order_payment", "payment_id", "PAY_9988",
                "INSERT INTO t_order_payment (payment_id, amount) VALUES ('PAY_9988', 2999)"
        );
        double insertLatency = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(insertUndo);
        assertEquals("DELETE FROM t_order_payment WHERE payment_id = 'PAY_9988'", insertUndo.undoSql());
        assertTrue(insertLatency <= 0.5, "Undo Log 生成耗时必须 <= 0.5ms，实测: " + insertLatency + "ms");

        // 2. 记录 UPDATE 操作并传入 preImage 旧值
        Map<String, Object> preImage = Map.of("balance", 10000, "status", "NORMAL", "user_id", "U778");
        UndoLogEntry updateUndo = txAligner.recordUpdate(
                txId, "t_user_balance", "user_id", "U778", preImage,
                "UPDATE t_user_balance SET balance = 8000 WHERE user_id = 'U778'"
        );

        assertNotNull(updateUndo);
        assertTrue(updateUndo.undoSql().startsWith("UPDATE t_user_balance SET"));
        assertTrue(updateUndo.undoSql().contains("balance = 10000"));
        assertTrue(updateUndo.undoSql().contains("WHERE user_id = 'U778'"));
    }

    // =========================================================================
    // TC-139-6: LIFO 逆向幂等补偿回滚成功率 100% 与耗时 <= 15ms
    // =========================================================================
    @Test
    @DisplayName("TC-139-6: 跨数据源异常时触发双向事务回滚，LIFO 执行 Undo Log，回滚成功率 100%，耗时 <= 15ms")
    void testBiDirectionalTransaction_rollbackExecution() {
        String txId = "TX-MULTI-SOURCE-ROLLBACK-002";
        txAligner.beginTransaction(txId);

        // 模拟依次执行 3 步数据库写操作
        txAligner.recordInsert(txId, "t_order", "order_id", "ORD_1", "INSERT INTO t_order...");
        txAligner.recordUpdate(txId, "t_inventory", "sku_id", "SKU_A", Map.of("stock", 50, "sku_id", "SKU_A"), "UPDATE t_inventory...");
        txAligner.recordInsert(txId, "t_coupon_usage", "coupon_id", "CPN_9", "INSERT INTO t_coupon_usage...");

        // 模拟第 4 步向量库网络超时，触发事务回滚
        long start = System.nanoTime();
        RollbackResult result = txAligner.executeRollback(txId);
        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(result);
        assertTrue(result.success(), "事务回滚必须成功");
        assertEquals(3, result.compensatedSteps(), "必须逆向补偿执行 3 条 Undo 语句");
        assertTrue(elapsedMs <= 15.0, "双向事务回滚耗时必须 <= 15ms，实测: " + elapsedMs + "ms");

        // 验证执行顺序为 LIFO 严格逆序
        List<String> executed = result.executedUndoSqls();
        assertTrue(executed.get(0).contains("t_coupon_usage"), "第 1 条回滚必须是最后执行的 coupon");
        assertTrue(executed.get(1).contains("t_inventory"), "第 2 条回滚必须是中间的 inventory");
        assertTrue(executed.get(2).contains("t_order"), "第 3 条回滚必须是最早的 order");
    }

    // =========================================================================
    // TC-139-7: 纯 Java 21 Record 凭单签名不可变性与 SHA-256 常量时间自验真
    // =========================================================================
    @Test
    @DisplayName("TC-139-7: 纯 Java 21 Record 凭单全字段不可变与 SHA-256 哈希常量时间自验真")
    void testDatabaseMcpTransactionReceipt_immutableVerification() {
        String tenantId = "tenant_enterprise_01";
        String origSql = "SELECT * FROM t_contract";
        String rewrittenSql = "SELECT * FROM t_contract WHERE tenant_id = 'tenant_enterprise_01' LIMIT 1000";
        String opType = "SELECT_READONLY";
        int undoCount = 0;
        boolean isRolledBack = false;
        double latencyMs = 0.45;

        DatabaseMcpTransactionReceipt receipt = DatabaseMcpTransactionReceipt.create(
                tenantId, origSql, rewrittenSql, opType, undoCount, isRolledBack, latencyMs
        );

        assertNotNull(receipt);
        assertTrue(receipt.receiptId().startsWith("RCP-DB-TX-"));
        assertEquals(64, receipt.sha256Signature().length(), "SHA-256 签名必须为 64 位字符");
        assertTrue(receipt.verifySignature(), "原始不可变凭单签名自验真必须 100% 成功");

        // 篡改测试
        DatabaseMcpTransactionReceipt tampered = new DatabaseMcpTransactionReceipt(
                receipt.receiptId(),
                "TENANT_MALICIOUS_TAMPERED",
                receipt.originalSql(),
                receipt.rewrittenSql(),
                receipt.operationType(),
                receipt.undoLogCount(),
                receipt.isRolledBack(),
                receipt.latencyMs(),
                receipt.timestamp(),
                receipt.sha256Signature()
        );
        assertFalse(tampered.verifySignature(), "篡改凭单字段后自验真必须严格返回 false");
    }

    // =========================================================================
    // TC-139-8: 端到端全链路闭环集成与防篡改签发
    // =========================================================================
    @Test
    @DisplayName("TC-139-8: 端到端闭环: SQL 接收 -> AST 沙箱预检 -> 租户与 LIMIT 重写 -> 捕获 Undo Log -> 模拟回滚 -> 凭单签发")
    void testEndToEndDatabaseMcp_fullPipelineIntegration() {
        String txId = "TX-E2E-FINANCIAL-PAY-008";
        String tenantId = "tenant_citic_bank";

        // 1. 尝试执行高危 DDL 攻击，被沙箱直接拦截
        String attackSql = "DROP TABLE t_core_account";
        SqlAuditResult attackRes = sandboxGovernor.auditAndRewriteSql(attackSql, tenantId, true);
        assertFalse(attackRes.allowed());

        // 2. 执行合法写操作并进入双向事务
        txAligner.beginTransaction(txId);
        String insertSql = "INSERT INTO t_bill (bill_id, amount) VALUES ('B9001', 12000)";
        SqlAuditResult insertAudit = sandboxGovernor.auditAndRewriteSql(insertSql, tenantId, true);
        assertTrue(insertAudit.allowed());

        // 捕获前置镜像并记录 Undo Log
        txAligner.recordInsert(txId, "t_bill", "bill_id", "B9001", insertAudit.rewrittenSql());

        // 3. 模拟后续微服务调用超时，触发事务回滚
        RollbackResult rollback = txAligner.executeRollback(txId);
        assertTrue(rollback.success());
        assertEquals(1, rollback.compensatedSteps());

        // 4. 签发不可变审计存证凭单
        DatabaseMcpTransactionReceipt receipt = DatabaseMcpTransactionReceipt.create(
                tenantId,
                insertSql,
                insertAudit.rewrittenSql(),
                "COMPENSATED_ROLLBACK",
                rollback.compensatedSteps(),
                true,
                rollback.latencyMs()
        );

        assertNotNull(receipt);
        assertTrue(receipt.verifySignature(), "端到端生成的事务存证凭单自验真必须 100% 成功");
    }
}
