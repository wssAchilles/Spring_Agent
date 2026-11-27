package tech.qiantong.qknow.hermes.memory.bitemporal.causal;

import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertex;
import tech.qiantong.qknow.hermes.memory.bitemporal.HippocampalVertexTier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 双时态情景-关系动态物化视图生成器（Phase 9.3 BTER-MV，连接对话抽取、活跃槽台账与 CodeAct 沙盒）。
 */
public class EpisodicDatasetViewMaterializer {

    public record MaterializedEpisodicViewReceipt(
            TemporalAnchorSpec anchorSpec,
            ReadOnlyDatasetView datasetView,
            int totalProjectedRows,
            int activeRows,
            int supersededRows,
            int deduplicatedIngestCount
    ) {}

    /**
     * Phase 10.3 CC-E2R 证书化情景物化结果：附带完备性覆盖率证书 {@link EpisodicViewCompletenessCertificate}。
     */
    public record CertifiedEpisodicViewReceipt(
            MaterializedEpisodicViewReceipt viewReceipt,
            EpisodicViewCompletenessCertificate certificate,
            List<StructuredEpisodicFact> projectedFacts
    ) {
        public CertifiedEpisodicViewReceipt {
            Objects.requireNonNull(viewReceipt, "viewReceipt 不能为空");
            Objects.requireNonNull(certificate, "certificate 不能为空");
            projectedFacts = List.copyOf(projectedFacts != null ? projectedFacts : List.of());
        }
    }

    private final TemporalAnchorQueryParser anchorParser;
    private final ActiveSlotLedgerIndex ledgerIndex;
    private final EpisodicRelationalStateExtractor extractor;
    private final BiTemporalHippocampalGraphEngine hippocampalGraphEngine;

    public EpisodicDatasetViewMaterializer(
            TemporalAnchorQueryParser anchorParser,
            ActiveSlotLedgerIndex ledgerIndex,
            EpisodicRelationalStateExtractor extractor,
            BiTemporalHippocampalGraphEngine hippocampalGraphEngine
    ) {
        this.anchorParser = anchorParser != null ? anchorParser : new TemporalAnchorQueryParser();
        this.ledgerIndex = Objects.requireNonNull(ledgerIndex, "ActiveSlotLedgerIndex 不能为空");
        this.extractor = extractor != null ? extractor : new EpisodicRelationalStateExtractor();
        this.hippocampalGraphEngine = hippocampalGraphEngine;
    }

    /**
     * 在线摄入对话回合事实并同步双写至双时态海马体图谱（若已装配）。
     */
    public int ingestDialogueTurn(
            String scopeId,
            String sessionId,
            String defaultEntityKey,
            String turnText,
            long turnEpochMs
    ) {
        List<StructuredEpisodicFact> facts = extractor.extractFromTurn(
                scopeId, sessionId, defaultEntityKey, turnText, turnEpochMs
        );
        int insertedOrSuperseded = 0;
        for (StructuredEpisodicFact fact : facts) {
            var res = ledgerIndex.upsertFact(fact);
            if (res.action() != ActiveSlotLedgerIndex.UpsertAction.IDEMPOTENT_DEDUP_NOOP) {
                insertedOrSuperseded++;
                syncToHippocampalGraph(fact);
            }
        }
        return insertedOrSuperseded;
    }

    /**
     * 直接摄入显式结构化情景事实（同步写入活跃台账与海马体因果边）。
     */
    public ActiveSlotLedgerIndex.LedgerUpsertReceipt ingestStructuredFact(StructuredEpisodicFact fact) {
        var receipt = ledgerIndex.upsertFact(fact);
        if (receipt.action() != ActiveSlotLedgerIndex.UpsertAction.IDEMPOTENT_DEDUP_NOOP) {
            syncToHippocampalGraph(fact);
        }
        return receipt;
    }

