package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicDatasetViewMaterializer;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicRelationalStateExtractor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.PredicateCardinality;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ReadOnlyDatasetView;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.StructuredEpisodicFact;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorSpec;
import tech.qiantong.qknow.hermes.tool.codeact.CodeActProgrammaticBridge;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActReceipt;
import tech.qiantong.qknow.hermes.tool.codeact.VerifiedCodeActSandboxRuntime;
import tech.qiantong.qknow.hermes.tool.hoare.HoareToolContractGateway;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9.3 契约单测：BTER-MV 相对时间窗、抗历史死边挤出活跃槽索引、防幻觉跨度检验与直连 CodeAct 零误差求值。
 */
class Phase9_3EpisodicRelationalViewContractTest {

    @Test
    void relativeTemporalWindowParserHandlesBilingualExpressionsAccurately() {
        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();
        long refMs = Instant.parse("2025-05-15T12:00:00Z").toEpochMilli();

        TemporalAnchorSpec past30 = parser.parse("How many times did Alice visit gym in the past 30 days?", refMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.EVOLUTION_WINDOW, past30.mode());
        assertEquals(Instant.parse("2025-04-15T00:00:00Z").toEpochMilli(), past30.validFromEpochMs());
        assertEquals(refMs, past30.validToEpochMs());

        TemporalAnchorSpec zhRecent = parser.parse("最近7天Alice花了多少预算？", refMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.EVOLUTION_WINDOW, zhRecent.mode());
        assertEquals(Instant.parse("2025-05-08T00:00:00Z").toEpochMilli(), zhRecent.validFromEpochMs());

        TemporalAnchorSpec yesterday = parser.parse("What was Alice's weight yesterday?", refMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.POINT_AS_OF, yesterday.mode());
        assertEquals(Instant.parse("2025-05-14T23:59:59.999Z").toEpochMilli(), yesterday.validToEpochMs());

        // 原有绝对日期必须保持 100% 兼容
        TemporalAnchorSpec abs = parser.parse("截至2024-03-01当时的情况", refMs);
        assertEquals(TemporalAnchorSpec.QueryTemporalMode.POINT_AS_OF, abs.mode());
        assertTrue(TemporalAnchorQueryParser.isExplicitlyAnchored(abs));
    }

    @Test
    void activeSlotLedgerResistsSixtyHistoricalDeadEdgesWithoutFailingSupersession() {
        ActiveSlotLedgerIndex index = new ActiveSlotLedgerIndex();
        long baseTime = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli();

        // 同一槽位连续演进 60 次历史死边（远超 Graphiti 默认 DEFAULT_SEARCH_LIMIT=10）
        for (int i = 1; i <= 60; i++) {
            long ts = baseTime + i * 86_400_000L;
            String val = (100 + i) + " USD";
            String turn = "My monthly budget is " + val;
            float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("user budget " + val, 1024);
            var fact = new StructuredEpisodicFact(
                    "f-" + i, "longmem-1", "user", "PERSON", "budget",
                    val, (double) (100 + i), "USD", ts, null, ts, null,
                    "sess-" + i, PredicateCardinality.FUNCTIONAL, "ACTIVE",
                    val, turn, vec
            );
            var rcpt = index.upsertFact(fact);
            if (i == 1) {
                assertEquals(ActiveSlotLedgerIndex.UpsertAction.INSERTED, rcpt.action());
            } else {
                assertEquals(ActiveSlotLedgerIndex.UpsertAction.SUPERSEDED_ACTIVE_SLOT, rcpt.action());
            }
        }

        // 第 61 次更新依然 O(1) 精准失效第 60 条活跃事实
        long ts61 = baseTime + 61 * 86_400_000L;
        StructuredEpisodicFact fact61 = new StructuredEpisodicFact(
                "f-61", "longmem-1", "user", "PERSON", "budget",
                "999 USD", 999.0, "USD", ts61, null, ts61, null,
                "sess-61", PredicateCardinality.FUNCTIONAL, "ACTIVE",
                "999 USD", "Updated budget is 999 USD.",
                LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("user budget 999 USD", 1024)
        );
        var rcpt61 = index.upsertFact(fact61);
        assertEquals(ActiveSlotLedgerIndex.UpsertAction.SUPERSEDED_ACTIVE_SLOT, rcpt61.action());
        assertEquals("f-60", rcpt61.supersededFactId());

        var latestFacts = index.queryFacts("longmem-1", TemporalAnchorSpec.latest(ts61), false);
        assertEquals(1, latestFacts.size(), "活跃截面仅应保留唯一最新活跃槽，杜绝历史死边挤出引发双活冲突");
        assertEquals("f-61", latestFacts.get(0).factId());
        assertEquals(60, index.countSupersededFacts("longmem-1"));
    }

