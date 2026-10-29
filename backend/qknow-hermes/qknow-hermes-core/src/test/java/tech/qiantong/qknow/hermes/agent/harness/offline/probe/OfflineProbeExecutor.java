package tech.qiantong.qknow.hermes.agent.harness.offline.probe;

import tech.qiantong.qknow.hermes.agent.harness.HarnessEvidenceRecord;
import tech.qiantong.qknow.hermes.agent.harness.HermesHarnessVoucher;
import tech.qiantong.qknow.hermes.agent.harness.offline.OfflineBenchmarkTestCase;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Hermes 阶段四离线探针执行器契约。
 *
 * <p>吸收 Google Research Harness² 的 Probe Rollout 设计思想，
 * 负责通过局部请求凭单隔离调度执行基线或候选探针，捕获物理执行证据并落盘。</p>
 */
public interface OfflineProbeExecutor {

    /**
     * 对单个离线基准用例执行一次真实探针运行。
     *
     * @param runId 实验运行唯一标识
     * @param variantName 变异标识（如 "baseline" 或 "candidate"）
     * @param testCase 离线基准测试用例
     * @param voucher 执行凭单
     * @param options 运行配置
     * @return 探针执行综合报告
     */
    ProbeExecutionReport executeSingleProbe(
            String runId,
            String variantName,
            OfflineBenchmarkTestCase testCase,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options
    );

    /**
     * 对全量基准任务套件执行批量探针运行。
     *
     * @param runId 实验运行唯一标识
     * @param variantName 变异标识
     * @param testSuite 离线基准测试套件
     * @param voucher 执行凭单
     * @param options 运行配置
     * @return 探针执行综合报告列表
     */
    List<ProbeExecutionReport> executeBatchProbe(
            String runId,
            String variantName,
            List<OfflineBenchmarkTestCase> testSuite,
            HermesHarnessVoucher voucher,
            ProbeExecutionOptions options
    );

    /**
     * 探针运行配置（不可变 Java 21 Record）。
     */
    record ProbeExecutionOptions(
            long timeoutMs,               // 单任务超时限制（毫秒）
            int maxRetryAttempts,         // 失败重试次数（默认 1）
            Path outputBaseDir,           // 物理文件落盘根路径
            boolean persistEvidenceJson,  // 是否强制物理落盘为 JSON
            String modelPlatform,         // 模型平台（固定 deepseek）
            String modelName              // 模型名称（如 deepseek-flash）
    ) {
        public ProbeExecutionOptions {
            Objects.requireNonNull(outputBaseDir, "outputBaseDir 不能为空");
            Objects.requireNonNull(modelPlatform, "modelPlatform 不能为空");
            Objects.requireNonNull(modelName, "modelName 不能为空");
            if (timeoutMs <= 0) {
                timeoutMs = 30_000L;
            }
        }

        public static ProbeExecutionOptions defaults() {
            return new ProbeExecutionOptions(
                    30_000L,
                    1,
                    Path.of("experiment/outputs/harness4"),
                    true,
                    "deepseek",
                    "deepseek-flash"
            );
        }
    }

    /**
     * 单次探针执行综合报告（不可变 Java 21 Record）。
     */
    record ProbeExecutionReport(
            String runId,
            String variantName,
            String taskId,
            String probeRequestId,
            String voucherVersion,
            String voucherFingerprint,
            boolean success,
            String errorCode,
            long executionDurationMs,
            HarnessEvidenceRecord evidenceRecord,
            Path persistedJsonPath,
            String rawOutputSummary
    ) {
        public ProbeExecutionReport {
            Objects.requireNonNull(runId, "runId 不能为空");
            Objects.requireNonNull(variantName, "variantName 不能为空");
            Objects.requireNonNull(taskId, "taskId 不能为空");
            Objects.requireNonNull(probeRequestId, "probeRequestId 不能为空");
            Objects.requireNonNull(voucherVersion, "voucherVersion 不能为空");
            Objects.requireNonNull(voucherFingerprint, "voucherFingerprint 不能为空");
            Objects.requireNonNull(errorCode, "errorCode 不能为空");
        }
    }
}
