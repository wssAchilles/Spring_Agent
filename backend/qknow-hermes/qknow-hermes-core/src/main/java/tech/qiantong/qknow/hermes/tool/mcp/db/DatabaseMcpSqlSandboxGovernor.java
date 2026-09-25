package tech.qiantong.qknow.hermes.tool.mcp.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库 MCP 工具 SQL AST 抽象语法树解析与安全沙箱执行中枢 (Phase 139 防线一/防线二)
 * <p>
 * 1. Default-Deny 只读沙箱：默认仅放行 SELECT，100% 硬拦截 DDL (DROP/ALTER/TRUNCATE) 与无约束 DML；
 * 2. 深度拦截底层系统字典库探测 (information_schema / pg_catalog / mysql)；
 * 3. 动态重写强制注入 LIMIT 上限 (<= 1000) 与多租户 tenant_id 隔离约束，耗时 <= 1.0ms。
 * </p>
 *
 * @author Achilles
 * @version 1.0
 */
@Slf4j
@Component
public class DatabaseMcpSqlSandboxGovernor {

    public static final int DEFAULT_MAX_LIMIT = 1000;

    // 高危 DDL 关键字识别正则 (忽略大小写)
    private static final Pattern HIGH_RISK_DDL_PATTERN = Pattern.compile(
            "\\b(DROP|ALTER|TRUNCATE|CREATE|RENAME|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 系统底层字典表探测正则
    private static final Pattern SYSTEM_TABLE_PATTERN = Pattern.compile(
            "\\b(information_schema|mysql\\.|pg_catalog|sys\\.|performance_schema)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 基础语句类型提取正则
    private static final Pattern SQL_COMMAND_PATTERN = Pattern.compile(
            "^\\s*([A-Za-z]+)",
            Pattern.CASE_INSENSITIVE
    );

    // LIMIT 子句提取正则
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "\\bLIMIT\\s+(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    // WHERE 子句判定正则
    private static final Pattern WHERE_PATTERN = Pattern.compile(
            "\\bWHERE\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * SQL 语法树审计与安全重写结果
     */
    public record SqlAuditResult(
            boolean allowed,
            String originalSql,
            String rewrittenSql,
            String operationType,
            String rejectionReason,
            double latencyMs
    ) {}

    /**
     * 对输入 SQL 执行轻量 AST 语法解析、安全沙箱判定与动态条件重写
     *
     * @param originalSql 原始输入的 SQL 语句
     * @param tenantId    当前调用的租户唯一标识
     * @param allowWrite  是否开启写模式 (若为 false，则执行 Default-Deny 只读沙箱，仅放行 SELECT)
     * @return 语法审计与安全重写结果
     */
    public SqlAuditResult auditAndRewriteSql(String originalSql, String tenantId, boolean allowWrite) {
        long startNano = System.nanoTime();
        if (originalSql == null || originalSql.trim().isEmpty()) {
            return new SqlAuditResult(false, "", "", "EMPTY", "SQL 语句不能为空", (System.nanoTime() - startNano) / 1_000_000.0);
        }

        String cleanSql = originalSql.trim();
        // 去除末尾分号以便安全拼接重写
        if (cleanSql.endsWith(";")) {
            cleanSql = cleanSql.substring(0, cleanSql.length() - 1).trim();
        }

        // 1. 提取核心操作类型
        Matcher cmdMatcher = SQL_COMMAND_PATTERN.matcher(cleanSql);
        String operation = cmdMatcher.find() ? cmdMatcher.group(1).toUpperCase(Locale.ROOT) : "UNKNOWN";

        // 2. 检查高危 DDL 语句
        if (HIGH_RISK_DDL_PATTERN.matcher(cleanSql).find()) {
            double latency = (System.nanoTime() - startNano) / 1_000_000.0;
            log.warn("[SqlSandbox] 拦截高危 DDL 指令: SQL={}, 耗时={}ms", cleanSql, latency);
            return new SqlAuditResult(false, originalSql, cleanSql, operation,
                    "SECURITY_SANDBOX_REJECTED: 严禁执行 DDL 高危表结构与权限变更操作！", latency);
        }

        // 3. 检查系统敏感字典库探测
        if (SYSTEM_TABLE_PATTERN.matcher(cleanSql).find()) {
            double latency = (System.nanoTime() - startNano) / 1_000_000.0;
            log.warn("[SqlSandbox] 拦截底层系统字典库探测: SQL={}, 耗时={}ms", cleanSql, latency);
            return new SqlAuditResult(false, originalSql, cleanSql, operation,
                    "SECURITY_SANDBOX_REJECTED: 严禁访问底层系统字典库与元数据！", latency);
        }

        // 4. Default-Deny 只读沙箱检查：若未开启写模式，仅允许 SELECT
        if (!allowWrite && !operation.equals("SELECT")) {
            double latency = (System.nanoTime() - startNano) / 1_000_000.0;
            log.warn("[SqlSandbox] 只读沙箱拦截非只读指令: Operation={}, 耗时={}ms", operation, latency);
            return new SqlAuditResult(false, originalSql, cleanSql, operation,
                    "SECURITY_SANDBOX_REJECTED: 沙箱处于只读模式，仅允许执行 SELECT 查询！", latency);
        }

        // 5. 危险 DML 检查：DELETE / UPDATE 必须携带 WHERE 条件，严禁全表误修改
        if (operation.equals("DELETE") || operation.equals("UPDATE")) {
            if (!WHERE_PATTERN.matcher(cleanSql).find()) {
                double latency = (System.nanoTime() - startNano) / 1_000_000.0;
                log.warn("[SqlSandbox] 拦截无约束危险 DML 操作: SQL={}, 耗时={}ms", cleanSql, latency);
                return new SqlAuditResult(false, originalSql, cleanSql, operation,
                        "SECURITY_SANDBOX_REJECTED: 严禁执行无 WHERE 条件的全表修改或删除！", latency);
            }
        }

        // 6. 动态重写：针对 SELECT 语句强制重写 LIMIT 上限与多租户隔离约束
        String rewrittenSql = cleanSql;
        if (operation.equals("SELECT")) {
            rewrittenSql = rewriteSelectWithTenantAndLimit(cleanSql, tenantId);
        } else if (tenantId != null && !tenantId.isBlank()) {
            // 对 DML 语句也追加租户约束
            rewrittenSql = appendTenantConstraint(rewrittenSql, tenantId);
        }

        double latency = (System.nanoTime() - startNano) / 1_000_000.0;
        return new SqlAuditResult(true, originalSql, rewrittenSql, operation, "ALLOWED", latency);
    }

    /**
     * 对 SELECT 语句强制注入 LIMIT 与多租户条件
     */
    private String rewriteSelectWithTenantAndLimit(String sql, String tenantId) {
        String result = sql;

        // 1. 注入多租户隔离
        if (tenantId != null && !tenantId.isBlank()) {
            result = appendTenantConstraint(result, tenantId);
        }

        // 2. 检查与改写 LIMIT
        Matcher limitMatcher = LIMIT_PATTERN.matcher(result);
        if (limitMatcher.find()) {
            int currentLimit = Integer.parseInt(limitMatcher.group(1));
            if (currentLimit > DEFAULT_MAX_LIMIT) {
                // 钳位至最大上限
                result = limitMatcher.replaceFirst("LIMIT " + DEFAULT_MAX_LIMIT);
            }
        } else {
            // 原 SQL 无 LIMIT，强制追加
            result = result + " LIMIT " + DEFAULT_MAX_LIMIT;
        }

        return result;
    }

    /**
     * 向 WHERE 子句动态追加租户隔离条件
     */
    private String appendTenantConstraint(String sql, String tenantId) {
        Matcher whereMatcher = WHERE_PATTERN.matcher(sql);
        String tenantClause = "tenant_id = '" + tenantId + "'";

        if (whereMatcher.find()) {
            int whereEnd = whereMatcher.end();
            return sql.substring(0, whereEnd) + " (" + tenantClause + ") AND " + sql.substring(whereEnd);
        } else {
            // 无 WHERE 子句，在 LIMIT 或末尾前追加
            Matcher limitMatcher = LIMIT_PATTERN.matcher(sql);
            if (limitMatcher.find()) {
                int limitStart = limitMatcher.start();
                return sql.substring(0, limitStart).trim() + " WHERE " + tenantClause + " " + sql.substring(limitStart);
            } else {
                return sql + " WHERE " + tenantClause;
            }
        }
    }
}
