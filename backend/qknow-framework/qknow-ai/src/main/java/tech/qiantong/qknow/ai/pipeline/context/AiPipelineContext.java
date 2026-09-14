package tech.qiantong.qknow.ai.pipeline.context;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import tech.qiantong.qknow.ai.audit.causal.CausalTraceNode;
import tech.qiantong.qknow.ai.audit.merkle.MerkleProof;
import tech.qiantong.qknow.ai.guardrail.model.GuardrailDecision;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 贯穿全生命周期的 AI 编排管道上下文 (AiPipelineContext)
 *
 * 封装原始请求、安全审查状态、SLA 选路结果、共识仲裁事实、输出合规判定、Merkle 证据树与因果图。
 * 遵循严格线程安全与阶段状态格单调推进原则。
 *
 * @author qknow
 */
@Getter
@ToString
public class AiPipelineContext {

    // ================= 基础元数据 (不可变) =================
    private final String traceId;
    private final String tenantId;
    private final String userId;
    private final String profile;
    private final Instant createdAt;
    private final long globalDeadlineMs; // 全局绝对截止时间戳 (System.currentTimeMillis() + timeout)

    // ================= 防重入与防穿透控制 =================
    private final AtomicInteger reentrancyDepth = new AtomicInteger(0);

    // ================= 输入与脱敏状态 =================
    private volatile String rawPrompt;
    private volatile String sanitizedPrompt;
    private final Map<String, String> piiRedactionMap = new ConcurrentHashMap<>();
    private volatile GuardrailDecision inputGuardrailDecision;

    // ================= SLA 路由状态 =================
    private volatile String selectedChannelId;
    private volatile String selectedModelName;
    private volatile Double estimatedCost;

    // ================= 共识与业务执行 =================
    private volatile boolean consensusActivated = false;
    private volatile String consensusWinnerProposal;
    private final List<String> consensusVoters = Collections.synchronizedList(new ArrayList<>());
    private volatile Object executionResult;

    // ================= 输出合规状态 =================
    private volatile String rawOutput;
    private volatile String sanitizedOutput;
    private volatile GuardrailDecision outputGuardrailDecision;

    // ================= 密码学存证与因果图 =================
    private volatile String merkleRootHash;
    private volatile MerkleProof merkleInclusionProof;
    private final List<CausalTraceNode> causalNodes = Collections.synchronizedList(new ArrayList<>());

    // ================= 扩展属性池 (用于阶段间共享中间变量) =================
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    @Builder
    public AiPipelineContext(
            String traceId,
            String tenantId,
            String userId,
            String profile,
            String rawPrompt,
            long globalTimeoutMs
    ) {
        this.traceId = traceId != null ? traceId : UUID.randomUUID().toString().replace("-", "");
        this.tenantId = tenantId != null ? tenantId : "DEFAULT_TENANT";
        this.userId = userId != null ? userId : "ANONYMOUS";
        this.profile = profile != null ? profile : "DEFAULT";
        this.rawPrompt = rawPrompt != null ? rawPrompt : "";
        this.sanitizedPrompt = this.rawPrompt;
        this.createdAt = Instant.now();
        this.globalDeadlineMs = System.currentTimeMillis() + (globalTimeoutMs > 0 ? globalTimeoutMs : 30000L);
    }

    /**
     * 检查当前请求是否已超出全局截止时间
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= globalDeadlineMs;
    }

    /**
     * 计算当前请求剩余可用时间 (毫秒)
     */
    public long getRemainingTimeMs() {
        long remain = globalDeadlineMs - System.currentTimeMillis();
        return Math.max(remain, 0L);
    }

    public void updateSanitizedPrompt(String sanitizedPrompt, Map<String, String> redactions) {
        this.sanitizedPrompt = sanitizedPrompt;
        if (redactions != null) {
            this.piiRedactionMap.putAll(redactions);
        }
    }

    public void setInputGuardrailDecision(GuardrailDecision decision) {
        this.inputGuardrailDecision = decision;
    }

    public void setRoutingResult(String channelId, String modelName, Double cost) {
        this.selectedChannelId = channelId;
        this.selectedModelName = modelName;
        this.estimatedCost = cost;
    }

    public void setConsensusResult(boolean activated, String winner, List<String> voters) {
        this.consensusActivated = activated;
        this.consensusWinnerProposal = winner;
        if (voters != null) {
            this.consensusVoters.addAll(voters);
        }
    }

    public void setExecutionResult(Object result) {
        this.executionResult = result;
        if (result != null) {
            this.rawOutput = result.toString();
            this.sanitizedOutput = this.rawOutput;
        }
    }

    public void setOutputGuardrailResult(GuardrailDecision decision, String finalSanitizedOutput) {
        this.outputGuardrailDecision = decision;
        this.sanitizedOutput = finalSanitizedOutput;
    }

    public void setMerkleResult(String rootHash, MerkleProof proof) {
        this.merkleRootHash = rootHash;
        this.merkleInclusionProof = proof;
    }

    public void addCausalNode(CausalTraceNode node) {
        if (node != null) {
            this.causalNodes.add(node);
        }
    }

    public void setAttribute(String key, Object value) {
        if (value != null) {
            this.attributes.put(key, value);
        } else {
            this.attributes.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) this.attributes.get(key);
    }
}
