package tech.qiantong.qknow.hermes.cognitive.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 前缀守恒增量观测去重引擎（Phase 11.2 IDG-CO，引理 2.1 & 定理 2）。
 * <p>
 * 严格遵循 DeepSeek / 阿里云百炼 KV Prefix Cache 单价折扣定价定理（{@code c = 0.1} 下回溯修改历史消息必破缓存）：
 * 1. 绝不回溯改写任何已入栈历史 turn（{@code prefixMutationOccurred} 恒为 {@code false}）；
 * 2. 仅在工具输出首次进入上下文前的写入边界，按 SHA-256 指纹抑制跨轮重复的静态元信息区块与已交付正文片段；
 * 3. 严格遵守报错观测免折叠律（Error Exemption），所有错误/异常提示零删减原样透传防止无效重试螺旋。
 * </p>
 */
public class IncrementalDeltaObservationDeduplicator {

    private static final int MIN_ERROR_EXEMPT_BYPASS = 64;
    private static final int MIN_DEDUP_BLOCK_CHARS = 120;
    private static final double DEFAULT_SPAN_OVERLAP_THRESHOLD = 0.85;

    /**
     * 观测去重过滤输出与不可变凭单封装。
     */
    public record FilteredObservationResult(
            String emittedOutput,
            ObservationDeltaReceipt receipt
    ) {}

    private record SpanRecord(int startOffset, int endOffset, String contentHash) {}

    private static final class RequestDedupState {
        final Map<String, String> metaSectionHashByKey = new HashMap<>();
        final Map<String, Integer> blockFirstSeenStepByHash = new HashMap<>();
        final Map<String, List<SpanRecord>> deliveredSpansByTitle = new HashMap<>();
        final List<ObservationDeltaReceipt> receipts = new ArrayList<>();
    }

    private final Map<String, RequestDedupState> stateByRequest = new ConcurrentHashMap<>();
    private final AtomicLong totalObservationsFiltered = new AtomicLong(0L);
    private final AtomicLong totalDedupBlocksSuppressed = new AtomicLong(0L);
    private final AtomicLong totalMetaSectionsSuppressed = new AtomicLong(0L);
    private final AtomicLong totalCharsSaved = new AtomicLong(0L);

