package tech.qiantong.qknow.hermes.tool.sandbox;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 企业级生产容器代码沙箱运行时核心契约
 * 对标 E2B 与 Google NsJail，支持严格硬件级/进程级配额与只读防护
 *
 * @author Achilles
 * @since 2026-10-13
 */
public interface EnterpriseCodeSandboxRuntime extends AutoCloseable {

    /**
     * 沙箱资源配额约束对象 (纯 Java 21 Record)
     */
    record SandboxResourceQuota(
            long timeoutLimitMs,          // 硬看门狗超时时间 (默认 5000ms)
            long maxMemoryBytes,          // 最大内存限制 (默认 256MB)
            long maxCpuTimeMicros,        // 最大 CPU 时间 (默认 3000ms)
            int maxOutputBytes,           // 最大标准输出截断 (默认 64KB)
            boolean enforceReadOnlyFs,    // 是否强制只读挂载宿主文件系统
            boolean disableNetwork        // 是否断开网络出站
    ) {
        public static SandboxResourceQuota standardDefault() {
            return new SandboxResourceQuota(5000L, 256L * 1024 * 1024, 3000_000L, 64 * 1024, true, true);
        }
    }

    /**
     * 异步在受限安全沙箱中执行脚本
     *
     * @param scriptPayload   待执行的 Python 或 Shell 脚本代码
     * @param scriptLanguage  语言类型 ("python", "bash", "sh")
     * @param quota           资源配额约束
     * @param sanitizedEnv    白名单安全环境变量 (严禁包含敏感凭证)
     * @return 包含资源度量与安全签名的不可变凭单
     */
    CompletableFuture<EnterpriseSandboxExecutionReceipt> executeSandboxedScriptAsync(
            String scriptPayload,
            String scriptLanguage,
            SandboxResourceQuota quota,
            Map<String, String> sanitizedEnv
    );

    /**
     * 探测当前宿主操作系统支持的沙箱隔离级别
     *
     * @return "HARDWARE_MICROVM", "LINUX_NSJAIL_CGROUPS", 或 "LOCAL_VIRTUAL_THREAD_WATCHDOG"
     */
    String detectIsolationLevel();
}
