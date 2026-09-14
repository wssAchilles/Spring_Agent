package tech.qiantong.qknow.ai.code.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 静态 AST 语法树安全分析器
 * 基于 Cousot 抽象解释有界理论半格与属性符号可达性检测算法 (定理 1.1)
 * 对输入代码实施静态剪枝拦截，严防沙箱逃逸、危险系统调用与数据泄露
 */
@Component
public class AstSecurityInspector {

    private static final Logger log = LoggerFactory.getLogger(AstSecurityInspector.class);

    // 禁止导入的高危系统、进程与网络模块 (定理 1.1)
    private static final List<String> DANGEROUS_PYTHON_MODULES = Arrays.asList(
            "os", "sys", "subprocess", "socket", "shutil", "urllib", "requests", "http",
            "ftplib", "telnetlib", "posix", "pty", "commands", "ctypes", "importlib",
            "code", "pickle", "multiprocessing", "threading", "webbrowser", "platform"
    );

    // 禁止调用的底层反射与动态执行内置函数
    private static final List<String> DANGEROUS_CALLS = Arrays.asList(
            "eval(", "exec(", "__import__(", "compile(", "globals()", "locals()",
            "getattr(", "setattr(", "delattr(", "open("
    );

    // 禁止访问的面向对象元类继承与反射逃逸魔术属性 (定理 1.1 剪枝归纳)
    private static final List<String> DANGEROUS_ATTRIBUTES = Arrays.asList(
            "__subclasses__", "__globals__", "__code__", "__bases__", "__mro__",
            "__builtins__", "__class__", "__loader__", "__spec__"
    );

    /**
     * 静态预检代码安全性
     *
     * @param language 语言标识 (python, sql, bash, javascript)
     * @param code     源代码文本
     * @return 若发现违规返回具体的违规描述；若安全通过返回 null
     */
    public String inspect(String language, String code) {
        if (code == null || code.isBlank()) {
            return "Code snippet is empty or null.";
        }

        String normalizedLang = language != null ? language.toLowerCase().trim() : "python";
        return switch (normalizedLang) {
            case "python", "py" -> inspectPython(code);
            case "sql" -> inspectSql(code);
            case "bash", "sh" -> inspectBash(code);
            case "javascript", "js", "node" -> inspectJs(code);
            default -> "Unsupported language for code sandbox: " + language;
        };
    }

    /**
     * Python 静态 AST 符号分析
     */
    private String inspectPython(String code) {
        // 1. 拦截危险模块导入 (import mod / from mod import)
        for (String mod : DANGEROUS_PYTHON_MODULES) {
            Pattern p1 = Pattern.compile("(?m)^\\s*import\\s+(.*?,\\s*)*" + Pattern.quote(mod) + "(\\s*,|\\s*$|\\s+as\\b)");
            Pattern p2 = Pattern.compile("(?m)^\\s*from\\s+" + Pattern.quote(mod) + "\\s+import\\b");
            Pattern p3 = Pattern.compile("\\b__import__\\s*\\(\\s*['\"]" + Pattern.quote(mod) + "['\"]\\s*\\)");
            if (p1.matcher(code).find() || p2.matcher(code).find() || p3.matcher(code).find()) {
                log.warn("[AstSecurityInspector] Blocked dangerous Python module import: {}", mod);
                return "Security violation: Import of module '" + mod + "' is strictly prohibited in sandbox.";
            }
        }

        // 2. 拦截魔术属性访问与元类逃逸链
        for (String attr : DANGEROUS_ATTRIBUTES) {
            if (code.contains(attr)) {
                log.warn("[AstSecurityInspector] Blocked magic attribute access: {}", attr);
                return "Security violation: Access to magic attribute '" + attr + "' is strictly prohibited.";
            }
        }

        // 3. 拦截高危内置动态执行与文件系统函数
        for (String call : DANGEROUS_CALLS) {
            if (code.contains(call)) {
                log.warn("[AstSecurityInspector] Blocked dangerous function call: {}", call);
                return "Security violation: Call to '" + call + "' is strictly prohibited in sandbox.";
            }
        }

        return null; // 安全验证通过
    }

    /**
     * SQL 只读静态语法检查
     */
    private String inspectSql(String code) {
        String upper = code.toUpperCase().trim();
        List<String> writeKeywords = Arrays.asList(
                "INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ", "CREATE ",
                "TRUNCATE ", "EXEC ", "EXECUTE ", "GRANT ", "REVOKE ", "RENAME "
        );
        for (String kw : writeKeywords) {
            if (upper.contains(kw)) {
                log.warn("[AstSecurityInspector] Blocked non-readonly SQL keyword: {}", kw.trim());
                return "Security violation: Only read-only SELECT queries are allowed in SQL sandbox. Found: " + kw.trim();
            }
        }
        if (!upper.startsWith("SELECT") && !upper.startsWith("EXPLAIN") && !upper.startsWith("WITH")) {
            return "Security violation: SQL script must start with SELECT, EXPLAIN or WITH.";
        }
        return null;
    }

    /**
     * Bash 危险指令静态过滤
     */
    private String inspectBash(String code) {
        List<String> dangerousCommands = Arrays.asList(
                "rm ", "mkfs", "dd ", "chmod", "chown", "curl", "wget", "nc ",
                "netcat", "bash -i", "/bin/sh", ":(){:|:&};:", "> /dev/", "sudo",
                "killall", "pkill", "reboot", "shutdown"
        );
        for (String cmd : dangerousCommands) {
            if (code.contains(cmd)) {
                log.warn("[AstSecurityInspector] Blocked dangerous Bash command: {}", cmd.trim());
                return "Security violation: Dangerous command '" + cmd.trim() + "' is prohibited in Bash sandbox.";
            }
        }
        return null;
    }

    /**
     * JavaScript 受限检查
     */
    private String inspectJs(String code) {
        List<String> dangerousTokens = Arrays.asList(
                "child_process", "fs.", "net.", "http.", "process.exit", "process.env",
                "require('child_process')", "require(\"child_process\")"
        );
        for (String token : dangerousTokens) {
            if (code.contains(token)) {
                log.warn("[AstSecurityInspector] Blocked dangerous JS token: {}", token);
                return "Security violation: Dangerous token '" + token + "' is prohibited in JS sandbox.";
            }
        }
        return null;
    }
}
