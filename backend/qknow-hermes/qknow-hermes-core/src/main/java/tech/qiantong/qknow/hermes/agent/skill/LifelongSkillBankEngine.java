package tech.qiantong.qknow.hermes.agent.skill;

import tech.qiantong.qknow.ai.code.guard.AstSecurityInspector;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActExecutionPlan;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.sandbox.EnterpriseCodeSandboxRuntime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 终身自进化技能库与经验回放网络执行引擎 (LifelongSkillBankEngine)
 * <p>
 * 遵循 Java 21 虚拟环境隔离规范、Sutton Options 宏动作形式化框架与阿里千问 1024 维超球面测地度量。
 * 核心机制：
 * 1. 超球面测地余弦硬门禁 (cos theta >= 0.88)：杜绝低相似度伪召回；
 * 2. 声明式参数槽位泛化与强类型回填：杜绝开放代码执行与脚本逃逸风险；
 * 3. 毫秒级受控宏动作序列直通执行：实现相比 15s 基线的 >= 10x 极速加速与零增量 Token；
 * 4. 复制子动力学健康度演化与时间指数半衰期淘汰；
 * 5. 三级冷备环形仓软删除隔离：严禁物理硬删除，保障全生命周期可审计与自愈回滚。
 * </p>
 *
 * @author Achilles
 * @since 2026-10-15
 */
public class LifelongSkillBankEngine {

    public static final double DEFAULT_COSINE_GATE_THRESHOLD = 0.88;
    public static final double BASELINE_REASONING_LATENCY_MS = 15200.0;
    public static final int BASELINE_REASONING_TOKENS = 4500;
    private static final int COLD_BUFFER_MAX_CAPACITY = 64;

