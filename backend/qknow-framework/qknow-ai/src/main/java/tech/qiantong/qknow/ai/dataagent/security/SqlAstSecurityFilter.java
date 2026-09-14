package tech.qiantong.qknow.ai.dataagent.security;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 生产级多源 SQL 抽象语法树 (AST) 只读安全防火墙与 LIMIT 1000 强制重写注入器
 * 严格遵从定理 2.1（只读 AST 隔离不变量定理）
 */
@Component
public class SqlAstSecurityFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlAstSecurityFilter.class);
    public static final long MAX_ALLOWED_LIMIT = 1000L;

    /**
     * 对输入 SQL 进行严格只读安全校验与 LIMIT 强制重写
     *
     * @param rawSql 大模型初次生成或经过修正的原始 SQL
     * @return 经过 AST 深度验证且安全重写后的 SQL 字符串
     * @throws AstSecurityException 若检测到多语句堆叠、非只读操作或语法解析错误
     */
    public String validateAndRewrite(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new AstSecurityException("EMPTY_SQL", "SQL 语句不能为空", rawSql);
        }

        String cleanedSql = cleanMarkdownBlocks(rawSql).trim();

        // 1. 词法与语法解析
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(cleanedSql);
        } catch (JSQLParserException e) {
            log.warn("[SqlAstSecurityFilter] 语法解析失败: {}", e.getMessage());
            throw new AstSecurityException("SYNTAX_ERROR", "SQL 语法解析失败: " + e.getMessage(), cleanedSql);
        }

        // 2. 堆叠多语句 (Stacked Queries) 检测: 必须且只能为单一语句
        List<Statement> stmtList = statements.getStatements();
        if (stmtList == null || stmtList.isEmpty()) {
            throw new AstSecurityException("EMPTY_STATEMENT", "未解析到有效 SQL 语句", cleanedSql);
        }
        if (stmtList.size() > 1) {
            log.warn("[SqlAstSecurityFilter] 拦截堆叠多语句攻击: 语句数量={}", stmtList.size());
            throw new AstSecurityException("STACKED_QUERIES_DETECTED", "严禁执行分号堆叠的多条 SQL 语句", cleanedSql);
        }

        Statement statement = stmtList.get(0);

        // 3. 严格只读判定: 顶级语句必须且只能为 Select 语句
        if (!(statement instanceof Select selectStmt)) {
            String stmtType = statement.getClass().getSimpleName();
            log.warn("[SqlAstSecurityFilter] 拦截非 SELECT 只读写操作/DDL: 节点类型={}", stmtType);
            throw new AstSecurityException("NON_READONLY_OPERATION", "只允许执行只读 SELECT 查询，严禁执行 " + stmtType + " 变异操作", cleanedSql);
        }

        // 4. 深度 PlainSelect / SetOperationList 校验与 LIMIT 1000 强制重写
        if (selectStmt instanceof PlainSelect plainSelect) {
            enforcePlainSelectLimit(plainSelect);
        } else if (selectStmt instanceof SetOperationList setOperationList) {
            // 对 UNION 等复合查询的最外层注入 LIMIT
            Limit limit = setOperationList.getLimit();
            if (limit == null) {
                Limit newLimit = new Limit();
                newLimit.setRowCount(new LongValue(MAX_ALLOWED_LIMIT));
                setOperationList.setLimit(newLimit);
            }
        }

        return selectStmt.toString();
    }

    private void enforcePlainSelectLimit(PlainSelect plainSelect) {
        // 检查是否有 INTO 子句 (如 SELECT ... INTO OUTFILE / TABLE)
        if (plainSelect.getIntoTables() != null && !plainSelect.getIntoTables().isEmpty()) {
            throw new AstSecurityException("INTO_TABLE_MUTATION", "严禁在 SELECT 中使用 INTO 变异输出到外部表或文件", plainSelect.toString());
        }

        Limit limit = plainSelect.getLimit();
        if (limit == null) {
            Limit newLimit = new Limit();
            newLimit.setRowCount(new LongValue(MAX_ALLOWED_LIMIT));
            plainSelect.setLimit(newLimit);
        } else {
            // 如果已存在 Limit 且超过阈值，强制截断为 1000
            try {
                if (limit.getRowCount() instanceof LongValue longVal) {
                    if (longVal.getValue() > MAX_ALLOWED_LIMIT || longVal.getValue() <= 0) {
                        limit.setRowCount(new LongValue(MAX_ALLOWED_LIMIT));
                    }
                }
            } catch (Exception ignored) {
                limit.setRowCount(new LongValue(MAX_ALLOWED_LIMIT));
            }
        }
    }

    private String cleanMarkdownBlocks(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline != -1) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }
}
