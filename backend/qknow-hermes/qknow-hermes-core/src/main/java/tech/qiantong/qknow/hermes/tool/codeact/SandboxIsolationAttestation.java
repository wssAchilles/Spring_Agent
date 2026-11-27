package tech.qiantong.qknow.hermes.tool.codeact;

import java.util.Objects;

/**
 * 沙盒隔离能力诚实存证契约（Java 21 不可变 Record）。
 * <p>
 * 彻底根治旧版虚报 {@code readOnlyFsEnforced/networkIsolationEnforced} 的学术不端风险：
 * 仅当宿主实际存在并执行 {@code nsjail} 时方声明硬件/内核级只读断网隔离，macOS 或普通进程诚实标注实际隔离级别。
 * </p>
 */
public record SandboxIsolationAttestation(
        String isolationLevel,
        SecurityLatticeLevel effectiveLatticeLevel,
        boolean readOnlyFsEnforced,
        boolean networkIsolationEnforced,
        boolean envDefaultDenyApplied,
        boolean astInspectorPassed,
        String osName
) {
    public SandboxIsolationAttestation {
        Objects.requireNonNull(isolationLevel, "isolationLevel 不能为空");
        Objects.requireNonNull(effectiveLatticeLevel, "effectiveLatticeLevel 不能为空");
        osName = osName != null ? osName : System.getProperty("os.name", "unknown");
    }
}
