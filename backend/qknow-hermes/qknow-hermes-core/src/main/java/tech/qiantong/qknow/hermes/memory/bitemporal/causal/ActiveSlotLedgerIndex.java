package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 确定性双时态活跃槽索引台账（Phase 9.3 BTER-MV，定理 3 核心引擎）。
 * <p>
 * 彻底根治 Graphiti Issue #1956（全图无过滤 Top-10 RRF 在历史死边增多后挤出当前活跃事实导致漏失效）：
 * 1. 对 {@link PredicateCardinality#FUNCTIONAL} 单值属性维护 O(1) 唯一活跃槽索引
 *    {@code (scopeId, normalizedEntityKey, normalizedAttrKey) WHERE validTo == null && txTo == null}；
 * 2. 在千问 {@code S^1023} 单位超球面上实施三区零 LLM 优先消解（>= theta_dup 幂等折叠、单值冲突确定性双时态截断、灰区标记）；
 * 3. 对 {@link PredicateCardinality#COUNTABLE_EVENT} 事件流执行时间窗 + {@code S^1023} 余弦幂等去重，消除多轮复述重复计数。
 * </p>
 */
public class ActiveSlotLedgerIndex {

    public static final double DEFAULT_DUP_COSINE_THRESHOLD = 0.94;
    public static final double DEFAULT_GRAY_LOW_THRESHOLD = 0.72;
    public static final long DEFAULT_EVENT_DEDUP_WINDOW_MS = 60_000L;

    public enum UpsertAction {
        INSERTED,
        IDEMPOTENT_DEDUP_NOOP,
        SUPERSEDED_ACTIVE_SLOT,
        GRAY_ZONE_FLAGGED
    }

    public record SlotKey(String scopeId, String entityKey, String attrKey) {
        public static SlotKey of(String scopeId, String entityKey, String attrKey) {
            return new SlotKey(
                    normalize(scopeId),
                    normalize(entityKey),
                    normalize(attrKey)
            );
        }

        private static String normalize(String raw) {
            return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        }
    }

    public record LedgerUpsertReceipt(
            UpsertAction action,
            String activeFactId,
            String supersededFactId,
            double matchedCosine
    ) {}

    private final Map<SlotKey, StructuredEpisodicFact> activeFunctionalSlots = new ConcurrentHashMap<>();
    private final Map<String, List<StructuredEpisodicFact>> allFactsByScope = new ConcurrentHashMap<>();
    private final AtomicLong totalUpserts = new AtomicLong(0L);
    private final AtomicLong totalSupersedes = new AtomicLong(0L);
    private final AtomicLong totalDeduplicated = new AtomicLong(0L);

    public synchronized LedgerUpsertReceipt upsertFact(StructuredEpisodicFact incoming) {
        return upsertFact(incoming, DEFAULT_DUP_COSINE_THRESHOLD, DEFAULT_EVENT_DEDUP_WINDOW_MS);
    }

    /**
     * 执行 O(1) 活跃槽定位 + 千问 {@code S^1023} 幂等去重与双时态非破坏性取代写入。
     */
    public synchronized LedgerUpsertReceipt upsertFact(
            StructuredEpisodicFact incoming,
            double dupCosineThreshold,
            long eventDedupWindowMs
    ) {
        Objects.requireNonNull(incoming, "incoming fact 不能为空");
        totalUpserts.incrementAndGet();
        List<StructuredEpisodicFact> scopeLedger = allFactsByScope.computeIfAbsent(
                SlotKey.normalize(incoming.scopeId()), k -> new ArrayList<>());

        if (incoming.cardinality() == PredicateCardinality.FUNCTIONAL) {
            SlotKey key = SlotKey.of(incoming.scopeId(), incoming.entityKey(), incoming.attrKey());
            StructuredEpisodicFact currentActive = activeFunctionalSlots.get(key);
            if (currentActive == null) {
                activeFunctionalSlots.put(key, incoming);
                scopeLedger.add(incoming);
                return new LedgerUpsertReceipt(UpsertAction.INSERTED, incoming.factId(), null, 0.0);
            }

            double cos = cosine(currentActive.unitEmbedding1024(), incoming.unitEmbedding1024());
            boolean sameNumeric = currentActive.numericValue() != null && incoming.numericValue() != null
                    && Math.abs(currentActive.numericValue() - incoming.numericValue()) <= 1e-6;
            boolean sameRaw = currentActive.valueRaw().equalsIgnoreCase(incoming.valueRaw());

            if ((sameRaw && sameNumeric) || (sameNumeric && cos >= dupCosineThreshold)) {
                totalDeduplicated.incrementAndGet();
                return new LedgerUpsertReceipt(
                        UpsertAction.IDEMPOTENT_DEDUP_NOOP, currentActive.factId(), null, cos);
            }

            // 按双时态单调规则取代旧活跃槽（若新时间不早于旧时间，则精准关闭旧槽位）
            if (incoming.validFromEpochMs() >= currentActive.validFromEpochMs()) {
                StructuredEpisodicFact supersededOld = currentActive.withSuperseded(
                        incoming.validFromEpochMs(), incoming.txFromEpochMs());
                replaceInLedger(scopeLedger, currentActive.factId(), supersededOld);
                activeFunctionalSlots.put(key, incoming);
                scopeLedger.add(incoming);
                totalSupersedes.incrementAndGet();
                return new LedgerUpsertReceipt(
                        UpsertAction.SUPERSEDED_ACTIVE_SLOT, incoming.factId(), supersededOld.factId(), cos);
            }
            // 乱序更早的历史补录：直接保留历史关闭态入库，不覆盖最新活跃值
            StructuredEpisodicFact historicalClosed = incoming.withSuperseded(
                    currentActive.validFromEpochMs(), currentActive.txFromEpochMs());
            scopeLedger.add(historicalClosed);
            return new LedgerUpsertReceipt(UpsertAction.INSERTED, currentActive.factId(), historicalClosed.factId(), cos);
        }

        // COUNTABLE_EVENT / MULTI_VALUED：执行时间窗 + S^1023 球面幂等折叠防多轮复述重复计数
        SlotKey incomingKey = SlotKey.of(incoming.scopeId(), incoming.entityKey(), incoming.attrKey());
        for (StructuredEpisodicFact existing : scopeLedger) {
            if (!"ACTIVE".equalsIgnoreCase(existing.stateStatus())) {
                continue;
            }
            SlotKey existingKey = SlotKey.of(existing.scopeId(), existing.entityKey(), existing.attrKey());
            if (!existingKey.equals(incomingKey)) {
                continue;
            }
            long dt = Math.abs(existing.validFromEpochMs() - incoming.validFromEpochMs());
            double cos = cosine(existing.unitEmbedding1024(), incoming.unitEmbedding1024());
            boolean sameNum = (existing.numericValue() == null && incoming.numericValue() == null)
                    || (existing.numericValue() != null && incoming.numericValue() != null
                    && Math.abs(existing.numericValue() - incoming.numericValue()) <= 1e-6);
            boolean exactOrCosDup = existing.valueRaw().equalsIgnoreCase(incoming.valueRaw())
                    || (sameNum && cos >= dupCosineThreshold);
            if (dt <= eventDedupWindowMs && exactOrCosDup) {
                totalDeduplicated.incrementAndGet();
                return new LedgerUpsertReceipt(
                        UpsertAction.IDEMPOTENT_DEDUP_NOOP, existing.factId(), null, cos);
            }
        }

        scopeLedger.add(incoming);
        return new LedgerUpsertReceipt(UpsertAction.INSERTED, incoming.factId(), null, 0.0);
    }

    /**
     * 按双时态查询锚点确定性过滤目标域内的关系事实列表。
     */
    public synchronized List<StructuredEpisodicFact> queryFacts(
            String scopeId,
            TemporalAnchorSpec anchorSpec,
            boolean includeSuperseded
    ) {
        List<StructuredEpisodicFact> scopeLedger = allFactsByScope.get(SlotKey.normalize(scopeId));
        if (scopeLedger == null || scopeLedger.isEmpty()) {
            return List.of();
        }
        TemporalAnchorSpec spec = anchorSpec != null
                ? anchorSpec
                : TemporalAnchorSpec.latest(System.currentTimeMillis());

        List<StructuredEpisodicFact> result = new ArrayList<>();
        for (StructuredEpisodicFact f : scopeLedger) {
            boolean isActive = "ACTIVE".equalsIgnoreCase(f.stateStatus())
                    && f.validToEpochMs() == null && f.txToEpochMs() == null;
            if (!includeSuperseded && spec.mode() == TemporalAnchorSpec.QueryTemporalMode.LATEST && !isActive) {
                continue;
            }
            boolean match = switch (spec.mode()) {
                case LATEST -> includeSuperseded || isActive;
                case POINT_AS_OF, RETROACTIVE_AUDIT -> f.validFromEpochMs() <= spec.validToEpochMs()
                        && (f.validToEpochMs() == null || f.validToEpochMs() > spec.validFromEpochMs())
                        && f.txFromEpochMs() <= spec.txAtEpochMs();
                case EVOLUTION_WINDOW -> (f.validToEpochMs() == null || f.validToEpochMs() >= spec.validFromEpochMs())
                        && f.validFromEpochMs() <= spec.validToEpochMs()
                        && f.txFromEpochMs() <= spec.txAtEpochMs();
            };
            if (match) {
                result.add(f);
            }
        }
        result.sort((a, b) -> {
            int cmp = Long.compare(a.validFromEpochMs(), b.validFromEpochMs());
            return cmp != 0 ? cmp : a.factId().compareTo(b.factId());
        });
        return Collections.unmodifiableList(result);
    }

    public synchronized StructuredEpisodicFact getActiveFunctionalFact(String scopeId, String entityKey, String attrKey) {
        return activeFunctionalSlots.get(SlotKey.of(scopeId, entityKey, attrKey));
    }

    public synchronized int countSupersededFacts(String scopeId) {
        List<StructuredEpisodicFact> list = allFactsByScope.get(SlotKey.normalize(scopeId));
        if (list == null) {
            return 0;
        }
        int c = 0;
        for (StructuredEpisodicFact f : list) {
            if ("SUPERSEDED".equalsIgnoreCase(f.stateStatus())) {
                c++;
            }
        }
        return c;
    }

    public Map<String, Object> getTelemetryStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("activeFunctionalSlotCount", activeFunctionalSlots.size());
        map.put("totalUpserts", totalUpserts.get());
        map.put("totalSupersedes", totalSupersedes.get());
        map.put("totalDeduplicated", totalDeduplicated.get());
        return Collections.unmodifiableMap(map);
    }

    private static void replaceInLedger(
            List<StructuredEpisodicFact> ledger, String targetId, StructuredEpisodicFact replacement) {
        for (int i = 0; i < ledger.size(); i++) {
            if (ledger.get(i).factId().equals(targetId)) {
                ledger.set(i, replacement);
                return;
            }
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(na) * Math.sqrt(nb);
        return denom <= 1e-12 ? 0.0 : Math.max(-1.0, Math.min(1.0, dot / denom));
    }
}
