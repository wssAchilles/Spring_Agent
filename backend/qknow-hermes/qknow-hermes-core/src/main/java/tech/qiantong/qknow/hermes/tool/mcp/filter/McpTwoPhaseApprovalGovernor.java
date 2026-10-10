package tech.qiantong.qknow.hermes.tool.mcp.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生产级 MCP 工具调用两阶段审批网关 (McpTwoPhaseApprovalGovernor)
 * 建立 L1-L4 四级风险研判矩阵，对破坏性与高敏感操作实施挂起拦截与审批放行验证。
 */
public class McpTwoPhaseApprovalGovernor {

    private static final Logger log = LoggerFactory.getLogger(McpTwoPhaseApprovalGovernor.class);

    public enum RiskGrade {
        L1_SAFE,         // 只读查询、幂等检索 (直接放行)
        L2_LOW,          // 低风险参数配置修改 (记录审计后放行)
        L3_MEDIUM,       // 关键业务数据写入/更新 (需二次确认)
        L4_DESTRUCTIVE   // 高危破坏性操作：删除、转账、提权、系统命令 (强制人工审批)
    }

    public record ApprovalTicket(
            String ticketId,
            String toolName,
            RiskGrade riskGrade,
            Map<String, Object> arguments,
            long requestTimeEpochMs,
            String status,       // PENDING, APPROVED, REJECTED
            String operatorId
    ) {}

    // key: ticketId -> 审批票据
    private final Map<String, ApprovalTicket> ticketStore = new ConcurrentHashMap<>();

    /**
     * 评估工具调用的风险等级
     */
    public RiskGrade evaluateRisk(String toolName, Map<String, Object> arguments) {
        if (toolName == null) {
            return RiskGrade.L1_SAFE;
        }
        String lower = toolName.toLowerCase();
        if (lower.contains("delete") || lower.contains("drop") || lower.contains("truncate")
                || lower.contains("exec") || lower.contains("shutdown") || lower.contains("transfer")) {
            return RiskGrade.L4_DESTRUCTIVE;
        }
        if (lower.contains("update") || lower.contains("modify") || lower.contains("grant") || lower.contains("revoke")) {
            return RiskGrade.L3_MEDIUM;
        }
        if (lower.contains("create") || lower.contains("insert") || lower.contains("write")) {
            return RiskGrade.L2_LOW;
        }
        return RiskGrade.L1_SAFE;
    }

    /**
     * 判断是否需要发起审批挂起
     */
    public boolean requiresApproval(RiskGrade riskGrade) {
        return riskGrade == RiskGrade.L3_MEDIUM || riskGrade == RiskGrade.L4_DESTRUCTIVE;
    }

    /**
     * 发起两阶段审批申请
     */
    public ApprovalTicket requestApproval(String toolName, Map<String, Object> arguments, RiskGrade grade) {
        String ticketId = "APPR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ApprovalTicket ticket = new ApprovalTicket(
                ticketId, toolName, grade, arguments != null ? arguments : Map.of(),
                System.currentTimeMillis(), "PENDING", null
        );
        ticketStore.put(ticketId, ticket);
        log.warn("[MCP Approval] 拦截高危工具执行，已生成待审批工单: ticketId={}, tool={}, riskGrade={}",
                ticketId, toolName, grade);
        return ticket;
    }

    /**
     * 人工审批决定
     */
    public ApprovalTicket submitDecision(String ticketId, boolean approved, String operatorId) {
        ApprovalTicket ticket = ticketStore.get(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("未找到对应的审批工单: " + ticketId);
        }

        String newStatus = approved ? "APPROVED" : "REJECTED";
        ApprovalTicket updated = new ApprovalTicket(
                ticket.ticketId(), ticket.toolName(), ticket.riskGrade(),
                ticket.arguments(), ticket.requestTimeEpochMs(), newStatus, operatorId
        );
        ticketStore.put(ticketId, updated);
        log.info("[MCP Approval] 审批决定已生效: ticketId={}, status={}, operator={}", ticketId, newStatus, operatorId);
        return updated;
    }

    /**
     * 校验工单是否已通过审批放行
     */
    public boolean isApproved(String ticketId) {
        ApprovalTicket ticket = ticketStore.get(ticketId);
        return ticket != null && "APPROVED".equals(ticket.status());
    }

    public ApprovalTicket getTicket(String ticketId) {
        return ticketStore.get(ticketId);
    }
}
