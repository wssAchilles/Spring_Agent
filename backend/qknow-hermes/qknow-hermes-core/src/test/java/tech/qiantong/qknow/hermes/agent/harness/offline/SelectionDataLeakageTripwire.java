package tech.qiantong.qknow.hermes.agent.harness.offline;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 盲审防数据泄漏雷区扫描门禁 (Data Leakage Tripwire)。
 *
 * <p>吸收 Harness² context.py 的 assert_no_grader_verdicts 与 assert_no_leak_paths 机制，
 * 在向盲审评测器或变异器递交上下文前，强制执行双重雷区扫描，
 * 绝不允许评分结果、黄金标准答案或判定关键词进入大模型输入，从源头杜绝过拟合作弊。</p>
 */
public final class SelectionDataLeakageTripwire {

    private SelectionDataLeakageTripwire() {}

    /**
     * 严禁进入模型的评委判词与结果标记清单（大小写不敏感）。
     */
    public static final Set<String> GRADER_VERDICT_MARKERS = Set.of(
            "deterministic_eval_summary",
            "deterministic_eval_result",
            "judge_scope_and_deterministic_status",
            "passed_count",
            "pass_rate",
            "test_pass_rate",
            "gold_rubric",
            "ground_truth_answer",
            "qrels.tsv"
    );

    /**
     * 严禁被上下文读取的敏感测试路径模式。
     */
    public static final List<String> SENSITIVE_PATH_PATTERNS = List.of(
            "/qrels",
            "/holdout/",
            "/expected/",
            "/rubrics/",
            "/ground_truth/"
    );

    /**
     * 严格扫描文本内容与文件路径，一旦检测到任何泄漏立即抛出异常熔断。
     */
    public static void assertNoLeak(String contextText, List<String> filePathsRead) {
        // 1. 扫描文件路径列表
        if (filePathsRead != null) {
            for (String path : filePathsRead) {
                if (path == null) continue;
                String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
                for (String pattern : SENSITIVE_PATH_PATTERNS) {
                    if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                        throw new IllegalStateException("CRITICAL_DATA_LEAKAGE: 评测上下文包含非法敏感路径: " + path);
                    }
                }
            }
        }

        // 2. 扫描上下文文本内容
        if (contextText != null && !contextText.isBlank()) {
            String lowerContext = contextText.toLowerCase(Locale.ROOT);
            for (String marker : GRADER_VERDICT_MARKERS) {
                if (lowerContext.contains(marker.toLowerCase(Locale.ROOT))) {
                    throw new IllegalStateException("CRITICAL_DATA_LEAKAGE: 评测上下文包含非法判词标记: " + marker);
                }
            }
        }
    }
}