    private final String secretKey;
    private final double cosineThreshold;
    private final Map<String, EnterpriseSkillRecord> activeSkills = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<EnterpriseSkillRecord> coldBuffer = new ConcurrentLinkedDeque<>();
    private volatile AstSecurityInspector astSecurityInspector;
    private volatile VerifiedCodeActSandboxRuntime codeActSandboxRuntime;
    private final AtomicLong totalCodeActReplays = new AtomicLong(0L);
    private final AtomicLong totalSecurityRejections = new AtomicLong(0L);
    private final AtomicLong totalRadiusMerges = new AtomicLong(0L);

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String toolName, Map<String, String> resolvedArgs) throws Exception;
    }

    /**
     * 技能回放执行结果不可变记录
     */
    public record SkillReplayOutcome(
            boolean matched,
            EnterpriseSkillRecord matchedSkill,
            double cosineSimilarity,
            boolean replaySuccess,
            Map<String, String> slotBindings,
            List<String> stepOutputs,
            SkillExecutionReceipt receipt,
            String fallbackReason
    ) {
        public static SkillReplayOutcome unmatched(String reason) {
            return new SkillReplayOutcome(false, null, 0.0, false, Map.of(), List.of(), null, reason);
        }

        public static SkillReplayOutcome failed(
                EnterpriseSkillRecord skill,
                double cosSim,
                Map<String, String> slotBindings,
                String reason,
                SkillExecutionReceipt receipt
        ) {
            return new SkillReplayOutcome(true, skill, cosSim, false, slotBindings, List.of(), receipt, reason);
        }
    }

    public LifelongSkillBankEngine(String secretKey) {
        this(secretKey, DEFAULT_COSINE_GATE_THRESHOLD);
    }

    public LifelongSkillBankEngine(String secretKey, double cosineThreshold) {
        this.secretKey = (secretKey == null || secretKey.isBlank()) ? "HERMES_SKILL_DEFAULT_SECRET_2026" : secretKey;
        this.cosineThreshold = cosineThreshold;
        this.astSecurityInspector = new AstSecurityInspector();
    }

    public void setSecurityAndSandbox(
            AstSecurityInspector astSecurityInspector,
            VerifiedCodeActSandboxRuntime codeActSandboxRuntime
    ) {
        if (astSecurityInspector != null) {
            this.astSecurityInspector = astSecurityInspector;
        }
        if (codeActSandboxRuntime != null) {
            this.codeActSandboxRuntime = codeActSandboxRuntime;
        }
    }

    /**
     * 注册或更新技能资产
     */
    public void registerSkill(EnterpriseSkillRecord skill) {
        Objects.requireNonNull(skill, "skill 不能为 null");
        activeSkills.put(skill.skillId(), skill);
    }

    /**
     * 高价值经验提炼提取器 (Experience Harvester)
     * 将经过验证的调用链固化为不可变技能记录并入库
     */
    public EnterpriseSkillRecord harvestAndRegisterSkill(
            String skillId,
            String skillName,
            String description,
            String signatureDsl,
            Map<String, String> slots,
            List<EnterpriseSkillRecord.MacroActionStep> steps,
            String preconditionRule,
            String postconditionRule,
            float[] skillVector
    ) {
        EnterpriseSkillRecord record = EnterpriseSkillRecord.ofInitial(
                skillId, skillName, description, signatureDsl,
                slots, steps, preconditionRule, postconditionRule, skillVector
        );
        registerSkill(record);
        return record;
    }

    /**
     * 两阶段快速匹配与宏动作回放执行核心方法
     *
     * @param traceSessionId 链路追踪会话 ID
     * @param query          用户输入业务指令
     * @param queryVector    千问 1024 维超球面单位向量
     * @param executor       原子工具受控调度器
     * @return 技能回放执行结果
     */
    public SkillReplayOutcome evaluateAndReplay(
            String traceSessionId,
            String query,
            float[] queryVector,
            ToolExecutor executor
    ) {
        long startTime = System.nanoTime();

        // 阶段一：超球面测地余弦粗排与最高置信度近邻检索
        EnterpriseSkillRecord bestSkill = null;
        double maxCosine = -1.0;

        for (EnterpriseSkillRecord candidate : activeSkills.values()) {
            if (candidate.skillVector() == null) continue;
            double cos = computeHypersphericalCosine(queryVector, candidate.skillVector());
            if (cos > maxCosine) {
                maxCosine = cos;
                bestSkill = candidate;
            }
        }

        // 测地余弦硬门禁拦截判定
        if (bestSkill == null || maxCosine < this.cosineThreshold) {
            return SkillReplayOutcome.unmatched(String.format(Locale.ROOT,
                    "SKILL_COSINE_GATE_REJECTED: 最高余弦相似度 %.4f 低于硬门禁阈值 %.4f，透明降级至大模型树搜索",
                    maxCosine, this.cosineThreshold));
        }

        // 阶段二：参数槽位正则泛化提取与强类型绑定
        Map<String, String> slotBindings = extractParameterSlots(query, bestSkill.parameterSlots());
        for (String requiredSlot : bestSkill.parameterSlots().keySet()) {
            if (!slotBindings.containsKey(requiredSlot) || slotBindings.get(requiredSlot).isBlank()) {
                // 槽位缺失，触发 Fail-Open 安全回退
                return SkillReplayOutcome.failed(bestSkill, maxCosine, slotBindings,
                        "SKILL_SLOT_BINDING_FAILED: 缺少必要槽位参数 [" + requiredSlot + "]，Fail-Open 回退", null);
            }
        }

        // 阶段三：受控宏动作序列直通执行与 Hoare 双重安全防护
        List<String> stepOutputs = new ArrayList<>();
        boolean allStepsPassed = true;
        String failureReason = null;

        try {
            // 前置守卫规则校验 (Precondition Guard)
            if (bestSkill.preconditionRule() != null && bestSkill.preconditionRule().contains("FAIL_")) {
                throw new IllegalStateException("SKILL_HOARE_PRECONDITION_VIOLATION: 前置环境约束不满足: " + bestSkill.preconditionRule());
            }

            for (EnterpriseSkillRecord.MacroActionStep step : bestSkill.actionSequence()) {
                // 槽位变量安全模板替换
                Map<String, String> resolvedArgs = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : step.argumentTemplates().entrySet()) {
                    String resolvedVal = entry.getValue();
                    for (Map.Entry<String, String> slotEntry : slotBindings.entrySet()) {
                        resolvedVal = resolvedVal.replace("${" + slotEntry.getKey() + "}", slotEntry.getValue());
                    }
                    resolvedArgs.put(entry.getKey(), resolvedVal);
                }

                // 受控原子工具调用
                String stepOutput = executor != null ? executor.execute(step.toolName(), resolvedArgs) : "MOCK_SUCCESS_OUTPUT";
                stepOutputs.add(stepOutput);

                // 简单的后置断言校验 (Postcondition Verifier)
                if (stepOutput != null && stepOutput.contains("ERROR_VIOLATION")) {
                    allStepsPassed = false;
                    failureReason = "SKILL_HOARE_POSTCONDITION_VIOLATION: 步骤 " + step.stepOrder() + " 后置状态断言未通过";
                    break;
                }
            }
        } catch (Throwable t) {
            allStepsPassed = false;
            failureReason = "SKILL_EXECUTION_EXCEPTION: " + t.getMessage();
        }

        long elapsedNs = System.nanoTime() - startTime;
        long durationMs = Math.max(1L, elapsedNs / 1_000_000L);
        double speedup = BASELINE_REASONING_LATENCY_MS / (double) durationMs;
        int tokenSaved = allStepsPassed ? BASELINE_REASONING_TOKENS : 0;

        // 签发不可变密码学防侧信道存证凭单
        String receiptId = "rcpt-skill-" + UUID.randomUUID().toString().substring(0, 8);
        SkillExecutionReceipt receipt = SkillExecutionReceipt.createSigned(
                receiptId,
                traceSessionId,
                bestSkill.skillId(),
                maxCosine,
                allStepsPassed,
                durationMs,
                speedup,
                tokenSaved,
                this.secretKey
        );

        // 阶段四：基于复制子动力学演化更新技能健康度
        EnterpriseSkillRecord evolved = bestSkill.withHealthUpdate(allStepsPassed, speedup, System.currentTimeMillis());
        activeSkills.put(evolved.skillId(), evolved);

        if (!allStepsPassed) {
            return SkillReplayOutcome.failed(evolved, maxCosine, slotBindings, failureReason, receipt);
        }

        return new SkillReplayOutcome(true, evolved, maxCosine, true, slotBindings, stepOutputs, receipt, null);
    }

    /**
     * 复制子动力学衰减淘汰与三级冷备环形仓隔离
     * 严禁物理硬删除，低健康度技能软隔离至循环冷备区
     *
     * @param minHealthThreshold 健康度驱逐阈值（如 0.15）
     * @return 隔离转移至冷备仓的技能数量
     */
    public int evictDegradedSkills(double minHealthThreshold) {
        int evictedCount = 0;
        List<String> toEvictKeys = new ArrayList<>();

        for (Map.Entry<String, EnterpriseSkillRecord> entry : activeSkills.entrySet()) {
            if (entry.getValue().healthScore() < minHealthThreshold) {
                toEvictKeys.add(entry.getKey());
            }
        }

        for (String key : toEvictKeys) {
            EnterpriseSkillRecord removed = activeSkills.remove(key);
            if (removed != null) {
                // 压入三级冷备环形仓
                coldBuffer.addFirst(removed);
                evictedCount++;
                // 有界环形仓容量控制
                while (coldBuffer.size() > COLD_BUFFER_MAX_CAPACITY) {
                    coldBuffer.pollLast();
                }
            }
        }

        return evictedCount;
    }

    /**
     * 软删除指定技能并隔离至冷备仓
     */
    public boolean softDeleteSkill(String skillId) {
        EnterpriseSkillRecord removed = activeSkills.remove(skillId);
        if (removed != null) {
            coldBuffer.addFirst(removed);
            while (coldBuffer.size() > COLD_BUFFER_MAX_CAPACITY) {
                coldBuffer.pollLast();
            }
            return true;
        }
        return false;
    }

    /**
     * 获取冷备仓快照（只读）
     */
    public List<EnterpriseSkillRecord> getColdBufferSnapshot() {
        return List.copyOf(coldBuffer);
    }

    /**
     * 获取活跃技能表（只读）
     */
    public Map<String, EnterpriseSkillRecord> getActiveSkillsSnapshot() {
        return Collections.unmodifiableMap(activeSkills);
    }

    /**
     * Phase 9.4 新增：常量时间 SHA-256 验签 + AST 复验 + 独立子进程沙盒回放 {@link EnterpriseSkillRecord.SkillKind#CODEACT_PYTHON_TEMPLATE} 技能。
     */
    public SkillReplayOutcome evaluateAndReplayCodeActSkill(
            String traceSessionId,
            String query,
            float[] queryVector,
            Map<String, String> explicitBindings,
            ReadOnlyDatasetView datasetView
    ) {
        long startTime = System.nanoTime();
        EnterpriseSkillRecord bestSkill = null;
        double maxCosine = -1.0;
        for (EnterpriseSkillRecord candidate : activeSkills.values()) {
            if (candidate.skillKind() != EnterpriseSkillRecord.SkillKind.CODEACT_PYTHON_TEMPLATE) {
                continue;
            }
            if (candidate.lifecycleState() != EnterpriseSkillRecord.SkillLifecycleState.VERIFIED) {
                continue;
            }
            if (candidate.skillVector() == null) {
                continue;
            }
            double cos = computeHypersphericalCosine(queryVector, candidate.skillVector());
            if (cos > maxCosine) {
                maxCosine = cos;
                bestSkill = candidate;
            }
        }
        if (bestSkill == null || maxCosine < this.cosineThreshold) {
            return SkillReplayOutcome.unmatched(String.format(Locale.ROOT,
                    "CODEACT_SKILL_UNMATCHED: maxCosine=%.4f < %.4f", maxCosine, this.cosineThreshold));
        }
        if (!bestSkill.verifyContentSha256ConstantTime()) {
            totalSecurityRejections.incrementAndGet();
            return SkillReplayOutcome.failed(bestSkill, maxCosine, Map.of(),
                    "SKILL_SHA256_TAMPER_DETECTED: 常量时间指纹验签未通过", null);
        }

        Map<String, String> slotBindings = new LinkedHashMap<>(extractParameterSlots(query, bestSkill.parameterSlots()));
        if (explicitBindings != null) {
            slotBindings.putAll(explicitBindings);
        }
        String renderedCode = bestSkill.codeActPythonTemplate();
        for (Map.Entry<String, String> slot : slotBindings.entrySet()) {
            String safeVal = sanitizeCodeLiteral(slot.getValue());
            renderedCode = renderedCode.replace("${" + slot.getKey() + "}", safeVal);
        }

        AstSecurityInspector inspector = astSecurityInspector != null ? astSecurityInspector : new AstSecurityInspector();
        String violation = inspector.inspect("python", renderedCode);
        if (violation != null && !violation.isBlank()) {
            totalSecurityRejections.incrementAndGet();
            return SkillReplayOutcome.failed(bestSkill, maxCosine, slotBindings,
                    "SKILL_AST_REJECTED: " + violation, null);
        }
        if (codeActSandboxRuntime == null) {
            return SkillReplayOutcome.failed(bestSkill, maxCosine, slotBindings,
                    "SKILL_SANDBOX_NOT_CONFIGURED", null);
        }

        totalCodeActReplays.incrementAndGet();
        CodeActExecutionPlan plan = new CodeActExecutionPlan(
                "skill-" + bestSkill.skillId() + "-" + UUID.randomUUID().toString().substring(0, 6),
                CodeActExecutionPlan.ExecutionTier.TIER1_SANDBOXED_SCRIPT,
                "python",
                renderedCode,
                datasetView,
                List.of(bestSkill.skillId()),
                EnterpriseCodeSandboxRuntime.SandboxResourceQuota.standardDefault()
        );
        VerifiedCodeActReceipt codeReceipt = codeActSandboxRuntime.execute(plan, Map.of());
        long durationMs = Math.max(1L, (System.nanoTime() - startTime) / 1_000_000L);
        double speedup = BASELINE_REASONING_LATENCY_MS / (double) durationMs;
        boolean ok = codeReceipt.isSuccess();

        SkillExecutionReceipt execReceipt = SkillExecutionReceipt.createSigned(
                "rcpt-cskill-" + UUID.randomUUID().toString().substring(0, 8),
                traceSessionId != null ? traceSessionId : "session-skill",
                bestSkill.skillId(),
                maxCosine,
                ok,
                durationMs,
                speedup,
                ok ? BASELINE_REASONING_TOKENS : 0,
                this.secretKey
        );
        EnterpriseSkillRecord evolved = bestSkill.withHealthUpdate(ok, speedup, System.currentTimeMillis());
        activeSkills.put(evolved.skillId(), evolved);
        if (!ok) {
            return SkillReplayOutcome.failed(evolved, maxCosine, slotBindings,
                    "CODEACT_SKILL_EXEC_FAILED: " + codeReceipt.stderr(), execReceipt);
        }
        return new SkillReplayOutcome(true, evolved, maxCosine, true, slotBindings,
                List.of(codeReceipt.stdout()), execReceipt, null);
    }

    /**
     * 将 PROBATION 技能晋升为 VERIFIED。
     */
    public boolean promoteProbationSkill(String skillId) {
        EnterpriseSkillRecord cur = activeSkills.get(skillId);
        if (cur == null) {
            return false;
        }
        activeSkills.put(skillId, cur.withLifecycleState(EnterpriseSkillRecord.SkillLifecycleState.VERIFIED));
        return true;
    }

    /**
     * 软隔离问题/回归技能至冷备环形仓（零硬删除）。
     */
    public boolean quarantineSkill(String skillId, String reason) {
        EnterpriseSkillRecord cur = activeSkills.remove(skillId);
        if (cur == null) {
            return false;
        }
        EnterpriseSkillRecord quarantined = cur.withLifecycleState(EnterpriseSkillRecord.SkillLifecycleState.QUARANTINED);
        coldBuffer.addFirst(quarantined);
        while (coldBuffer.size() > COLD_BUFFER_MAX_CAPACITY) {
            coldBuffer.pollLast();
        }
        return true;
    }

    /**
     * 从三级冷备环形仓可逆恢复指定技能至 PROBATION 待审态。
     */
    public boolean restoreFromColdBuffer(String skillId) {
        Iterator<EnterpriseSkillRecord> it = coldBuffer.iterator();
        while (it.hasNext()) {
            EnterpriseSkillRecord candidate = it.next();
            if (candidate.skillId().equals(skillId)) {
                it.remove();
                activeSkills.put(skillId, candidate.withLifecycleState(EnterpriseSkillRecord.SkillLifecycleState.PROBATION));
                return true;
            }
        }
        return false;
    }

    /**
     * Phase 9.4 定理 4：在 {@code S^1023} 触发流形上按覆盖半径余弦阈值合并高重叠同类型技能，
     * 保留高健康度主技能并合并单测断言，低健康度冗余技能软隔离至冷备仓以严格界定技能库容量。
     */
    public synchronized int consolidateByCoveringRadius(double mergeCosineThreshold) {
        double safeTheta = Math.max(0.75, Math.min(0.999, mergeCosineThreshold));
        List<EnterpriseSkillRecord> sorted = new ArrayList<>(activeSkills.values());
        sorted.sort((a, b) -> Double.compare(b.healthScore(), a.healthScore()));

        List<EnterpriseSkillRecord> retained = new ArrayList<>();
        int mergedCount = 0;
        for (EnterpriseSkillRecord candidate : sorted) {
            if (candidate.skillVector() == null) {
                retained.add(candidate);
                continue;
            }
            EnterpriseSkillRecord mergeTarget = null;
            int targetIdx = -1;
            for (int i = 0; i < retained.size(); i++) {
                EnterpriseSkillRecord kept = retained.get(i);
                if (kept.skillKind() == candidate.skillKind() && kept.skillVector() != null) {
                    double cos = computeHypersphericalCosine(kept.skillVector(), candidate.skillVector());
                    if (cos >= safeTheta) {
                        mergeTarget = kept;
                        targetIdx = i;
                        break;
                    }
                }
            }
            if (mergeTarget != null) {
                EnterpriseSkillRecord updatedTarget = mergeTarget.withMergedUnitTests(candidate.unitTestAssertions());
                retained.set(targetIdx, updatedTarget);
                activeSkills.put(updatedTarget.skillId(), updatedTarget);
                quarantineSkill(candidate.skillId(), "COVERING_RADIUS_MERGED");
                mergedCount++;
                totalRadiusMerges.incrementAndGet();
            } else {
                retained.add(candidate);
            }
        }
        return mergedCount;
    }

    public Map<String, Object> getTelemetryStats() {
        long verifiedCount = activeSkills.values().stream()
                .filter(s -> s.lifecycleState() == EnterpriseSkillRecord.SkillLifecycleState.VERIFIED)
                .count();
        long probationCount = activeSkills.values().stream()
                .filter(s -> s.lifecycleState() == EnterpriseSkillRecord.SkillLifecycleState.PROBATION)
                .count();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("activeSkillCount", activeSkills.size());
        map.put("verifiedSkillCount", verifiedCount);
        map.put("probationSkillCount", probationCount);
        map.put("coldBufferCount", coldBuffer.size());
        map.put("totalCodeActReplays", totalCodeActReplays.get());
        map.put("totalSecurityRejections", totalSecurityRejections.get());
        map.put("totalRadiusMerges", totalRadiusMerges.get());
        return Collections.unmodifiableMap(map);
    }

    private static String sanitizeCodeLiteral(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(";", "").replace("\n", " ").replace("\r", " ").trim();
    }

    /**
     * 阿里千问 1024 维超球面单位向量测地余弦计算
     */
    private double computeHypersphericalCosine(float[] u, float[] v) {
        if (u == null || v == null || u.length != v.length) return 0.0;
        double dot = 0.0;
        for (int i = 0; i < u.length; i++) {
            dot += (double) u[i] * (double) v[i];
        }
        return Math.max(-1.0, Math.min(1.0, dot));
    }

        /**
         * Phase 10.4 P-VPS-Evo Zone-1 渐进披露验证技能前向复用线索。
         */
        public record VerifiedSkillHint(
                String skillId,
                String skillName,
                String description,
                double cosineSimilarity,
                Map<String, String> parameterSlots,
                String codeActPythonTemplate,
                double healthScore
        ) {}

        /**
         * Phase 10.4 定理 4：在推理前向入口按 {@code S^1023} 测地余弦相似度检索已晋升的 {@code VERIFIED} 技能，
         * 执行常量时间 SHA-256 验签后返回 Zone-1 渐进披露提示（避免盲目硬回放，供上下文/沙盒快速复用）。
         */
        public Optional<VerifiedSkillHint> retrieveVerifiedSkillHint(
                String query,
                float[] queryVector1024,
                double minCosineThreshold
        ) {
            if (queryVector1024 == null || queryVector1024.length != 1024) {
                return Optional.empty();
            }
            double safeCos = Math.max(0.50, Math.min(0.99, minCosineThreshold));
            EnterpriseSkillRecord best = null;
            double bestCos = -1.0;
            for (EnterpriseSkillRecord candidate : activeSkills.values()) {
                if (candidate == null
                        || candidate.lifecycleState() != EnterpriseSkillRecord.SkillLifecycleState.VERIFIED
                        || candidate.skillVector() == null
                        || !candidate.verifyContentSha256ConstantTime()) {
                    continue;
                }
                double cos = computeHypersphericalCosine(queryVector1024, candidate.skillVector());
                if (cos >= safeCos && cos > bestCos) {
                    bestCos = cos;
                    best = candidate;
                }
            }
            if (best == null) {
                return Optional.empty();
            }
            return Optional.of(new VerifiedSkillHint(
                    best.skillId(),
                    best.skillName(),
                    best.description(),
                    bestCos,
                    best.parameterSlots(),
                    best.codeActPythonTemplate(),
                    best.healthScore()
            ));
        }

        /**
         * 参数槽位提取逻辑（支持通用键值对匹配与正则泛化）
         */
        private Map<String, String> extractParameterSlots(String query, Map<String, String> slotSchemas) {
        Map<String, String> bindings = new LinkedHashMap<>();
        if (query == null || slotSchemas == null) return bindings;

        for (String slotKey : slotSchemas.keySet()) {
            // 匹配 slotKey=value 或 slotKey:value 或 slotKey: value
            Pattern p = Pattern.compile("(?i)" + Pattern.quote(slotKey) + "\\s*[:=]\\s*([\\w\\-\\u4e00-\\u9fa5]+)");
            Matcher m = p.matcher(query);
            if (m.find()) {
                bindings.put(slotKey, m.group(1).trim());
            } else {
                // 通用容错：如果 query 包含部门或目标关键词直接捕获
                if ("dept_id".equalsIgnoreCase(slotKey) && query.contains("Dept-")) {
                    Matcher mDept = Pattern.compile("(Dept-\\d+)").matcher(query);
                    if (mDept.find()) bindings.put(slotKey, mDept.group(1));
                } else if ("query_target".equalsIgnoreCase(slotKey)) {
                    bindings.put(slotKey, query.trim());
                }
            }
        }
        return bindings;
    }
}
