package tech.qiantong.qknow.mcp.client.safety;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高危工具沙箱安全门禁与二次审批中枢 (HighRiskToolSafetyGovernor)
 * 维护三级风险分类矩阵 (READ_ONLY, LOW_RISK, HIGH_RISK_DESTRUCTIVE)
 * 实施 RBAC 权限校验与破坏性操作 100% 物理挂起 + 人机二次审批 (HITL)
 */
@Slf4j
@Component
public class HighRiskToolSafetyGovernor {

    public enum RiskLevel {
        READ_ONLY,
        LOW_RISK,
        HIGH_RISK_DESTRUCTIVE
    }

    // 高危破坏性关键词集合（用于启发式安全兜底）
    private static final Set<String> DESTRUCTIVE_KEYWORDS = Set.of(
            "drop", "delete", "truncate", "rm", "format", "shutdown", "reboot", "kill", "purge"
    );

    // 在途待审批工单表
    private final Map<String, ApprovalTicketBO> pendingTickets = new ConcurrentHashMap<>();

    /**
     * 执行工具调用前的综合安全审查
     */
    public SafetyDecisionBO evaluateAndIntercept(
            String serverId,
            String toolName,
            Map<String, Object> arguments,
            RiskLevel designatedRisk,
            String operatorRole,
            String operatorUserId) {

        if (toolName == null || operatorRole == null) {
            throw new IllegalArgumentException("Tool name and operator role must not be null");
        }

        RiskLevel effectiveRisk = resolveRiskLevel(toolName, designatedRisk, arguments);

        // 1. RBAC 角色权限验真
        if (!hasRolePermission(operatorRole, effectiveRisk)) {
            String denyReason = String.format("RBAC 角色权限拒绝: 角色 [%s] 无权执行 [%s] 等级工具 [%s]",
                    operatorRole, effectiveRisk, toolName);
            log.warn("[SafetyGovernor] {}", denyReason);
            return SafetyDecisionBO.rejected("ERR_MCP_ACCESS_DENIED", denyReason, effectiveRisk);
        }

        // 2. 高危破坏性操作强制 HITL 物理挂起门禁
        if (effectiveRisk == RiskLevel.HIGH_RISK_DESTRUCTIVE) {
            String ticketId = "TICKET-HITL-" + UUID.randomUUID().toString().substring(0, 8);
            String argHash = computeArgumentHash(arguments);
            ApprovalTicketBO ticket = new ApprovalTicketBO(
                    ticketId,
                    serverId,
                    toolName,
                    argHash,
                    operatorUserId,
                    effectiveRisk,
                    System.currentTimeMillis(),
                    "PENDING"
            );
            pendingTickets.put(ticketId, ticket);
            String suspendReason = String.format("高危破坏性操作 [%s] 已被安全门禁物理挂起，工单号: %s，等待管理员审批",
                    toolName, ticketId);
            log.warn("[SafetyGovernor] {}", suspendReason);
            return SafetyDecisionBO.suspended(ticketId, suspendReason, effectiveRisk);
        }

        // 3. 只读与低危操作放行
        return SafetyDecisionBO.allowed(effectiveRisk);
    }

    /**
     * 提交管理员人机二次审批决策
     */
    public boolean submitApprovalDecision(String ticketId, boolean approved, String approverUserId) {
        if (ticketId == null || !pendingTickets.containsKey(ticketId)) {
            log.warn("[SafetyGovernor] 审批工单不存在或已过期: ticketId={}", ticketId);
            return false;
        }

        ApprovalTicketBO ticket = pendingTickets.remove(ticketId);
        log.info("[SafetyGovernor] 工单 {} 审批结果: approved={}, 审批人: {}",
                ticketId, approved, approverUserId);
        return approved;
    }

    /**
     * 判定有效风险等级（若指定了高危或工具名包含破坏性关键词，均升格为高危）
     */
    public RiskLevel resolveRiskLevel(String toolName, RiskLevel designatedRisk, Map<String, Object> arguments) {
        if (designatedRisk == RiskLevel.HIGH_RISK_DESTRUCTIVE) {
            return RiskLevel.HIGH_RISK_DESTRUCTIVE;
        }
        String lowerName = toolName.toLowerCase();
        for (String kw : DESTRUCTIVE_KEYWORDS) {
            if (lowerName.contains(kw)) {
                return RiskLevel.HIGH_RISK_DESTRUCTIVE;
            }
        }
        return designatedRisk != null ? designatedRisk : RiskLevel.LOW_RISK;
    }

    private boolean hasRolePermission(String role, RiskLevel risk) {
        // GUEST 仅可访问 READ_ONLY
        if ("ROLE_GUEST".equalsIgnoreCase(role)) {
            return risk == RiskLevel.READ_ONLY;
        }
        // OPERATOR 可访问 READ_ONLY 与 LOW_RISK
        if ("ROLE_OPERATOR".equalsIgnoreCase(role)) {
            return risk == RiskLevel.READ_ONLY || risk == RiskLevel.LOW_RISK;
        }
        // ADMIN 具备全量权限（但 HIGH_RISK 仍需走 HITL 流程）
        return "ROLE_ADMIN".equalsIgnoreCase(role) || "ROLE_SUPER_ADMIN".equalsIgnoreCase(role);
    }

    public static String computeArgumentHash(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // empty hash
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(arguments.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "unknown-hash";
        }
    }

    public record SafetyDecisionBO(
            boolean allowed,
            boolean approvalRequired,
            String ticketId,
            String rejectionCode,
            String reason,
            RiskLevel riskLevel
    ) {
        public static SafetyDecisionBO allowed(RiskLevel level) {
            return new SafetyDecisionBO(true, false, null, null, null, level);
        }

        public static SafetyDecisionBO suspended(String ticketId, String reason, RiskLevel level) {
            return new SafetyDecisionBO(false, true, ticketId, "ERR_MCP_HITL_APPROVAL_SUSPENDED", reason, level);
        }

        public static SafetyDecisionBO rejected(String code, String reason, RiskLevel level) {
            return new SafetyDecisionBO(false, false, null, code, reason, level);
        }
    }

    public record ApprovalTicketBO(
            String ticketId,
            String serverId,
            String toolName,
            String argumentHash,
            String requesterUserId,
            RiskLevel riskLevel,
            long requestTimestamp,
            String status
    ) {}
}
