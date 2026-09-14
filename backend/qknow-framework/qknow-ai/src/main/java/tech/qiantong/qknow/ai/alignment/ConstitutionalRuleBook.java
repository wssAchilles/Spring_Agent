package tech.qiantong.qknow.ai.alignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 不可变宪政伦理原则树 (定理 1.2: 宪政安全超平面隔离不变量)
 */
@Component
public class ConstitutionalRuleBook {

    private static final Logger log = LoggerFactory.getLogger(ConstitutionalRuleBook.class);

    public enum PrincipleTier {
        ABSOLUTE_REDLINE(1),      // 绝对红线 (如恶意越权、数据外发破坏，100% 阻断)
        BUSINESS_COMPLIANCE(2),   // 业务合规 (如范围超限、缺审计参数，需投影拉回修补)
        EFFICIENCY_GUIDELINE(3);  // 效率指导 (如多余空循环)

        private final int priority;

        PrincipleTier(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }

    public record ConstitutionalPrinciple(
            String id,
            String name,
            PrincipleTier tier,
            String description,
            List<String> forbiddenKeywords,
            double violationPenalty
    ) {}

    private final List<ConstitutionalPrinciple> principles;

    public ConstitutionalRuleBook() {
        List<ConstitutionalPrinciple> list = new ArrayList<>();
        list.add(new ConstitutionalPrinciple(
                "CR-001",
                "DATA_CONFIDENTIALITY",
                PrincipleTier.ABSOLUTE_REDLINE,
                "严禁越权外发敏感数据或全表拖库",
                List.of("DUMP_DB", "EXFILTRATE_CREDENTIALS", "LEAK_PII", "DROP TABLE", "SELECT * FROM sys_user_secrets"),
                1.0
        ));
        list.add(new ConstitutionalPrinciple(
                "CR-002",
                "NO_UNAUTHORIZED_MUTATION",
                PrincipleTier.ABSOLUTE_REDLINE,
                "严禁未经授权修改内核配置或绕过权限控制",
                List.of("OVERWRITE_KERNEL_CONFIG", "BYPASS_RBAC", "SHUTDOWN_CLUSTER_UNAUTH", "KILL_SAFETY_WATCHDOG"),
                1.0
        ));
        list.add(new ConstitutionalPrinciple(
                "CR-003",
                "FACTUAL_FAITHFULNESS",
                PrincipleTier.BUSINESS_COMPLIANCE,
                "严禁伪造不存在的事实验证证据",
                List.of("FABRICATE_FALSE_EVIDENCE", "HALLUCINATE_CITATIONS"),
                0.6
        ));
        list.add(new ConstitutionalPrinciple(
                "CR-004",
                "STRICT_ACCESS_CONTROL",
                PrincipleTier.BUSINESS_COMPLIANCE,
                "访问必须携带租户鉴权票据与合法范围声明",
                List.of("DISABLE_SECURITY_LOGS", "SUDO_ESCALATION"),
                0.5
        ));
        this.principles = Collections.unmodifiableList(list);
    }

    /**
     * 检查动作是否触犯宪法原则，返回优先级最高的违规条目
     */
    public Optional<ConstitutionalPrinciple> checkViolation(String actionName, Map<String, Object> parameters) {
        if (actionName == null) return Optional.empty();
        String upperAction = actionName.toUpperCase();

        StringBuilder sb = new StringBuilder(upperAction);
        if (parameters != null) {
            for (Object v : parameters.values()) {
                if (v != null) {
                    sb.append(" ").append(v.toString().toUpperCase());
                }
            }
        }
        String payload = sb.toString();

        return principles.stream()
                .filter(p -> p.forbiddenKeywords().stream().anyMatch(kw -> payload.contains(kw.toUpperCase())))
                .min(Comparator.comparingInt(p -> p.tier().getPriority()));
    }

    public List<ConstitutionalPrinciple> getPrinciples() {
        return principles;
    }
}
