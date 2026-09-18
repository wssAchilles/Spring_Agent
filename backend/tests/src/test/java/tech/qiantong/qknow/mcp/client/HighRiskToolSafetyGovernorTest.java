package tech.qiantong.qknow.mcp.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor;
import tech.qiantong.qknow.mcp.client.safety.HighRiskToolSafetyGovernor.RiskLevel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HighRiskToolSafetyGovernor 高危工具安全门禁与二次审批测试")
class HighRiskToolSafetyGovernorTest {

    private HighRiskToolSafetyGovernor governor;

    @BeforeEach
    void setUp() {
        governor = new HighRiskToolSafetyGovernor();
    }

    @Test
    @DisplayName("只读与低危工具在合法角色权限下直接放行")
    void governor_readOnlyAndLowRisk_authorizedForOperator() {
        var decision1 = governor.evaluateAndIntercept(
                "srv-db",
                "get_user_info",
                Map.of("userId", "U1001"),
                RiskLevel.READ_ONLY,
                "ROLE_OPERATOR",
                "user-op-01"
        );
        assertTrue(decision1.allowed());
        assertFalse(decision1.approvalRequired());
        assertEquals(RiskLevel.READ_ONLY, decision1.riskLevel());

        var decision2 = governor.evaluateAndIntercept(
                "srv-cache",
                "set_user_temp_flag",
                Map.of("key", "k1", "val", "v1"),
                RiskLevel.LOW_RISK,
                "ROLE_OPERATOR",
                "user-op-01"
        );
        assertTrue(decision2.allowed());
        assertFalse(decision2.approvalRequired());
        assertEquals(RiskLevel.LOW_RISK, decision2.riskLevel());
    }

    @Test
    @DisplayName("越权访问被 RBAC 鉴权机制物理阻断 (ACCESS_DENIED)")
    void governor_guestRoleUnauthorized_accessDenied() {
        var decision = governor.evaluateAndIntercept(
                "srv-db",
                "modify_system_config",
                Map.of("timeout", 5000),
                RiskLevel.LOW_RISK,
                "ROLE_GUEST", // 访客无权修改配置
                "guest-user"
        );
        assertFalse(decision.allowed());
        assertEquals("ERR_MCP_ACCESS_DENIED", decision.rejectionCode());
        assertTrue(decision.reason().contains("RBAC 角色权限拒绝"));
    }

    @Test
    @DisplayName("消融实验：高危破坏性操作（删库/删表）100% 物理拦截并生成待审批工单")
    void governor_destructiveOperation_suspendedForApproval() {
        // 包含 drop 破坏性关键词的工具
        var decision = governor.evaluateAndIntercept(
                "srv-prod-db",
                "drop_table_customers",
                Map.of("tableName", "t_customers", "cascade", true),
                RiskLevel.HIGH_RISK_DESTRUCTIVE,
                "ROLE_ADMIN",
                "admin-01"
        );

        assertFalse(decision.allowed(), "破坏性操作绝对禁止直接静默执行");
        assertTrue(decision.approvalRequired(), "必须标记需要人机二次审批");
        assertEquals("ERR_MCP_HITL_APPROVAL_SUSPENDED", decision.rejectionCode());
        assertNotNull(decision.ticketId(), "必须生成全局唯一审批工单号");
        assertTrue(decision.ticketId().startsWith("TICKET-HITL-"));
        assertEquals(RiskLevel.HIGH_RISK_DESTRUCTIVE, decision.riskLevel());
    }

    @Test
    @DisplayName("HITL 审批工单流转验证：管理员批准与驳回生命周期")
    void governor_hitlApprovalLifecycle_approvedOrRejected() {
        // 1. 触发挂起工单
        var decision = governor.evaluateAndIntercept(
                "srv-shell",
                "purge_system_logs",
                Map.of("path", "/var/log/app"),
                RiskLevel.HIGH_RISK_DESTRUCTIVE,
                "ROLE_ADMIN",
                "operator-02"
        );
        String ticketId = decision.ticketId();
        assertNotNull(ticketId);

        // 2. 模拟管理员人工电子签名批准
        boolean approveResult = governor.submitApprovalDecision(ticketId, true, "security-director-01");
        assertTrue(approveResult, "批准工单应返回 true");

        // 3. 工单已被消费，不可重复提交
        boolean secondAttempt = governor.submitApprovalDecision(ticketId, true, "security-director-01");
        assertFalse(secondAttempt, "已消费工单再次提交应返回 false");

        // 4. 模拟另一个工单并驳回
        var decisionReject = governor.evaluateAndIntercept(
                "srv-finance",
                "format_disk_array",
                Map.of(),
                RiskLevel.HIGH_RISK_DESTRUCTIVE,
                "ROLE_ADMIN",
                "operator-03"
        );
        boolean rejectResult = governor.submitApprovalDecision(decisionReject.ticketId(), false, "security-director-01");
        assertFalse(rejectResult, "驳回工单应返回 false");
    }
}