    /**
     * 基于问题文本解析相对/绝对双时态锚点，并投影出可供 CodeAct 直接运算的只读关系视图。
     */
    public MaterializedEpisodicViewReceipt materializeViewForQuery(
            String scopeId,
            String queryText,
            long referenceNowMs,
            boolean includeSuperseded
    ) {
        TemporalAnchorSpec anchorSpec = anchorParser.parse(queryText, referenceNowMs);
        List<StructuredEpisodicFact> matched = ledgerIndex.queryFacts(scopeId, anchorSpec, includeSuperseded);
        int supersededCount = ledgerIndex.countSupersededFacts(scopeId);
        ReadOnlyDatasetView view = ReadOnlyDatasetView.fromEpisodicFacts(
                "e2r-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                anchorSpec,
                matched,
                supersededCount
        );
        long dedupStat = ((Number) ledgerIndex.getTelemetryStats().getOrDefault("totalDeduplicated", 0L)).longValue();
        return new MaterializedEpisodicViewReceipt(
                anchorSpec,
                view,
                matched.size(),
                view.activeFactCount(),
                view.supersededFactCount(),
                (int) Math.min(Integer.MAX_VALUE, dedupStat)
        );
    }

    /**
     * Phase 10.3 CC-E2R：在投影情景视图的同时执行三元组完备性审计并签发可信分级证书。
     */
    public CertifiedEpisodicViewReceipt materializeCertifiedViewForQuery(
            String scopeId,
            String queryText,
            long referenceNowMs,
            boolean includeSuperseded,
            List<String> rawTurnTexts,
            int maxDisplayRows,
            EpisodicViewCompletenessAuditor auditor
    ) {
        TemporalAnchorSpec anchorSpec = anchorParser.parse(queryText, referenceNowMs);
        List<StructuredEpisodicFact> matched = ledgerIndex.queryFacts(scopeId, anchorSpec, includeSuperseded);
        int supersededCount = ledgerIndex.countSupersededFacts(scopeId);
        ReadOnlyDatasetView view = ReadOnlyDatasetView.fromEpisodicFacts(
                "e2r-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                anchorSpec,
                matched,
                supersededCount
        );
        long dedupStat = ((Number) ledgerIndex.getTelemetryStats().getOrDefault("totalDeduplicated", 0L)).longValue();
        MaterializedEpisodicViewReceipt viewReceipt = new MaterializedEpisodicViewReceipt(
                anchorSpec,
                view,
                matched.size(),
                view.activeFactCount(),
                view.supersededFactCount(),
                (int) Math.min(Integer.MAX_VALUE, dedupStat)
        );
        EpisodicViewCompletenessAuditor safeAuditor = auditor != null ? auditor : new EpisodicViewCompletenessAuditor();
        EpisodicViewCompletenessCertificate cert = safeAuditor.certify(rawTurnTexts, matched, maxDisplayRows);
        return new CertifiedEpisodicViewReceipt(viewReceipt, cert, matched);
    }

    private void syncToHippocampalGraph(StructuredEpisodicFact fact) {
        if (hippocampalGraphEngine == null || fact.unitEmbedding1024() == null) {
            return;
        }
        String subjId = "e2r_sub_" + fact.scopeId() + "_" + fact.entityKey();
        String objId = "e2r_obj_" + fact.factId();
        hippocampalGraphEngine.upsertVertex(new HippocampalVertex(
                subjId, fact.entityKey(), HippocampalVertexTier.PHRASE_ENTITY, fact.unitEmbedding1024()
        ));
        hippocampalGraphEngine.upsertVertex(new HippocampalVertex(
                objId, fact.valueRaw(), HippocampalVertexTier.PHRASE_ENTITY, fact.unitEmbedding1024()
        ));
        hippocampalGraphEngine.ingestCausalFactEdge(
                subjId,
                objId,
                fact.attrKey(),
                fact.cardinality(),
                fact.unitEmbedding1024(),
                fact.validFromEpochMs(),
                fact.txFromEpochMs(),
                fact.sessionId(),
                null
        );
    }
}
