package tech.qiantong.qknow.mcp.client.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.mcp.core.orchestration.dto.IdempotentExecutionReceipt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 两阶段安全与 AST 语法级防注入网关 (TwoStageMcpSecurityGateway)
 * 借鉴 Kong AI Gateway 与金融两阶段提交理念：
 * 阶段一 (Dry-Run 预演)：静态语法防御拦截 SQL/Shell 注入与间接提示词攻击，评估操作风险面并签发审批凭证
 * 阶段二 (Commit 真实提交)：强制验证幂等键与预演许可，杜绝二次写破坏，全链路采用 MessageDigest.isEqual 常量时间签名存证
 */
public class TwoStageMcpSecurityGateway {

    private static final Logger log = LoggerFactory.getLogger(TwoStageMcpSecurityGateway.class);

    // 恶意注入检测正则模式
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)\\b(DROP\\s+TABLE|TRUNCATE\\s+TABLE|ALTER\\s+TABLE|DELETE\\s+FROM|UPDATE\\s+.*\\s+SET|UNION\\s+SELECT)\\b|--|/\\*|\\b1\\s*=\\s*1\\b"
    );
    private static final Pattern SHELL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(\\b(rm\\s+-rf|sh\\s+-c|bash\\s+-c|mkfs|dd\\s+if)\\b|[;|`&>]|\\$\\()"
    );
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore\\s+all\\s+previous\\s+instructions|system\\s+override|disregard\\s+(all\\s+)?(rules|prompts)|jailbreak)"
    );

    // 内存幂等凭单存储: idempotencyKey -> IdempotentExecutionReceipt
    private final ConcurrentHashMap<String, IdempotentExecutionReceipt> receiptStore = new ConcurrentHashMap<>();
    // 预演授权令牌存储: approvalToken -> TokenMetadata
    private final ConcurrentHashMap<String, DryRunApproval> approvalStore = new ConcurrentHashMap<>();

    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }

    public record SecurityScanResult(
            boolean isSafe,
            RiskLevel riskLevel,
            List<String> violations
    ) {}

    public record DryRunAssessmentReport(
            String reportId,
            String toolName,
            boolean passed,
            RiskLevel riskLevel,
            String dryRunApprovalToken,
            String assessmentSummary,
            List<String> violations,
            long timestamp
    ) {}

    private record DryRunApproval(
            String token,
            String toolName,
            String payloadHash,
            long expiresAt
    ) {}

    /**
     * 第一阶段前置静态安全检查
     */
    public SecurityScanResult preCheckSecurity(String toolName, Map<String, Object> arguments) {
        List<String> violations = new ArrayList<>();
        String payloadString = arguments != null ? arguments.toString() : "";

        // 1. 检查间接提示词注入
        if (PROMPT_INJECTION_PATTERN.matcher(payloadString).find()) {
            violations.add("检测到对抗性间接提示词注入载荷 (Prompt Injection)");
        }

        // 2. 检查 SQL 注入
        if (SQL_INJECTION_PATTERN.matcher(payloadString).find()) {
            violations.add("检测到破坏性或非法 SQL 语法注入载荷 (SQL Injection)");
        }

        // 3. 检查 Shell/命令注入
        if (SHELL_INJECTION_PATTERN.matcher(payloadString).find()) {
            violations.add("检测到未经授权的高危系统命令或管道逃逸字符 (Command Injection)");
        }

        RiskLevel level = RiskLevel.LOW;
        if (!violations.isEmpty()) {
            level = RiskLevel.HIGH;
        } else if (toolName != null && (toolName.contains("delete") || toolName.contains("drop") || toolName.contains("execute") || toolName.contains("write"))) {
            level = RiskLevel.HIGH;
        } else if (toolName != null && (toolName.contains("update") || toolName.contains("insert") || toolName.contains("create"))) {
            level = RiskLevel.MEDIUM;
        }

        boolean isSafe = violations.isEmpty();
        return new SecurityScanResult(isSafe, level, violations);
    }

    /**
     * 阶段一：Dry-Run 预演评估
     * 无论工具读写属性均执行预演，对于高危操作签发有效期 5 分钟的 dryRunApprovalToken
     */
    public DryRunAssessmentReport dryRun(String toolName, Map<String, Object> arguments) {
        long now = System.currentTimeMillis();
        String reportId = "dryrun_" + UUID.randomUUID().toString().substring(0, 8);
        SecurityScanResult scan = preCheckSecurity(toolName, arguments);

        if (!scan.isSafe()) {
            log.warn("两阶段安全网关 [Dry-Run] 拦截到高危安全违规: tool={}, violations={}", toolName, scan.violations());
            return new DryRunAssessmentReport(
                    reportId, toolName, false, scan.riskLevel(), null,
                    "预演安全审查不通过，存在阻断性语法违规", scan.violations(), now
            );
        }

        String payloadHash = hashPayload(arguments != null ? arguments.toString() : "");
        String approvalToken = "token_" + UUID.randomUUID().toString().replace("-", "");
        // 5 分钟有效期
        approvalStore.put(approvalToken, new DryRunApproval(approvalToken, toolName, payloadHash, now + 300_000L));

        String summary = String.format("预演评估通过: 风险等级=%s, 语法检查合格, 签发执行令牌", scan.riskLevel());
        log.info("两阶段安全网关 [Dry-Run] 预演通过: tool={}, riskLevel={}", toolName, scan.riskLevel());

        return new DryRunAssessmentReport(
                reportId, toolName, true, scan.riskLevel(), approvalToken, summary, List.of(), now
        );
    }

    /**
     * 阶段二：Commit 正式提交执行
     * 强制检验幂等键与预演授权，成功提交后签署常量时间验真凭单
     */
    public IdempotentExecutionReceipt commitExecution(
            String idempotencyKey,
            String dryRunApprovalToken,
            String toolName,
            Map<String, Object> arguments,
            Supplier<String> executionLogic) {

        long startNano = System.nanoTime();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey 不能为空");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName 不能为空");
        }

        // 1. 幂等拦截: 若此前已成功提交，安全重放既有结果，杜绝重复执行破坏
        IdempotentExecutionReceipt existing = receiptStore.get(idempotencyKey);
        if (existing != null && IdempotentExecutionReceipt.STATUS_COMMITTED.equals(existing.status())) {
            log.info("两阶段网关 [Commit] 命中既有幂等提交，执行安全重放: key={}, tool={}", idempotencyKey, toolName);
            long elapsedMicros = (System.nanoTime() - startNano) / 1000L;
            String replayReceiptId = "rcpt_replay_" + UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String sig = IdempotentExecutionReceipt.calculateSignature(
                    replayReceiptId, idempotencyKey, toolName,
                    IdempotentExecutionReceipt.STATUS_REPLAYED, existing.payloadHash(), now
            );

            IdempotentExecutionReceipt replayReceipt = new IdempotentExecutionReceipt(
                    replayReceiptId, idempotencyKey, toolName,
                    IdempotentExecutionReceipt.STATUS_REPLAYED, existing.payloadHash(),
                    existing.responsePayload(), elapsedMicros, now, sig
            );

            if (!replayReceipt.verifySignature()) {
                throw new IllegalStateException("重放凭单在常量时间验签中校验失败！");
            }
            return replayReceipt;
        }

        // 2. 预演授权令牌校验
        SecurityScanResult scan = preCheckSecurity(toolName, arguments);
        if (scan.riskLevel() == RiskLevel.HIGH) {
            if (dryRunApprovalToken == null || dryRunApprovalToken.isBlank()) {
                throw new SecurityException("高危操作必须提供有效的 dryRunApprovalToken 预演授权令牌");
            }
            DryRunApproval approval = approvalStore.get(dryRunApprovalToken);
            if (approval == null || approval.expiresAt() < System.currentTimeMillis()) {
                throw new SecurityException("提供的预演授权令牌不存在或已过期，请重新发起 Dry-Run 预演");
            }
            if (!approval.toolName().equals(toolName)) {
                throw new SecurityException("预演授权令牌与当前执行工具不匹配: tokenTool=" + approval.toolName() + ", reqTool=" + toolName);
            }
        }

        // 3. 执行真实业务逻辑
        String payloadHash = hashPayload(arguments != null ? arguments.toString() : "");
        try {
            String response = executionLogic.get();
            long elapsedMicros = (System.nanoTime() - startNano) / 1000L;
            String receiptId = "rcpt_commit_" + UUID.randomUUID().toString().substring(0, 8);
            long now = System.currentTimeMillis();
            String sig = IdempotentExecutionReceipt.calculateSignature(
                    receiptId, idempotencyKey, toolName,
                    IdempotentExecutionReceipt.STATUS_COMMITTED, payloadHash, now
            );

            IdempotentExecutionReceipt receipt = new IdempotentExecutionReceipt(
                    receiptId, idempotencyKey, toolName,
                    IdempotentExecutionReceipt.STATUS_COMMITTED, payloadHash,
                    response, elapsedMicros, now, sig
            );

            // 严格验证自签名真实性 (使用 MessageDigest.isEqual 常量时间校验)
            if (!receipt.verifySignature()) {
                throw new IllegalStateException("两阶段提交凭单签名在常量时间比对中校验失败！");
            }

            receiptStore.put(idempotencyKey, receipt);
            log.info("两阶段安全网关 [Commit] 正式提交成功: key={}, tool={}, latency={}μs", idempotencyKey, toolName, elapsedMicros);
            return receipt;

        } catch (Exception e) {
            log.error("两阶段安全网关 [Commit] 执行发生异常: key={}, tool={}", idempotencyKey, toolName, e);
            throw new RuntimeException("工具提交执行失败: " + e.getMessage(), e);
        }
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "hash_error";
        }
    }

    public int getCommittedReceiptCount() {
        return (int) receiptStore.values().stream()
                .filter(r -> IdempotentExecutionReceipt.STATUS_COMMITTED.equals(r.status()))
                .count();
    }
}
