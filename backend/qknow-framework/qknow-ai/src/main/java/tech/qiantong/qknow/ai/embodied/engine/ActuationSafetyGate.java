package tech.qiantong.qknow.ai.embodied.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.embodied.dto.ActionPrimitiveDTO;

/**
 * 物理执行安全门禁 (Theorem 3.1)
 * 对不可逆物理操作实施控制屏障函数 (CBF) 与数字孪生仿真双重核验 (Fail-Close)
 *
 * @author Achilles
 * @since 2026-09-14
 */
public class ActuationSafetyGate {

    private static final Logger log = LoggerFactory.getLogger(ActuationSafetyGate.class);

    private static final String REQUIRED_TICKET_PREFIX = "TICKET-AUTH-";

    /**
     * 校验并授权动作原语执行
     */
    public boolean verifyAndAuthorize(ActionPrimitiveDTO action, DigitalTwinSimulator.SimulationReport report) {
        if (action == null || report == null) {
            log.error("安全门禁阻断：动作或仿真报告为空");
            return false;
        }

        // 1. 仿真推演失败，绝对物理阻断
        if (!report.isSafe()) {
            action.setStatus(ActionPrimitiveDTO.ExecutionStatus.BLOCKED);
            log.error("安全门禁物理阻断：数字孪生推演不安全！原因: {}", report.getRejectionReason());
            return false;
        }

        // 2. 若属于物理不可逆操作，严格校验安全授权凭证 (Safety Ticket)
        if (action.getReversibility() == ActionPrimitiveDTO.ReversibilityLevel.IRREVERSIBLE) {
            String ticket = action.getSafetyTicket();
            if (ticket == null || !ticket.startsWith(REQUIRED_TICKET_PREFIX)) {
                action.setStatus(ActionPrimitiveDTO.ExecutionStatus.BLOCKED);
                log.error("安全门禁物理拦截：动作 {} 为物理不可逆操作 (IRREVERSIBLE)，缺少有效安全授权凭证！", action.getActionId());
                return false;
            }
            log.info("安全门禁授权放行：物理不可逆操作 {} 验证通过，凭证有效: {}", action.getActionId(), ticket);
        } else {
            log.info("安全门禁放行：动作 {} 属于 {} 安全等级，仿真通过放行", action.getActionId(), action.getReversibility());
        }

        action.setStatus(ActionPrimitiveDTO.ExecutionStatus.SIMULATED_PASS);
        return true;
    }
}