    /**
     * 在工具观察入栈前执行写入即增量去重（Append-Only，零历史前缀篡改）。
     */
    public FilteredObservationResult filterToolObservation(
            String requestId,
            int stepIndex,
            String toolName,
            String rawOutput
    ) {
        String safeReq = normalizeReqId(requestId);
        String safeTool = toolName != null ? toolName : "";
        String safeRaw = rawOutput != null ? rawOutput : "";
        int rawChars = safeRaw.length();

        RequestDedupState state = stateByRequest.computeIfAbsent(safeReq, k -> new RequestDedupState());

        // 报错观测豁免律：短消息或报错观测零删减原样透传，杜绝丢失错误信号引起死循环重试
        if (rawChars < MIN_ERROR_EXEMPT_BYPASS || isErrorObservation(safeRaw)) {
            ObservationDeltaReceipt receipt = new ObservationDeltaReceipt(
                    safeReq, stepIndex, safeTool, rawChars, rawChars, 0,
                    0, 0, false, System.currentTimeMillis()
            );
            synchronized (state) {
                state.receipts.add(receipt);
            }
            totalObservationsFiltered.incrementAndGet();
            return new FilteredObservationResult(safeRaw, receipt);
        }

        int dedupBlocks = 0;
        int suppressedMeta = 0;
        String emitted;

        synchronized (state) {
            // 拆分段落级区块（支持 \n\n 与常见分隔符 ---），兼顾结构化 XML 元块保持完整识别
            List<String> blocks = splitLogicalBlocks(safeRaw);
            StringBuilder out = new StringBuilder(Math.min(rawChars, 2048));

            for (String block : blocks) {
                if (block == null || block.isBlank()) {
                    continue;
                }
                String trimmed = block.trim();
                String metaKey = detectStaticMetaSectionKey(trimmed);
                if (metaKey != null) {
                    String metaHash8 = sha256Hex8(normalizeWhitespace(trimmed));
                    String prevHash = state.metaSectionHashByKey.get(metaKey);
                    if (metaHash8.equals(prevHash)) {
                        appendLine(out, "[OBS_UNCHANGED: " + metaKey + "#" + metaHash8 + "]");
                        suppressedMeta++;
                    } else {
                        state.metaSectionHashByKey.put(metaKey, metaHash8);
                        appendLine(out, trimmed);
                    }
                    continue;
                }

                if (trimmed.length() >= MIN_DEDUP_BLOCK_CHARS) {
                    String normHash = sha256Hex(normalizeWhitespace(trimmed));
                    String hash8 = normHash.substring(0, 8);
                    Integer firstStep = state.blockFirstSeenStepByHash.get(normHash);
                    if (firstStep != null && firstStep < stepIndex) {
                        appendLine(out, "[DUP_REF block=" + hash8 + "@T" + firstStep + ", chars=" + trimmed.length() + "]");
                        dedupBlocks++;
                    } else {
                        state.blockFirstSeenStepByHash.putIfAbsent(normHash, stepIndex);
                        appendLine(out, trimmed);
                    }
                } else {
                    appendLine(out, trimmed);
                }
            }

            emitted = out.toString().trim();
            if (emitted.isEmpty()) {
                emitted = "[OBS_DEDUP_SUMMARY: all blocks previously delivered at earlier steps]";
            }

            // 防倒胀保护与首轮零去重守恒：若未触发任何去重/抑制或句柄反而未变短，原样返回原文（0字符变更）
            if ((dedupBlocks == 0 && suppressedMeta == 0) || emitted.length() >= rawChars) {
                emitted = safeRaw;
                dedupBlocks = 0;
                suppressedMeta = 0;
            }

            int emittedChars = emitted.length();
            int savedChars = Math.max(0, rawChars - emittedChars);
            ObservationDeltaReceipt receipt = new ObservationDeltaReceipt(
                    safeReq, stepIndex, safeTool, rawChars, emittedChars, savedChars,
                    dedupBlocks, suppressedMeta, false, System.currentTimeMillis()
            );
            state.receipts.add(receipt);

            totalObservationsFiltered.incrementAndGet();
            if (dedupBlocks > 0) {
                totalDedupBlocksSuppressed.addAndGet(dedupBlocks);
            }
            if (suppressedMeta > 0) {
                totalMetaSectionsSuppressed.addAndGet(suppressedMeta);
            }
            if (savedChars > 0) {
                totalCharsSaved.addAndGet(savedChars);
            }
            return new FilteredObservationResult(emitted, receipt);
        }
    }

    /**
     * 判断目标文档的字符区间是否已在本会话前序轮次交付过（覆盖率 >= 0.85 即视为已交付）。
     */
    public boolean isPassageSpanDelivered(String requestId, String title, int startOffset, int endOffset) {
        if (title == null || title.isBlank() || endOffset <= startOffset) {
            return false;
        }
        RequestDedupState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return false;
        }
        String key = title.trim().toLowerCase(Locale.ROOT);
        synchronized (state) {
            List<SpanRecord> spans = state.deliveredSpansByTitle.get(key);
            if (spans == null || spans.isEmpty()) {
                return false;
            }
            int queryLen = endOffset - startOffset;
            for (SpanRecord span : spans) {
                int overlap = Math.max(0, Math.min(endOffset, span.endOffset()) - Math.max(startOffset, span.startOffset()));
                if ((double) overlap / queryLen >= DEFAULT_SPAN_OVERLAP_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 登记已成功向模型交付的文档偏移区间与内容指纹。
     */
    public void recordDeliveredSpan(String requestId, String title, int startOffset, int endOffset, String contentHash) {
        if (title == null || title.isBlank() || endOffset <= startOffset) {
            return;
        }
        RequestDedupState state = stateByRequest.computeIfAbsent(normalizeReqId(requestId), k -> new RequestDedupState());
        String key = title.trim().toLowerCase(Locale.ROOT);
        String safeHash = contentHash != null ? contentHash : "";
        synchronized (state) {
            state.deliveredSpansByTitle
                    .computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new SpanRecord(startOffset, endOffset, safeHash));
        }
    }

    public List<ObservationDeltaReceipt> getReceipts(String requestId) {
        RequestDedupState state = stateByRequest.get(normalizeReqId(requestId));
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return Collections.unmodifiableList(new ArrayList<>(state.receipts));
        }
    }

    public void clearRequest(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            stateByRequest.remove(normalizeReqId(requestId));
        }
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        long charsSaved = totalCharsSaved.get();
        map.put("totalObservationsFiltered", totalObservationsFiltered.get());
        map.put("totalDedupBlocksSuppressed", totalDedupBlocksSuppressed.get());
        map.put("totalMetaSectionsSuppressed", totalMetaSectionsSuppressed.get());
        map.put("totalCharsSaved", charsSaved);
        map.put("estimatedTokensSaved", charsSaved / 3L);
        map.put("prefixMutations", 0L);
        return Collections.unmodifiableMap(map);
    }

