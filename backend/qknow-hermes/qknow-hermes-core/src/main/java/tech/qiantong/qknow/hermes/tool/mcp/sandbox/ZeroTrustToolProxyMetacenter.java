package tech.qiantong.qknow.hermes.tool.mcp.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Phase 122 核心资产：企业级 MCP 零信任工具代理总控中枢 (ZeroTrustToolProxyMetacenter)
 * 落实第五演进阶段生产级企业 MCP 工具生态安全基石：
 * 1. 统一受控网关：所有智能体调用工具必须通过零信任代理中枢统一分发与隔离；
 * 2. 细粒度多租户配额与权限管控：针对每个租户动态绑定安全策略与无锁 CAS 令牌桶限流器；
 * 3. 递归同态模式与 AST 防注入审查：调用入参 100% 深度检查，阻断命令注入、路径穿越与间接提示词注入；
 * 4. 瞬态隔离沙箱运行时联动：环境变量彻底清洗 (Default-Deny)，虚拟线程有界 I/O 防死锁，看门狗超时强杀；
 * 5. 密码学存证与自验真凭单：全链路执行结果自动签发不可变 {@link McpSecuritySandboxReceipt}。
 */
public class ZeroTrustToolProxyMetacenter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustToolProxyMetacenter.class);

    // 默认租户验真防伪密钥
    public static final String DEFAULT_TENANT_SECRET = "qknow_zero_trust_mcp_secret_phase122";

    /**
     * 租户安全策略模型
     */
    public record TenantSecurityPolicy(
            String tenantId,
            String secretKey,
            long rateLimitCapacity,
            double refillRatePerSecond,
            Set<String> allowedTools,
            boolean enabled
    ) {
        public static TenantSecurityPolicy defaultPolicy(String tenantId) {
            return defaultPolicy(tenantId, DEFAULT_TENANT_SECRET);
        }

        public static TenantSecurityPolicy defaultPolicy(String tenantId, String secretKey) {
            return new TenantSecurityPolicy(tenantId, secretKey, 100L, 50.0, Set.of("*"), true);
        }

        public boolean isToolAllowed(String toolName) {
            if (!enabled) {
                return false;
            }
            if (allowedTools == null || allowedTools.isEmpty()) {
                return false;
            }
            return allowedTools.contains("*") || allowedTools.contains(toolName);
        }
    }

    /**
     * 工具安全执行请求对象
     */
    public record ToolExecutionRequest(
            String tenantId,
            String toolName,
            String callerAgent,
            Map<String, Object> arguments,
            Set<String> requiredParams,
            List<String> commandToExecute,
            Map<String, String> extraSafeEnv,
            String stdinContent,
            long timeoutMs
    ) {
        public ToolExecutionRequest {
            Objects.requireNonNull(tenantId, "租户标识 tenantId 不能为空");
            Objects.requireNonNull(toolName, "工具名称 toolName 不能为空");
            Objects.requireNonNull(callerAgent, "调用智能体标识 callerAgent 不能为空");
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private String toolName;
            private String callerAgent = "agent-hermes-core";
            private Map<String, Object> arguments = Collections.emptyMap();
            private Set<String> requiredParams = Collections.emptySet();
            private List<String> commandToExecute = Collections.emptyList();
            private Map<String, String> extraSafeEnv = Collections.emptyMap();
            private String stdinContent;
            private long timeoutMs = EphemeralToolSandboxRuntime.DEFAULT_TIMEOUT_MS;

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder toolName(String toolName) {
                this.toolName = toolName;
                return this;
            }

            public Builder callerAgent(String callerAgent) {
                this.callerAgent = callerAgent;
                return this;
            }

            public Builder arguments(Map<String, Object> arguments) {
                this.arguments = arguments != null ? arguments : Collections.emptyMap();
                return this;
            }

            public Builder requiredParams(Set<String> requiredParams) {
                this.requiredParams = requiredParams != null ? requiredParams : Collections.emptySet();
                return this;
            }

            public Builder commandToExecute(List<String> commandToExecute) {
                this.commandToExecute = commandToExecute != null ? commandToExecute : Collections.emptyList();
                return this;
            }

            public Builder extraSafeEnv(Map<String, String> extraSafeEnv) {
                this.extraSafeEnv = extraSafeEnv != null ? extraSafeEnv : Collections.emptyMap();
                return this;
            }

            public Builder stdinContent(String stdinContent) {
                this.stdinContent = stdinContent;
                return this;
            }

            public Builder timeoutMs(long timeoutMs) {
                this.timeoutMs = timeoutMs;
                return this;
            }

            public ToolExecutionRequest build() {
                return new ToolExecutionRequest(
                        tenantId, toolName, callerAgent, arguments, requiredParams,
                        commandToExecute, extraSafeEnv, stdinContent, timeoutMs
                );
            }
        }
    }

    /**
     * 工具安全执行响应对象
     */
    public record ToolExecutionResponse(
            McpSecuritySandboxReceipt receipt,
            boolean success,
            int exitCode,
            String stdout,
            String stderr,
            long latencyMicros
    ) {
        public static ToolExecutionResponse blocked(McpSecuritySandboxReceipt receipt) {
            return new ToolExecutionResponse(
                    receipt, false, -1, "", receipt.blockedReason(), receipt.latencyMicros()
            );
        }

        public static ToolExecutionResponse fromSandbox(
                McpSecuritySandboxReceipt receipt,
                EphemeralToolSandboxRuntime.SandboxExecutionResult result
        ) {
            return new ToolExecutionResponse(
                    receipt, result.isSuccess(), result.exitCode(),
                    result.stdout(), result.stderr(), receipt.latencyMicros()
            );
        }

        public static ToolExecutionResponse dryRunPassed(McpSecuritySandboxReceipt receipt) {
            return new ToolExecutionResponse(
                    receipt, true, 0, "PASSED_DRY_RUN", "", receipt.latencyMicros()
            );
        }
    }

    // 租户策略配置映射表
    private final ConcurrentMap<String, TenantSecurityPolicy> tenantPolicies = new ConcurrentHashMap<>();

    // 租户无锁 CAS 令牌桶限流器映射表
    private final ConcurrentMap<String, LockFreeTokenBucketLimiter> tenantLimiters = new ConcurrentHashMap<>();

    // 动态 Schema 模式检查与 AST 防注入器
    private final DeepSchemaSecurityInspector schemaInspector;

    // 瞬态沙箱隔离运行时
    private final EphemeralToolSandboxRuntime sandboxRuntime;

    public ZeroTrustToolProxyMetacenter() {
        this(new DeepSchemaSecurityInspector(), new EphemeralToolSandboxRuntime());
    }

    public ZeroTrustToolProxyMetacenter(DeepSchemaSecurityInspector schemaInspector, EphemeralToolSandboxRuntime sandboxRuntime) {
        this.schemaInspector = Objects.requireNonNull(schemaInspector, "模式校验器不能为空");
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "沙箱运行时不能为空");
    }

    /**
     * 注册或更新租户安全策略
     *
     * @param policy 租户安全策略
     */
    public void registerTenantPolicy(TenantSecurityPolicy policy) {
        Objects.requireNonNull(policy, "租户安全策略不能为空");
        tenantPolicies.put(policy.tenantId(), policy);
        // 同步更新或初始化限流器
        tenantLimiters.put(policy.tenantId(), new LockFreeTokenBucketLimiter(
                policy.rateLimitCapacity(), policy.refillRatePerSecond()
        ));
        log.info("[ZeroTrustMetacenter] 租户策略更新完成: tenantId={}, capacity={}, refillRate={}",
                policy.tenantId(), policy.rateLimitCapacity(), policy.refillRatePerSecond());
    }

    /**
     * 获取指定租户策略
     *
     * @param tenantId 租户标识
     * @return 租户安全策略 (未找到返回 null)
     */
    public TenantSecurityPolicy getTenantPolicy(String tenantId) {
        return tenantPolicies.get(tenantId);
    }

    /**
     * 获取租户当前可用令牌配额
     *
     * @param tenantId 租户标识
     * @return 可用令牌数，租户不存在返回 0
     */
    public long getAvailableTokens(String tenantId) {
        LockFreeTokenBucketLimiter limiter = tenantLimiters.get(tenantId);
        return limiter != null ? limiter.getAvailableTokens() : 0L;
    }

    /**
     * 重置租户限流令牌桶
     *
     * @param tenantId 租户标识
     */
    public void resetTenantLimiter(String tenantId) {
        LockFreeTokenBucketLimiter limiter = tenantLimiters.get(tenantId);
        if (limiter != null) {
            limiter.reset();
        }
    }

    /**
     * 零信任代理核心执行入口：端到端受控安全执行
     *
     * @param request 执行请求对象
     * @return 包含不可变存证凭单与执行产物的响应对象
     */
    public ToolExecutionResponse executeToolSecurely(ToolExecutionRequest request) {
        long startNano = System.nanoTime();
        String receiptId = "rcpt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String sandboxId = "sbx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long timestampEpochMs = System.currentTimeMillis();

        // 1. 租户身份与权限鉴权 (Step 1: Zero-Trust Tenant Authorization)
        TenantSecurityPolicy policy = tenantPolicies.get(request.tenantId());
        if (policy == null || !policy.enabled()) {
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;
            String secretKey = policy != null ? policy.secretKey() : DEFAULT_TENANT_SECRET;
            McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                    receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                    McpSecuritySandboxReceipt.STATUS_BLOCKED_UNAUTHORIZED, latencyMicros,
                    "租户未注册或已被禁用: " + request.tenantId(), 0L, timestampEpochMs, secretKey
            );
            return ToolExecutionResponse.blocked(receipt);
        }

        if (!policy.isToolAllowed(request.toolName())) {
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;
            McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                    receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                    McpSecuritySandboxReceipt.STATUS_BLOCKED_UNAUTHORIZED, latencyMicros,
                    "租户无权调用此工具: " + request.toolName(), getAvailableTokens(request.tenantId()),
                    timestampEpochMs, policy.secretKey()
            );
            return ToolExecutionResponse.blocked(receipt);
        }

        // 2. 纳秒级 CAS 令牌桶细粒度流控 (Step 2: Lock-Free Token Bucket Rate Limiting)
        LockFreeTokenBucketLimiter limiter = tenantLimiters.computeIfAbsent(
                request.tenantId(),
                tid -> new LockFreeTokenBucketLimiter(policy.rateLimitCapacity(), policy.refillRatePerSecond())
        );

        if (!limiter.tryAcquire(1)) {
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;
            McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                    receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                    McpSecuritySandboxReceipt.STATUS_BLOCKED_RATE_LIMIT, latencyMicros,
                    "租户触发细粒度流控超限拒绝 (Quota Exceeded)", 0L, timestampEpochMs, policy.secretKey()
            );
            return ToolExecutionResponse.blocked(receipt);
        }

        // 3. 递归同态模式与 AST 防注入审查 (Step 3: Deep Schema & AST Injection Inspection)
        DeepSchemaSecurityInspector.InspectionResult inspection = schemaInspector.inspectArguments(
                request.arguments(), request.requiredParams()
        );
        if (!inspection.passed()) {
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;
            McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                    receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                    McpSecuritySandboxReceipt.STATUS_BLOCKED_INSPECTION, latencyMicros,
                    inspection.blockedReason(), limiter.getAvailableTokens(), timestampEpochMs, policy.secretKey()
            );
            return ToolExecutionResponse.blocked(receipt);
        }

        // 4. 沙箱执行模式分支判定 (Step 4: Sandbox Execution)
        if (request.commandToExecute() == null || request.commandToExecute().isEmpty()) {
            // 仿真测试或无外部子进程调用，安全通过
            long latencyMicros = (System.nanoTime() - startNano) / 1000L;
            McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                    receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                    McpSecuritySandboxReceipt.STATUS_PASSED, latencyMicros,
                    McpSecuritySandboxReceipt.NONE_BLOCKED_REASON, limiter.getAvailableTokens(),
                    timestampEpochMs, policy.secretKey()
            );
            return ToolExecutionResponse.dryRunPassed(receipt);
        }

        // 调用瞬态沙箱隔离运行时
        EphemeralToolSandboxRuntime.SandboxExecutionResult sandboxResult = sandboxRuntime.executeSandboxed(
                request.commandToExecute(), request.extraSafeEnv(), request.stdinContent(), request.timeoutMs()
        );

        long totalLatencyMicros = (System.nanoTime() - startNano) / 1000L;

        // 5. 归档执行状态与签发存证凭单 (Step 5: Status Mapping & Receipt Issuance)
        String executionStatus;
        String blockedReason;
        if (sandboxResult.timedOut()) {
            executionStatus = McpSecuritySandboxReceipt.STATUS_FAILED_TIMEOUT;
            blockedReason = "沙箱进程执行超时被看门狗强杀 (Timeout Watchdog)";
        } else if (sandboxResult.isSuccess()) {
            executionStatus = McpSecuritySandboxReceipt.STATUS_PASSED;
            blockedReason = McpSecuritySandboxReceipt.NONE_BLOCKED_REASON;
        } else {
            executionStatus = McpSecuritySandboxReceipt.STATUS_FAILED_EXECUTION;
            blockedReason = "沙箱进程非零退出: " + sandboxResult.exitCode();
        }

        McpSecuritySandboxReceipt receipt = McpSecuritySandboxReceipt.create(
                receiptId, sandboxId, request.tenantId(), request.toolName(), request.callerAgent(),
                executionStatus, totalLatencyMicros, blockedReason, limiter.getAvailableTokens(),
                timestampEpochMs, policy.secretKey()
        );

        return ToolExecutionResponse.fromSandbox(receipt, sandboxResult);
    }

    /**
     * 校验凭单签名真伪 (防伪验真)
     *
     * @param receipt 待验真凭单
     * @return true-合法且未被篡改, false-非法或已篡改
     */
    public boolean verifyReceipt(McpSecuritySandboxReceipt receipt) {
        if (receipt == null) {
            return false;
        }
        TenantSecurityPolicy policy = tenantPolicies.get(receipt.tenantId());
        String secretKey = policy != null ? policy.secretKey() : DEFAULT_TENANT_SECRET;
        return receipt.verifySignature(secretKey);
    }

    public DeepSchemaSecurityInspector getSchemaInspector() {
        return schemaInspector;
    }

    public EphemeralToolSandboxRuntime getSandboxRuntime() {
        return sandboxRuntime;
    }

    @Override
    public void close() {
        if (sandboxRuntime != null) {
            sandboxRuntime.close();
        }
    }
}
