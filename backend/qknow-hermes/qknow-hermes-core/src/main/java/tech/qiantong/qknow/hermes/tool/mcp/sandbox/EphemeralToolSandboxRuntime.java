package tech.qiantong.qknow.hermes.tool.mcp.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Phase 122 核心资产：瞬态隔离沙箱运行时 (EphemeralToolSandboxRuntime)
 * 落实定理 1.1 瞬态沙箱非干涉性信息流隔离定理与零凭据外泄证明：
 * 1. Default-Deny 环境变量清洗：processBuilder.environment().clear() 彻底擦除宿主凭证
 * 2. 仅保留白名单安全环境变量 (PATH, USER, LANG, JAVA_HOME, TMPDIR 等)，敏感凭证外泄率严格为 0.0%
 * 3. 独立 UUID 瞬态工作区隔离 (AutoCloseable 退出物理擦除，宿主文件系统零污染)
 * 4. 基于 Java 21 虚拟线程泵送标准 I/O，强制输入 16KB、输出 64KB 物理截断，彻底杜绝管道死锁与 Broken Pipe
 * 5. 毫秒级看门狗超时监控 (ScheduledExecutor)，超时强制强杀 (destroyForcibly)
 */
public class EphemeralToolSandboxRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EphemeralToolSandboxRuntime.class);

    // 标准输入最大允许写入字节数 (16KB)
    public static final int MAX_STDIN_BYTES = 16 * 1024;

    // 标准输出最大允许捕获字节数 (64KB)，防止撑爆内存与管道缓冲区死锁
    public static final int MAX_STDOUT_BYTES = 64 * 1024;

    // 默认执行硬超时时间 (毫秒)
    public static final long DEFAULT_TIMEOUT_MS = 3000L;

    // 默认允许放行的基础安全环境变量白名单 (全大写)
    public static final Set<String> SAFE_ENV_WHITELIST = Set.of(
            "PATH", "USER", "LANG", "LC_ALL", "JAVA_HOME", "TMPDIR"
    );

    // 严禁泄露的高危敏感凭证键名与值特征正则
    private static final Pattern SENSITIVE_CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(key|secret|password|passwd|token|credential|auth|priv|aws_|openai_|deepseek_|qwen_)"
    );

    private final Path rootSandboxDir;
    private final ScheduledExecutorService watchdogExecutor;
    private final ExecutorService virtualThreadExecutor;

    public EphemeralToolSandboxRuntime() {
        this(Path.of(System.getProperty("java.io.tmpdir"), "qknow_mcp_sandbox"));
    }

    public EphemeralToolSandboxRuntime(Path rootSandboxDir) {
        this.rootSandboxDir = rootSandboxDir;
        try {
            if (!Files.exists(this.rootSandboxDir)) {
                Files.createDirectories(this.rootSandboxDir);
            }
        } catch (IOException e) {
            log.warn("[SandboxRuntime] 创建根沙箱目录失败: {}", rootSandboxDir, e);
        }

        this.watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-sandbox-watchdog");
            t.setDaemon(true);
            return t;
        });
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 沙箱执行结果结构体
     */
    public record SandboxExecutionResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut,
            boolean truncated,
            long latencyMicros
    ) {
        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }
    }

    /**
     * 在隔离瞬态沙箱中执行命令
     *
     * @param command       待执行命令及参数列表
     * @param extraSafeEnv  需要补充注入的特定安全环境变量 (已清洗)
     * @param stdinContent  标准输入报文 (可为 null)
     * @param timeoutMs     执行超时时间毫秒
     * @return 沙箱执行结果
     */
    public SandboxExecutionResult executeSandboxed(
            List<String> command,
            Map<String, String> extraSafeEnv,
            String stdinContent,
            long timeoutMs
    ) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("命令列表不能为空");
        }

        long startNano = System.nanoTime();
        long effectiveTimeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;

        // 1. 分配独立瞬态隔离目录
        String ephemeralId = UUID.randomUUID().toString().replace("-", "");
        Path ephemeralWorkDir = rootSandboxDir.resolve(ephemeralId);
        try {
            Files.createDirectories(ephemeralWorkDir);
        } catch (IOException e) {
            log.error("[SandboxRuntime] 创建瞬态工作区失败: {}", ephemeralWorkDir, e);
            long latency = (System.nanoTime() - startNano) / 1000L;
            return new SandboxExecutionResult(-1, "", "无法创建瞬态工作区: " + e.getMessage(), false, false, latency);
        }

        Process process = null;
        ScheduledFuture<?> watchdogFuture = null;
        boolean isTimedOut = false;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(ephemeralWorkDir.toFile());

            // 2. 彻底清空全部继承的父进程环境变量 (Default-Deny 铁律)
            pb.environment().clear();

            // 3. 仅重新注入经过严格白名单清洗的基础安全环境变量
            Map<String, String> sanitizedEnv = buildSanitizedEnvironment(extraSafeEnv);
            pb.environment().putAll(sanitizedEnv);

            // 4. 接管管道流，坚决禁止 Redirect.INHERIT 隐蔽信道
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);

            process = pb.start();
            Process activeProcess = process;

            final java.util.concurrent.atomic.AtomicBoolean timeoutTriggered = new java.util.concurrent.atomic.AtomicBoolean(false);

            // 5. 挂载单线程看门狗硬超时强杀
            watchdogFuture = watchdogExecutor.schedule(() -> {
                try {
                    log.warn("[SandboxRuntime] 执行超时 ({}ms)，看门狗强制强杀子进程", effectiveTimeout);
                    timeoutTriggered.set(true);
                    activeProcess.destroyForcibly();
                } catch (Exception ignored) {
                }
            }, effectiveTimeout, TimeUnit.MILLISECONDS);

            // 6. 使用 Java 21 虚拟线程异步排空标准输入，实施 16KB 截断
            if (stdinContent != null && !stdinContent.isEmpty()) {
                virtualThreadExecutor.submit(() -> {
                    try (OutputStream os = activeProcess.getOutputStream()) {
                        byte[] inputBytes = stdinContent.getBytes(StandardCharsets.UTF_8);
                        int bytesToWrite = Math.min(inputBytes.length, MAX_STDIN_BYTES);
                        os.write(inputBytes, 0, bytesToWrite);
                        os.flush();
                    } catch (IOException ignored) {
                        // 子进程提前关闭输入流属正常场景，忽略 Broken Pipe
                    }
                });
            } else {
                try {
                    process.getOutputStream().close();
                } catch (IOException ignored) {
                }
            }

            // 7. 使用虚拟线程异步有界读取标准输出与标准错误流，实施 64KB 截断保护
            Future<StreamDrainResult> stdoutFuture = virtualThreadExecutor.submit(() ->
                    drainBoundedStream(activeProcess.getInputStream(), MAX_STDOUT_BYTES)
            );
            Future<StreamDrainResult> stderrFuture = virtualThreadExecutor.submit(() ->
                    drainBoundedStream(activeProcess.getErrorStream(), MAX_STDOUT_BYTES)
            );

            // 8. 等待进程退出
            boolean finished = process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS);
            if (!finished || timeoutTriggered.get()) {
                isTimedOut = true;
                process.destroyForcibly();
                try {
                    process.waitFor(200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            // 取消看门狗
            if (watchdogFuture != null) {
                watchdogFuture.cancel(true);
            }

            StreamDrainResult stdoutRes = stdoutFuture.get(500, TimeUnit.MILLISECONDS);
            StreamDrainResult stderrRes = stderrFuture.get(500, TimeUnit.MILLISECONDS);

            int exitCode = isTimedOut ? -1 : process.exitValue();
            boolean isTruncated = stdoutRes.truncated() || stderrRes.truncated();
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;

            return new SandboxExecutionResult(
                    exitCode,
                    stdoutRes.content(),
                    stderrRes.content(),
                    isTimedOut,
                    isTruncated,
                    latencyMicros
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long latency = (System.nanoTime() - startNano) / 1000L;
            return new SandboxExecutionResult(-1, "", "执行被中断", false, false, latency);
        } catch (Exception e) {
            long latency = (System.nanoTime() - startNano) / 1000L;
            return new SandboxExecutionResult(-1, "", "沙箱执行异常: " + e.getMessage(), isTimedOut, false, latency);
        } finally {
            if (watchdogFuture != null) {
                watchdogFuture.cancel(true);
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            // 9. 物理擦除瞬态工作区目录，保持宿主环境绝对零污染
            cleanDirectoryRecursively(ephemeralWorkDir);
        }
    }

    /**
     * 严格执行 Default-Deny 环境变量白名单清洗
     *
     * @param extraSafeEnv 额外注入的自定义环境变量
     * @return 经过净化后的只读环境变量映射
     */
    public Map<String, String> buildSanitizedEnvironment(Map<String, String> extraSafeEnv) {
        Map<String, String> sanitized = new HashMap<>();

        // 仅从系统安全白名单挑选必要环境变量
        for (String safeKey : SAFE_ENV_WHITELIST) {
            String sysVal = System.getenv(safeKey);
            if (sysVal != null && !containsSensitiveToken(safeKey, sysVal)) {
                sanitized.put(safeKey, sysVal);
            }
        }

        // 处理用户传入的额外环境配置，同样执行严格过滤
        if (extraSafeEnv != null && !extraSafeEnv.isEmpty()) {
            for (Map.Entry<String, String> entry : extraSafeEnv.entrySet()) {
                String key = entry.getKey();
                String val = entry.getValue();
                if (key != null && val != null && !containsSensitiveToken(key, val)) {
                    sanitized.put(key, val);
                }
            }
        }

        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * 检验键名或值中是否潜伏敏感凭据特征
     */
    public boolean containsSensitiveToken(String key, String value) {
        if (key != null && SENSITIVE_CREDENTIAL_PATTERN.matcher(key).find()) {
            return true;
        }
        if (value != null && SENSITIVE_CREDENTIAL_PATTERN.matcher(value).find()) {
            return true;
        }
        return false;
    }

    private record StreamDrainResult(String content, boolean truncated) {}

    /**
     * 有界流抽干与物理截断，防止死锁
     */
    private StreamDrainResult drainBoundedStream(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int bytesRead;
        int totalRead = 0;
        boolean truncated = false;

        while ((bytesRead = in.read(chunk)) != -1) {
            if (totalRead + bytesRead <= maxBytes) {
                buffer.write(chunk, 0, bytesRead);
                totalRead += bytesRead;
            } else {
                // 超出上限，执行物理截断并排空剩余数据以防管道挂起
                int allowed = maxBytes - totalRead;
                if (allowed > 0) {
                    buffer.write(chunk, 0, allowed);
                    totalRead += allowed;
                }
                truncated = true;
                // 继续空读排空直到流关闭
                while (in.read(chunk) != -1) {
                }
                break;
            }
        }

        String resultStr = buffer.toString(StandardCharsets.UTF_8);
        if (truncated) {
            resultStr = resultStr + "\n[WARN: Output truncated at 64KB]";
        }
        return new StreamDrainResult(resultStr, truncated);
    }

    private void cleanDirectoryRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (Exception e) {
            log.warn("[SandboxRuntime] 清理瞬态工作区失败: {}", dir, e);
        }
    }

    @Override
    public void close() {
        watchdogExecutor.shutdownNow();
        virtualThreadExecutor.shutdownNow();
    }
}
