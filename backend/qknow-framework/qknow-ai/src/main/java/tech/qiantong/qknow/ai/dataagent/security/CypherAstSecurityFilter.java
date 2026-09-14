package tech.qiantong.qknow.ai.dataagent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Neo4j Cypher 图查询语言只读安全防火墙与子句白名单校验器
 * 严格遵从推论 2.1（Cypher 语法只读同构不变量）
 */
@Component
public class CypherAstSecurityFilter {

    private static final Logger log = LoggerFactory.getLogger(CypherAstSecurityFilter.class);

    // 严禁包含的变异与写操作子句/关键词
    private static final List<String> DANGEROUS_CYPHER_KEYWORDS = Arrays.asList(
            "CREATE", "MERGE", "DELETE", "DETACH DELETE", "SET", "REMOVE",
            "DROP", "ALTER", "LOAD CSV", "PERIODIC COMMIT"
    );

    // 严禁调用的高危系统/写操作存储过程
    private static final Pattern DANGEROUS_PROCEDURE_PATTERN = Pattern.compile(
            "(?i)\\bCALL\\s+apoc\\.(export|import|system|custom|trigger|periodic)\\b"
    );

    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\bLIMIT\\s+(\\d+)\\b");

    /**
     * 校验并安全规范化 Cypher 查询
     */
    public String validateAndRewrite(String rawCypher) {
        if (rawCypher == null || rawCypher.isBlank()) {
            throw new AstSecurityException("EMPTY_CYPHER", "Cypher 查询语句不能为空", rawCypher);
        }

        String cleaned = cleanMarkdownBlocks(rawCypher).trim();

        // 1. 堆叠多语句检测 (禁止分号多语句)
        if (cleaned.contains(";")) {
            String[] segments = cleaned.split(";");
            int validCount = 0;
            for (String seg : segments) {
                if (!seg.trim().isEmpty()) validCount++;
            }
            if (validCount > 1) {
                log.warn("[CypherAstSecurityFilter] 拦截 Cypher 堆叠多语句攻击");
                throw new AstSecurityException("STACKED_CYPHER_DETECTED", "严禁执行多条分号堆叠的 Cypher 语句", cleaned);
            }
        }

        // 2. 检查危险变异关键字
        for (String kw : DANGEROUS_CYPHER_KEYWORDS) {
            Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(kw) + "\\b");
            if (pattern.matcher(cleaned).find()) {
                log.warn("[CypherAstSecurityFilter] 拦截 Cypher 变异写操作: 关键字={}", kw);
                throw new AstSecurityException("NON_READONLY_CYPHER", "只允许执行只读 Cypher MATCH/RETURN 查询，严禁执行 " + kw + " 操作", cleaned);
            }
        }

        // 3. 检查危险存储过程调用
        if (DANGEROUS_PROCEDURE_PATTERN.matcher(cleaned).find()) {
            log.warn("[CypherAstSecurityFilter] 拦截高危 APOC 过程调用");
            throw new AstSecurityException("DISALLOWED_PROCEDURE_CALL", "严禁调用可能具有写操作或系统风险的存储过程", cleaned);
        }

        // 4. 起始只读子句白名单校验
        String upper = cleaned.toUpperCase().trim();
        if (!upper.startsWith("MATCH") && !upper.startsWith("OPTIONAL MATCH") && !upper.startsWith("WITH") && !upper.startsWith("UNWIND")) {
            throw new AstSecurityException("INVALID_CYPHER_START", "Cypher 查询必须以 MATCH, OPTIONAL MATCH, WITH 或 UNWIND 只读子句起始", cleaned);
        }

        // 5. 强制注入 / 修正 LIMIT 1000
        Matcher limitMatcher = LIMIT_PATTERN.matcher(cleaned);
        if (!limitMatcher.find()) {
            cleaned = cleaned + " LIMIT 1000";
        } else {
            try {
                long currentLimit = Long.parseLong(limitMatcher.group(1));
                if (currentLimit > 1000 || currentLimit <= 0) {
                    cleaned = limitMatcher.replaceFirst("LIMIT 1000");
                }
            } catch (Exception e) {
                cleaned = limitMatcher.replaceFirst("LIMIT 1000");
            }
        }

        return cleaned;
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
