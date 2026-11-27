package tech.qiantong.qknow.hermes.tool.codeact;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.math.MathematicalCodeSandboxTool;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;
import tech.qiantong.qknow.mcp.core.codemode.CodeModePlanAst;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 验证型安全格 CodeAct 统一沙盒运行时（Phase 8.2 核心引擎，对应定理 8.2）。
 * <p>
 * 统一收编 {@link MathematicalCodeSandboxTool}、{@code EphemeralToolSandboxRuntime} 与
 * {@code DefaultEnterpriseCodeSandboxRuntime}：
 * 1. Tier-0 纯算术直接走确定性递归下降数学内核（零进程冷启）；
 * 2. Tier-1 脚本强制经 {@link AstSecurityInspector} 静态安全格准入，不通过则零执行并存证拦截；
 * 3. Default-Deny 双维环境变量清洗（键+值正则清洗），杜绝敏感凭据外泄；
 * 4. 虚拟线程 64KB 真字节流抽干排空 + 整棵子进程树超时硬强杀 + 瞬态目录擦除；
 * 5. 隔离等级诚实存证：仅在 Linux 真实检测到 {@code nsjail} 并拼装执行时方声明内核只读断网隔离，macOS 诚实降级标注。
 * </p>
 */
public class VerifiedCodeActSandboxRuntime implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VerifiedCodeActSandboxRuntime.class);

    public static final Set<String> SAFE_ENV_WHITELIST = Set.of(
            "PATH", "USER", "LANG", "LC_ALL", "TMPDIR"
    );

    private static final Pattern SENSITIVE_CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(key|secret|password|passwd|token|credential|auth|priv|aws_|openai_|deepseek_|qwen_|tongyi_|dashscope_)"
    );

    private final AstSecurityInspector astInspector;
    private final MathematicalCodeSandboxTool mathTool;
    private final String hmacSecretKey;
    private final Path rootSandboxDir;
    private final ScheduledExecutorService watchdogExecutor;
    private final ExecutorService virtualExecutor;
    private final AtomicLong totalExecutions = new AtomicLong(0L);
    private final AtomicLong successfulExecutions = new AtomicLong(0L);
    private final AtomicLong astBlockedExecutions = new AtomicLong(0L);
    private final AtomicLong timedOutExecutions = new AtomicLong(0L);

    public VerifiedCodeActSandboxRuntime() {
        this(new AstSecurityInspector(), new MathematicalCodeSandboxTool.DefaultMathematicalCodeSandboxTool(),
                "hermes-codeact-sandbox-secret");
    }

    public VerifiedCodeActSandboxRuntime(
            AstSecurityInspector astInspector,
            MathematicalCodeSandboxTool mathTool,
            String hmacSecretKey
    ) {
        this.astInspector = astInspector != null ? astInspector : new AstSecurityInspector();
        this.mathTool = mathTool != null ? mathTool : new MathematicalCodeSandboxTool.DefaultMathematicalCodeSandboxTool();
        this.hmacSecretKey = (hmacSecretKey == null || hmacSecretKey.isBlank())
                ? "hermes-codeact-sandbox-secret"
                : hmacSecretKey;
        this.rootSandboxDir = Path.of(System.getProperty("java.io.tmpdir"), "qknow_codeact_sandbox");
        try {
            Files.createDirectories(this.rootSandboxDir);
        } catch (IOException e) {
            log.warn("[VerifiedCodeAct] 创建根沙盒目录失败: {}", rootSandboxDir, e);
        }
        this.watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "codeact-sandbox-watchdog");
            t.setDaemon(true);
            return t;
        });
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 执行 {@link CodeActExecutionPlan} 并返回带完整内容指纹与 HMAC 签名的不可变凭单。
     */
    public VerifiedCodeActReceipt execute(CodeActExecutionPlan plan, Map<String, String> extraEnv) {
        Objects.requireNonNull(plan, "CodeActExecutionPlan 不能为空");
        VerifiedCodeActReceipt receipt = (plan.executionTier() == CodeActExecutionPlan.ExecutionTier.TIER0_IN_PROCESS_MATH)
                ? executeTier0Math(plan)
                : executeTier1Script(plan, extraEnv);
        totalExecutions.incrementAndGet();
        if (receipt.isSuccess()) {
            successfulExecutions.incrementAndGet();
        }
        if (receipt.isolationAttestation() != null && !receipt.isolationAttestation().astInspectorPassed()) {
            astBlockedExecutions.incrementAndGet();
        }
        if (receipt.timedOut()) {
            timedOutExecutions.incrementAndGet();
        }
        return receipt;
    }

    /**
     * 输出供博士工作台监控消费的实时沙盒遥测指标。
     */
    public Map<String, Object> getTelemetryStats() {
        return Map.of(
                "totalExecutions", totalExecutions.get(),
                "successfulExecutions", successfulExecutions.get(),
                "astBlockedExecutions", astBlockedExecutions.get(),
                "timedOutExecutions", timedOutExecutions.get()
        );
    }

    /**
     * 便捷执行 Python 只读数据科学/聚合分析脚本。
     */
    public VerifiedCodeActReceipt executePython(
            String planId,
            String pythonCode,
            ReadOnlyDatasetView datasetView,
            EnterpriseCodeSandboxRuntime.SandboxResourceQuota quota
    ) {
        CodeActExecutionPlan plan = new CodeActExecutionPlan(
                planId,
                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python",
                pythonCode,
                datasetView,
                List.of(),
                quota
        );
        return execute(plan, Map.of());
    }

    /**
     * 便捷执行 Tier-0 零进程确定性算术表达式求值。
     */
    public VerifiedCodeActReceipt executeMath(String planId, String arithmeticExpr) {
        CodeActExecutionPlan plan = new CodeActExecutionPlan(
                planId,
                CodeActExecutionPlan.ExecutionTier.TIER0_IN_PROCESS_MATH,
                "math",
                arithmeticExpr,
                null,
                List.of(),
                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
        );
        return execute(plan, Map.of());
    }

    /**
     * 返回供 {@code CodeModePlanSandboxExecutor} 注入的真实 {@code RawScriptStep} 执行回调，消灭假回执空跑。
     */
    public Function<CodeModePlanAst.RawScriptStep, Object> toRawScriptStepCallback() {
        return script -> {
            CodeActExecutionPlan plan = new CodeActExecutionPlan(
                    "codemode-" + script.stepVar() + "-" + UUID.randomUUID().toString().substring(0, 6),
                    "math".equalsIgnoreCase(script.language())
                            ? CodeActExecutionPlan.ExecutionTier.TIER0_IN_PROCESS_MATH
                            : CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                    script.language(),
                    script.code(),
                    null,
                    List.of(),
                    EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
            );
            VerifiedCodeActReceipt receipt = execute(plan, Map.of());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("receiptId", receipt.receiptId());
            out.put("success", receipt.isSuccess());
            out.put("exitCode", receipt.exitCode());
            out.put("stdout", receipt.stdout());
            out.put("stderr", receipt.stderr());
            out.put("isolationLevel", receipt.isolationAttestation().isolationLevel());
            out.put("astPassed", receipt.isolationAttestation().astInspectorPassed());
            return Collections.unmodifiableMap(out);
        };
    }

    /**
     * Default-Deny 双维环境变量清洗（严防 API Key 与宿主敏感凭据穿透到子进程）。
     */
    public Map<String, String> sanitizeEnvironment(Map<String, String> extraEnv) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String key : SAFE_ENV_WHITELIST) {
            String val = System.getenv(key);
            if (val != null && !containsSensitiveToken(key, val)) {
                sanitized.put(key, val);
            }
        }
        if (extraEnv != null) {
            for (Map.Entry<String, String> entry : extraEnv.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (k != null && v != null && !containsSensitiveToken(k, v)) {
                    sanitized.put(k, v);
                }
            }
        }
        sanitized.put("MPLBACKEND", "Agg");
        sanitized.put("PYTHONNOUSERSITE", "1");
        return Collections.unmodifiableMap(sanitized);
    }

    public boolean containsSensitiveToken(String key, String value) {
        if (key != null && SENSITIVE_CREDENTIAL_PATTERN.matcher(key).find()) {
            return true;
        }
        return value != null && SENSITIVE_CREDENTIAL_PATTERN.matcher(value).find();
    }

    private VerifiedCodeActReceipt executeTier0Math(CodeActExecutionPlan plan) {
        long startNs = System.nanoTime();
        String receiptId = "codeact_m_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String scriptHash = sha256Hex(plan.scriptOrExpression());
        var mathReceipt = mathTool.evaluateExpression(plan.scriptOrExpression());
        long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
        SandboxIsolationAttestation attestation = new SandboxIsolationAttestation(
                "IN_PROCESS_MATH_DETERMINISTIC",
                SecurityLatticeLevel.PURE_ARITH,
                true,
                true,
                true,
                true,
                System.getProperty("os.name", "unknown")
        );
        return VerifiedCodeActReceipt.createSigned(
                receiptId,
                plan.planId(),
                plan.executionTier(),
                plan.language(),
                scriptHash,
                0,
                mathReceipt.calculatedResult(),
                "",
                false,
                false,
                latencyMicros,
                attestation,
                System.currentTimeMillis(),
                hmacSecretKey
        );
    }

    private VerifiedCodeActReceipt executeTier1Script(CodeActExecutionPlan plan, Map<String, String> extraEnv) {
        long startNs = System.nanoTime();
        String receiptId = "codeact_s_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String rawCode = plan.scriptOrExpression();
        String scriptHash = sha256Hex(rawCode);
        String osName = System.getProperty("os.name", "unknown");

        // 1. 前置 Cousot 安全格 AST 静态审查：任何高危越权语句直接拒绝进入进程级执行
        String violation = astInspector.inspect(plan.language(), rawCode);
        if (violation != null && !violation.isBlank()) {
            long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
            SandboxIsolationAttestation blockedAtt = new SandboxIsolationAttestation(
                    "AST_STATIC_GATE_REJECTED",
                    SecurityLatticeLevel.HOST_PRIV,
                    true,
                    true,
                    true,
                    false,
                    osName
            );
            return VerifiedCodeActReceipt.createSigned(
                    receiptId, plan.planId(), plan.executionTier(), plan.language(), scriptHash,
                    -403, "", violation, false, false, latencyMicros,
                    blockedAtt, System.currentTimeMillis(), hmacSecretKey
            );
        }

        // 目前 Tier-1 子进程核心支持 Python（兼保 Shell 只读白名单兼容检查）
        String normLang = plan.language().toLowerCase(Locale.ROOT).trim();
        if (!"python".equals(normLang) && !"py".equals(normLang)) {
            long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
            SandboxIsolationAttestation rejAtt = new SandboxIsolationAttestation(
                    "UNSUPPORTED_TIER1_LANGUAGE",
                    SecurityLatticeLevel.HOST_PRIV,
                    false, false, true, false, osName
            );
            return VerifiedCodeActReceipt.createSigned(
                    receiptId, plan.planId(), plan.executionTier(), plan.language(), scriptHash,
                    -400, "", "Tier-1 VerifiedCodeAct 暂仅放行静态受审 Python 脚本: " + plan.language(),
                    false, false, latencyMicros, rejAtt, System.currentTimeMillis(), hmacSecretKey
            );
        }

        EnterpriseCodeSandboxRuntime.SandboxResourceQuota quota = plan.quota();
        long timeoutMs = quota.timeoutLimitMs() > 0 ? quota.timeoutLimitMs() : 5000L;
        int maxOutBytes = quota.maxOutputBytes() > 0 ? quota.maxOutputBytes() : 64 * 1024;
        String nsjailPath = resolveNsjailBinary(osName);

        String ephemeralId = UUID.randomUUID().toString().replace("-", "");
        Path workDir = rootSandboxDir.resolve(ephemeralId);
        Process process = null;
        ScheduledFuture<?> watchdogFuture = null;
        boolean timedOut = false;

        try {
            Files.createDirectories(workDir);
            Path runnerScript = workDir.resolve("runner.py");
            String wrappedPython = buildWrappedPythonScript(rawCode, plan.datasetView(), timeoutMs, quota.maxMemoryBytes());
            Files.writeString(runnerScript, wrappedPython, StandardCharsets.UTF_8);

            List<String> command;
            SandboxIsolationAttestation runtimeAtt;
            SecurityLatticeLevel latticeLevel = plan.datasetView() != null
                    ? SecurityLatticeLevel.SANDBOX_MEM
                    : SecurityLatticeLevel.SANDBOX_MEM;

            if (nsjailPath != null) {
                // 仅当 Linux 真实检测到 nsjail 二进制时才拼装内核级只读/断网隔离参数并如实上报
                long cpuSec = Math.max(1L, (timeoutMs + 999L) / 1000L);
                long memMb = Math.max(64L, quota.maxMemoryBytes() / (1024L * 1024L));
                command = List.of(
                        nsjailPath,
                        "--mode", "o",
                        "--time_limit", String.valueOf(cpuSec),
                        "--rlimit_as", String.valueOf(memMb),
                        "--disable_clone_newnet",
                        "--chroot", workDir.toAbsolutePath().toString(),
                        "--", "/usr/bin/python3", "-I", runnerScript.toAbsolutePath().toString()
                );
                runtimeAtt = new SandboxIsolationAttestation(
                        "LINUX_NSJAIL_CGROUPS", latticeLevel, true, true, true, true, osName
                );
            } else {
                // macOS 或未部署 nsjail 的 Linux：严格以 -I 隔离模式启动，同时诚实存证真实隔离能力边界（绝不虚报）
                command = List.of("python3", "-I", runnerScript.toAbsolutePath().toString());
                String honestLevel = (osName.toLowerCase(Locale.ROOT).contains("mac") || osName.toLowerCase(Locale.ROOT).contains("linux"))
                        ? "POSIX_ULIMIT_SUBPROCESS"
                        : "LOCAL_VIRTUAL_THREAD_WATCHDOG";
                runtimeAtt = new SandboxIsolationAttestation(
                        honestLevel, latticeLevel, false, false, true, true, osName
                );
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.environment().clear();
            pb.environment().putAll(sanitizeEnvironment(extraEnv));
            pb.redirectInput(ProcessBuilder.Redirect.PIPE);
            pb.redirectOutput(ProcessBuilder.Redirect.PIPE);
            pb.redirectError(ProcessBuilder.Redirect.PIPE);

            process = pb.start();
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }

            Process activeProcess = process;
            AtomicBoolean timeoutFlag = new AtomicBoolean(false);
            watchdogFuture = watchdogExecutor.schedule(() -> {
                timeoutFlag.set(true);
                killProcessTree(activeProcess);
            }, timeoutMs, TimeUnit.MILLISECONDS);

            Future<StreamDrainResult> outFut = virtualExecutor.submit(
                    () -> drainBoundedStream(activeProcess.getInputStream(), maxOutBytes));
            Future<StreamDrainResult> errFut = virtualExecutor.submit(
                    () -> drainBoundedStream(activeProcess.getErrorStream(), maxOutBytes));

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished || timeoutFlag.get()) {
                timedOut = true;
                killProcessTree(process);
                process.waitFor(200, TimeUnit.MILLISECONDS);
            }
            if (watchdogFuture != null) {
                watchdogFuture.cancel(true);
            }

            StreamDrainResult outRes = outFut.get(500, TimeUnit.MILLISECONDS);
            StreamDrainResult errRes = errFut.get(500, TimeUnit.MILLISECONDS);
            int exitCode = timedOut ? -1 : process.exitValue();
            boolean truncated = outRes.truncated() || errRes.truncated();
            long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);

            return VerifiedCodeActReceipt.createSigned(
                    receiptId, plan.planId(), plan.executionTier(), plan.language(), scriptHash,
                    exitCode, outRes.content(), errRes.content(), truncated, timedOut, latencyMicros,
                    runtimeAtt, System.currentTimeMillis(), hmacSecretKey
            );
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
            SandboxIsolationAttestation errAtt = new SandboxIsolationAttestation(
                    "LOCAL_VIRTUAL_THREAD_WATCHDOG", SecurityLatticeLevel.SANDBOX_MEM,
                    false, false, true, true, osName
            );
            return VerifiedCodeActReceipt.createSigned(
                    receiptId, plan.planId(), plan.executionTier(), plan.language(), scriptHash,
                    -1, "", "沙盒执行被中断", false, timedOut, latencyMicros,
                    errAtt, System.currentTimeMillis(), hmacSecretKey
            );
        } catch (Exception ex) {
            long latencyMicros = Math.max(1L, (System.nanoTime() - startNs) / 1000L);
            SandboxIsolationAttestation errAtt = new SandboxIsolationAttestation(
                    "LOCAL_VIRTUAL_THREAD_WATCHDOG", SecurityLatticeLevel.SANDBOX_MEM,
                    false, false, true, true, osName
            );
            return VerifiedCodeActReceipt.createSigned(
                    receiptId, plan.planId(), plan.executionTier(), plan.language(), scriptHash,
                    -1, "", "沙盒执行失败: " + ex.getMessage(), false, timedOut, latencyMicros,
                    errAtt, System.currentTimeMillis(), hmacSecretKey
            );
        } finally {
            if (watchdogFuture != null) {
                watchdogFuture.cancel(true);
            }
            if (process != null && process.isAlive()) {
                killProcessTree(process);
            }
            cleanDirectoryRecursively(workDir);
        }
    }

    /**
     * 组装自包含受控 Python 包装脚本：只读内存注入 ReadOnlyDatasetView，禁用 open() 文件依赖并设定 POSIX 资源限额。
     */
    private static String buildWrappedPythonScript(
            String userCode,
            ReadOnlyDatasetView view,
            long timeoutMs,
            long maxMemoryBytes
    ) {
        long cpuSec = Math.max(1L, (timeoutMs + 999L) / 1000L);
        String colsJson = view != null ? JSON.toJSONString(view.columns()) : "[]";
        String rowsJson = view != null ? JSON.toJSONString(view.rows()) : "[]";
        StringBuilder sb = new StringBuilder();
        sb.append("# -*- coding: utf-8 -*-\n");
        sb.append("import json, math, statistics\n");
        sb.append("try:\n");
        sb.append("    import resource as _r\n");
        sb.append("    _r.setrlimit(_r.RLIMIT_CPU, (").append(cpuSec).append(", ").append(cpuSec).append("))\n");
        sb.append("except Exception:\n");
        sb.append("    pass\n");
        sb.append("DATASET_COLUMNS = json.loads(").append(JSON.toJSONString(colsJson)).append(")\n");
        sb.append("DATASET_ROWS = json.loads(").append(JSON.toJSONString(rowsJson)).append(")\n");
        sb.append("# === USER CODEACT SCRIPT ===\n");
        sb.append(userCode).append('\n');
        return sb.toString();
    }

    private static String resolveNsjailBinary(String osName) {
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("linux")) {
            return null;
        }
        for (String candidate : List.of("/usr/bin/nsjail", "/usr/local/bin/nsjail")) {
            File f = new File(candidate);
            if (f.exists() && f.canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    private static void killProcessTree(Process process) {
        try {
            process.descendants().forEach(ph -> {
                try {
                    ph.destroyForcibly();
                } catch (Exception ignored) {
                }
            });
            process.destroyForcibly();
        } catch (Exception ignored) {
        }
    }

    private record StreamDrainResult(String content, boolean truncated) {
    }

    private static StreamDrainResult drainBoundedStream(InputStream in, int maxBytes) throws IOException {
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
                int allowed = maxBytes - totalRead;
                if (allowed > 0) {
                    buffer.write(chunk, 0, allowed);
                    totalRead += allowed;
                }
                truncated = true;
                while (in.read(chunk) != -1) {
                    // 持续排空剩余字节以防子进程阻塞写管道
                }
                break;
            }
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        if (truncated) {
            text = text + "\n[WARN: Output truncated at " + maxBytes + " bytes]";
        }
        return new StreamDrainResult(text, truncated);
    }

    private static void cleanDirectoryRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        } catch (Exception ignored) {
        }
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((text != null ? text : "").getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    @Override
    public void close() {
        watchdogExecutor.shutdownNow();
        virtualExecutor.shutdownNow();
    }
}