    @Test
    void verbatimSpanInvariantRejectsHallucinationAndCountableEventsDeduplicateIdempotently() {
        float[] vec = LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("bought coffee at Thrive Market", 1024);
        long t0 = Instant.parse("2025-05-01T10:00:00Z").toEpochMilli();

        // 1. 伪造不在源文本中的跨度必须被直接拒绝
        assertThrows(IllegalArgumentException.class, () -> new StructuredEpisodicFact(
                "hallucinated-1", "scope-h", "user", "PERSON", "event:bought",
                "Rolex watch", 1.0, "count", t0, null, t0, null,
                "s1", PredicateCardinality.COUNTABLE_EVENT, "ACTIVE",
                "bought Rolex watch", "Today I only bought organic tea at Thrive Market.", vec
        ));

        // 2. 同一轮复述事件在时间窗内自动幂等去重
        ActiveSlotLedgerIndex index = new ActiveSlotLedgerIndex();
        StructuredEpisodicFact e1 = new StructuredEpisodicFact(
                "e-1", "scope-c", "user", "PERSON", "event:bought",
                "organic tea at Thrive Market", 1.0, "count", t0, null, t0, null,
                "s1", PredicateCardinality.COUNTABLE_EVENT, "ACTIVE",
                "bought organic tea at Thrive Market", "I bought organic tea at Thrive Market.", vec
        );
        StructuredEpisodicFact e1Dup = new StructuredEpisodicFact(
                "e-2", "scope-c", "user", "PERSON", "event:bought",
                "organic tea at Thrive Market", 1.0, "count", t0 + 15_000L, null, t0 + 15_000L, null,
                "s2", PredicateCardinality.COUNTABLE_EVENT, "ACTIVE",
                "bought organic tea at Thrive Market", "Yeah, I bought organic tea at Thrive Market earlier.", vec
        );
        assertEquals(ActiveSlotLedgerIndex.UpsertAction.INSERTED, index.upsertFact(e1).action());
        assertEquals(ActiveSlotLedgerIndex.UpsertAction.IDEMPOTENT_DEDUP_NOOP, index.upsertFact(e1Dup).action());
        assertEquals(1, index.queryFacts("scope-c", TemporalAnchorSpec.latest(t0 + 20_000L), false).size());
    }

    @Test
    void episodicMaterializerToCodeActComputesExactInitialFinalDeltaAndWindowCount() {
        ActiveSlotLedgerIndex ledger = new ActiveSlotLedgerIndex();
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine();
        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();
        EpisodicDatasetViewMaterializer materializer = new EpisodicDatasetViewMaterializer(
                parser, ledger, new EpisodicRelationalStateExtractor(), hippo
        );

        // 模拟 LongMemEval a11281a2 场景：4月支出 250 -> 5月支出 350，以及最近30天内 4 次独立采购事件
        long apr10 = Instant.parse("2025-04-10T09:00:00Z").toEpochMilli();
        long may02 = Instant.parse("2025-05-02T09:00:00Z").toEpochMilli();
        long may15 = Instant.parse("2025-05-15T12:00:00Z").toEpochMilli();

        materializer.ingestDialogueTurn("lm-eval", "s1", "Alice", "My monthly spending is 250 USD in April.", apr10);
        materializer.ingestDialogueTurn("lm-eval", "s2", "Alice", "Now my monthly spending updated to 350 USD in May.", may02);

        materializer.ingestDialogueTurn("lm-eval", "s3", "Alice", "I bought apples at Walmart.", Instant.parse("2025-04-20T10:00:00Z").toEpochMilli());
        materializer.ingestDialogueTurn("lm-eval", "s4", "Alice", "I bought milk at Thrive Market.", Instant.parse("2025-04-28T10:00:00Z").toEpochMilli());
        materializer.ingestDialogueTurn("lm-eval", "s5", "Alice", "I bought coffee beans at Trader Joes.", Instant.parse("2025-05-05T10:00:00Z").toEpochMilli());
        materializer.ingestDialogueTurn("lm-eval", "s6", "Alice", "I bought olive oil at Whole Foods.", Instant.parse("2025-05-11T10:00:00Z").toEpochMilli());

        try (VerifiedCodeActSandboxRuntime runtime = new VerifiedCodeActSandboxRuntime()) {
            CodeActProgrammaticBridge bridge = new CodeActProgrammaticBridge(
                    runtime,
                    new tech.qiantong.qknow.hermes.memory.bitemporal.causal.BiTemporalCausalEvolutionRetriever(
                            hippo,
                            new tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver(),
                            parser
                    ),
                    new HoareToolContractGateway()
            );

            // 1. 验证初末差值 350 - 250 = 100 精确求值（彻底解决 LongMemEval a11281a2 错报 350）
            String pyDelta = """
                    col_attr = DATASET_COLUMNS.index("attr_key")
                    col_num = DATASET_COLUMNS.index("numeric_value")
                    col_ts = DATASET_COLUMNS.index("valid_from_ms")
                    vals = sorted([(int(r[col_ts]), float(r[col_num])) for r in DATASET_ROWS if r[col_attr] == "spending" and r[col_num]])
                    delta = vals[-1][1] - vals[0][1]
                    print(f"{delta:.0f}")
                    """;
            VerifiedCodeActReceipt deltaReceipt = bridge.queryEpisodicViewAndExecuteCode(
                    "lm-eval",
                    "How much more was Alice's spending in the past 60 days from April to May?",
                    may15,
                    true,
                    pyDelta,
                    materializer
            );
            assertTrue(deltaReceipt.isSuccess(), "CodeAct 初末差值沙盒应成功: " + deltaReceipt.stderr());
            assertEquals("100", deltaReceipt.stdout().trim());

            // 2. 验证过去 30 天内可计数事件精确计数 == 4
            String pyCount = """
                    col_card = DATASET_COLUMNS.index("cardinality")
                    events = [r for r in DATASET_ROWS if r[col_card] == "COUNTABLE_EVENT"]
                    print(len(events))
                    """;
            VerifiedCodeActReceipt countReceipt = bridge.queryEpisodicViewAndExecuteCode(
                    "lm-eval",
                    "How many grocery purchases did Alice make in the past 30 days?",
                    may15,
                    false,
                    pyCount,
                    materializer
            );
            assertTrue(countReceipt.isSuccess(), "CodeAct 事件计数沙盒应成功: " + countReceipt.stderr());
            assertEquals("4", countReceipt.stdout().trim());
        }
    }
}
