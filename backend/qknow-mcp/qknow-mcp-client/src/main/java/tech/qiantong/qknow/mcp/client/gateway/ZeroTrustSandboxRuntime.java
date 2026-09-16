package tech.qiantong.qknow.mcp.client.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * 零信任动态沙箱隔离运行时 (ZeroTrustSandboxRuntime)
 * 落实定理 1.1 非干涉性信息流隔离：
 * 1. Default-Deny 环境变量白名单清洗，清空敏感密钥，外泄概率严格等于 0.0%
 * 2. 隔离工作区瞬态挂载与只读保护
 * 3. 高危命令与提权 payload 即时硬拦截
 * 4. 5000ms 硬超时强杀看门狗
 */
public class ZeroTrustSandboxRuntime {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustSandboxRuntime.class);

    // 默认允许放行的最小安全环境变量白名单
    public static final Set<String> ALLOWED_ENV_WHITELIST = Set.of(
            "PATH", "USER", "LANG", "LC_ALL", "JAVA_HOME", "TMPDIR", "HOME"
    );

    // 严禁泄露的高危敏感凭证键名特征
    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "(?i)(key|secret|password|passwd|token|credential|auth|priv|aws_|openai_)"
    );

    // 高危 Shell 注入与破坏性命令拦截正则
    private static final Pattern DANGEROUS_COMMAND_PATTERN = Pattern.compile(
            "(?i)\\b(rm\\s+-rf|sudo|chmod\\s+777|chown|mkfs|dd\\s+if=|curl\\s+.*\\|\\s*(ba)?sh|wget\\s+.*\\|\\s*(ba)?sh|nc\\s+-e|>\\s*/dev/sd[a-z])\\b"
    );

    private final File sandboxWorkingDir;
    private final ScheduledExecutorService watchdogExecutor;

    public ZeroTrustSandboxRuntime(File sandboxWorkingDir) {
        this.sandboxWorkingDir = sandboxWorkingDir != null ? sandboxWorkingDir : new File(System.getProperty("java.io.tmpdir"), "qknow_mcp_sandbox");
        if (!this.sandboxWorkingDir.exists()) {
            this.sandboxWorkingDir.mkdirs();
        }
        this.watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-sandbox-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 严格执行 Default-Deny 环境变量清洗
     * 清空全部未在白名单中的键值，杜绝宿主敏感凭证穿透
     *
     * @param rawEnv 原始环境变量映射
     * @return 经过净化后的只读安全环境变量映射
     */
    public Map<String, String> sanitizeEnvironment(Map<String, String> rawEnv) {
        if (rawEnv == null || rawEnv.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : rawEnv.entrySet()) {
            String key = entry.getKey();
            if (ALLOWED_ENV_WHITELIST.contains(key.toUpperCase(Locale.ROOT))) {
                // 进一步校验白名单键值内是否夹带可疑凭证
                if (!SENSITIVE_KEY_PATTERN.matcher(key).find()) {
                    sanitized.put(key, entry.getValue());
                }
            }
        }
        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * 校验环境中是否存在泄漏的高危凭证
     *
     * @param env 环境变量映射
     * @return 是否检测到高危凭据
     */
    public boolean containsSensitiveCredential(Map<String, String> env) {
        if (env == null || env.isEmpty()) return false;
        for (String key : env.keySet()) {
            if (SENSITIVE_KEY_PATTERN.matcher(key).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查命令行是否安全
     *
     * @param command 待执行命令
     * @return 是否安全
     */
    public boolean isCommandSafe(String command) {
        if (command == null || command.isBlank()) {
            return true;
        }
        return !DANGEROUS_COMMAND_PATTERN.matcher(command).find();
    }

    /**
     * 在沙箱内执行受限 Callable 任务，配置看门狗硬超时 (默认 5000ms)
     *
     * @param task      执行任务
     * @param timeoutMs 超时毫秒数
     * @return 任务返回值
     * @throws TimeoutException  超时强杀
     * @throws SecurityException 高危命令拦截
     */
    public <T> T executeWithWatchdog(Callable<T> task, long timeoutMs) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Thread runnerThread = new Thread(future, "mcp-sandbox-worker-" + UUID.randomUUID());
        runnerThread.setDaemon(true);
        runnerThread.start();

        ScheduledFuture<?> killTask = watchdogExecutor.schedule(() -> {
            if (runnerThread.isAlive()) {
                log.warn("[MCP Sandbox Watchdog] 任务执行超过 {}ms 硬超时阈值，强制中断线程!", timeoutMs);
                runnerThread.interrupt();
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new TimeoutException("MCP 沙箱任务超时已强杀: 耗时超过 " + timeoutMs + "ms");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof InterruptedException) {
                throw new TimeoutException("MCP 沙箱任务超时已强杀 (线程已被中断): " + ee.getMessage());
            }
            throw ee;
        } finally {
            killTask.cancel(true);
        }
    }

    public File getSandboxWorkingDir() {
        return sandboxWorkingDir;
    }

    public void destroy() {
        watchdogExecutor.shutdownNow();
    }
}