    private static boolean isErrorObservation(String text) {
        String head = text.stripLeading();
        return head.startsWith("[ERROR]")
                || head.startsWith("[TOOL_ERROR")
                || head.startsWith("Error:")
                || head.startsWith("ERROR:")
                || head.startsWith("Exception")
                || head.startsWith("HTTP 4")
                || head.startsWith("HTTP 5")
                || head.startsWith("Tool execution failed");
    }

    private static String detectStaticMetaSectionKey(String block) {
        String lower = block.toLowerCase(Locale.ROOT);
        if (lower.startsWith("all dated sessions index") || lower.contains("all dated sessions index:")) {
            return "AllDatedSessionsIndex";
        }
        if (lower.startsWith("<temporal_evolution_timeline>")) {
            return "temporal_evolution_timeline";
        }
        if (lower.startsWith("<materialized_episodic_view")) {
            return "materialized_episodic_view_audit";
        }
        if (lower.startsWith("related linked titles")) {
            return "RelatedLinkedTitles";
        }
        if (lower.startsWith("[bcbt_unread")) {
            return "BCBT_UNREAD";
        }
        return null;
    }

    private static List<String> splitLogicalBlocks(String raw) {
        List<String> result = new ArrayList<>();
        String[] lines = raw.split("\\r?\\n", -1);
        StringBuilder current = new StringBuilder();
        String openXmlEndTag = null;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (openXmlEndTag != null) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
                if (trimmedLine.contains(openXmlEndTag)) {
                    result.add(current.toString());
                    current.setLength(0);
                    openXmlEndTag = null;
                }
                continue;
            }

            if (trimmedLine.startsWith("<temporal_evolution_timeline>")) {
                flushBlock(result, current);
                current.append(line);
                if (!trimmedLine.contains("</temporal_evolution_timeline>")) {
                    openXmlEndTag = "</temporal_evolution_timeline>";
                } else {
                    flushBlock(result, current);
                }
                continue;
            }
            if (trimmedLine.startsWith("<materialized_episodic_view")) {
                flushBlock(result, current);
                current.append(line);
                if (!trimmedLine.contains("</materialized_episodic_view_audit>")
                        && !trimmedLine.contains("</materialized_episodic_view>")) {
                    openXmlEndTag = trimmedLine.contains("audit")
                            ? "</materialized_episodic_view_audit>"
                            : "</materialized_episodic_view>";
                } else {
                    flushBlock(result, current);
                }
                continue;
            }

            if (trimmedLine.isEmpty() || "---".equals(trimmedLine)) {
                flushBlock(result, current);
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        flushBlock(result, current);
        return result;
    }

    private static void flushBlock(List<String> result, StringBuilder current) {
        if (current.length() > 0) {
            String s = current.toString().trim();
            if (!s.isEmpty()) {
                result.add(s);
            }
            current.setLength(0);
        }
    }

    private static void appendLine(StringBuilder sb, String block) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(block);
    }

    private static String normalizeWhitespace(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String sha256Hex8(String input) {
        return sha256Hex(input).substring(0, 8);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static String normalizeReqId(String requestId) {
        return (requestId == null || requestId.isBlank()) ? "req-idg-co" : requestId.trim();
    }
}
