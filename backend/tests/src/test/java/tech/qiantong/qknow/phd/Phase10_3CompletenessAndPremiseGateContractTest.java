package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.benchmark.ablation.HnscdsAblationConfig;
import tech.qiantong.qknow.hermes.cognitive.control.UnitSphereEmbeddingResolver;
import tech.qiantong.qknow.hermes.cognitive.verification.AttestedToolWitness;
import tech.qiantong.qknow.hermes.cognitive.verification.EvidenceGrade;
import tech.qiantong.qknow.hermes.cognitive.verification.PremiseGroundingAuditor;
import tech.qiantong.qknow.hermes.cognitive.verification.QuestionRequirementSpecCompiler;
import tech.qiantong.qknow.hermes.cognitive.verification.ReasoningChainConsistencyAuditor;
import tech.qiantong.qknow.hermes.memory.bitemporal.BiTemporalHippocampalGraphEngine;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.ActiveSlotLedgerIndex;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicDatasetViewMaterializer;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicRelationalStateExtractor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessAuditor;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.EpisodicViewCompletenessCertificate.CompletenessGrade;
import tech.qiantong.qknow.hermes.memory.bitemporal.causal.TemporalAnchorQueryParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 10.3 (CC-E2R + PG-FRSV) 离线契约单测：
 * 验证双向商户绑定抽取、情景视图三态完备性证书划分、
 * 第 7 维复合前提概念偷换拦截（table tennis vs tennis）及合法拒答豁免保护。
 */
class Phase10_3CompletenessAndPremiseGateContractTest {

    @Test
    void bidirectionalMerchantExtractorBindsPrecedingMerchantsWithoutDroppingToUser() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        EpisodicRelationalStateExtractor extractor = new EpisodicRelationalStateExtractor(resolver);
        long now = System.currentTimeMillis();

        String conv = "I went to Best Buy last Friday and bought a curved monitor for $350. "
                + "Later at Trader Joe's I spent $65 on groceries, and paid $120 at Whole Foods.";
        var extracted = extractor.extractFromTurn("scope-1", "sess-1", "user", conv, now);

        long boundCount = extracted.stream()
                .filter(f -> "spending".equalsIgnoreCase(f.attrKey()))
                .filter(f -> f.entityKey() != null && !"user".equalsIgnoreCase(f.entityKey()))
                .count();
        assertTrue(boundCount >= 2, "同句左侧商户回溯应绑出 Best Buy / Trader Joe's 等具名商户，而非退化为 user: bound=" + boundCount);
        assertEquals(3, EpisodicRelationalStateExtractor.countCurrencyMentions(conv));
    }

    @Test
    void completenessAuditorCertifiesCompletePartialAndSuppressGrades() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        TemporalAnchorQueryParser parser = new TemporalAnchorQueryParser();
        ActiveSlotLedgerIndex ledger = new ActiveSlotLedgerIndex();
        EpisodicRelationalStateExtractor extractor = new EpisodicRelationalStateExtractor(resolver);
        BiTemporalHippocampalGraphEngine hippo = new BiTemporalHippocampalGraphEngine("e2r-cert");
        EpisodicDatasetViewMaterializer materializer = new EpisodicDatasetViewMaterializer(
                parser, ledger, extractor, hippo
        );
        EpisodicViewCompletenessAuditor completenessAuditor = new EpisodicViewCompletenessAuditor();
        long t0 = System.currentTimeMillis();

        String turnComplete = "I spent $40 at Target and bought a lamp for $60 at Ikea.";
        materializer.ingestDialogueTurn("s-complete", "sess-1", "user", turnComplete, t0);
        var certComplete = materializer.materializeCertifiedViewForQuery(
                "s-complete", "How much did I spend total?", t0, true, List.of(turnComplete), 10, completenessAuditor
        );
        assertEquals(CompletenessGrade.COMPLETE_AUTHORITATIVE, certComplete.certificate().grade());

        // 含截断展示 (maxDisplayRows = 1 < totalRows) 或相对时间表述时应降为 PARTIAL_SUPPLEMENTARY
        String turnRel = "Last week I bought snacks for $25 at Costco and $30 at Walmart.";
        materializer.ingestDialogueTurn("s-partial", "sess-2", "user", turnRel, t0);
        var certPartial = materializer.materializeCertifiedViewForQuery(
                "s-partial", "How much did I spend last week?", t0, true, List.of(turnRel), 1, completenessAuditor
        );
        assertEquals(CompletenessGrade.PARTIAL_SUPPLEMENTARY, certPartial.certificate().grade());
        assertTrue(certPartial.certificate().advisoryBanner().contains("PARTIAL_VIEW_WARNING"));

        // 原文存在大量消费金额但视图零具名商户（纯 user 匿名）时应触发 UNRELIABLE_SUPPRESS
        String turnUnreliable = "I spent $100 yesterday, $200 today, and $300 earlier.";
        materializer.ingestDialogueTurn("s-suppress", "sess-3", "user", turnUnreliable, t0);
        var certSuppress = materializer.materializeCertifiedViewForQuery(
                "s-suppress", "How much did I spend?", t0, true, List.of(turnUnreliable), 10, completenessAuditor
        );
        assertEquals(CompletenessGrade.UNRELIABLE_SUPPRESS, certSuppress.certificate().grade());
    }

    @Test
    void premiseGroundingAuditorBlocksTopicShiftAndProtectsJustifiedAbstention() {
        UnitSphereEmbeddingResolver resolver = new UnitSphereEmbeddingResolver();
        PremiseGroundingAuditor premiseAuditor = new PremiseGroundingAuditor(resolver);
        ReasoningChainConsistencyAuditor consistencyAuditor = new ReasoningChainConsistencyAuditor(resolver);
        consistencyAuditor.setPremiseGroundingAuditor(premiseAuditor);
        QuestionRequirementSpecCompiler specCompiler = new QuestionRequirementSpecCompiler();
        HnscdsAblationConfig v5Cfg = HnscdsAblationConfig.g17V5CompletenessPremise(42L);

        String q = "How often do I play table tennis every week?";
        String ev = "User said: I play tennis three times a week at the neighborhood court.";
        List<AttestedToolWitness> witnesses = List.of(AttestedToolWitness.createSigned(
                "w1", "search_hippocampal_graph", "table tennis", ev, EvidenceGrade.TOOL_WITNESS, 1000L, "sec"
        ));

        // 1. 若模型用 tennis 偷换 table tennis 作答，FRSV 第 7 维必须重拳硬拦截
        var badAudit = consistencyAuditor.auditWithSpec(
                "pg-bad", q, "The user plays tennis three times a week.",
                "Final Answer: three times a week",
                witnesses, ev, null, specCompiler.compile(q), false, v5Cfg
        );
        assertTrue(badAudit.hasHardViolations(), "table tennis -> tennis 概念偷换必须触发硬违约");
        assertTrue(badAudit.violations().stream().anyMatch(v -> "PREMISE_TOPIC_SHIFT_UNGROUNDED".equals(v.code())));

        // 2. 若模型识别出概念缺失并诚实拒答，PG-FRSV 必须放行保护合法拒答（无假阳硬违约）
        var abstainAudit = consistencyAuditor.auditWithSpec(
                "pg-ok", q, "Evidence only mentions tennis, never table tennis.",
                "Final Answer: Insufficient information in the provided conversations.",
                witnesses, ev, null, specCompiler.compile(q), false, v5Cfg
        );
        assertFalse(abstainAudit.hasHardViolations(), "充分检索并给出明确拒答不应被打为违约: " + abstainAudit.violations());
    }
}
