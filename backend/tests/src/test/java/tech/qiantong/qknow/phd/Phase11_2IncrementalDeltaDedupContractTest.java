package tech.qiantong.qknow.phd;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.ai.vector.LocalitySensitiveFeatureHasher;
import tech.qiantong.qknow.hermes.cognitive.context.CausalEpisodeNode;
import tech.qiantong.qknow.hermes.cognitive.context.HystereticDualZoneContextFolder;
import tech.qiantong.qknow.hermes.cognitive.context.IncrementalDeltaObservationDeduplicator;
import tech.qiantong.qknow.hermes.memory.bitemporal.ColdStandbyTier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 11.2 (IDG-CO) 前缀守恒增量观测去重与零破缓存保护契约单测。
 */
class Phase11_2IncrementalDeltaDedupContractTest {

    @Test
    void staticMetaSectionsAndRepeatedBlocksAreDeduplicatedWithoutMutatingPrefix() {
        IncrementalDeltaObservationDeduplicator deduplicator = new IncrementalDeltaObservationDeduplicator();
        String repeatedPassage = "Session_0 recorded on 2023/05/21 at Trader Joe's and Walmart. ".repeat(4);
        String metaTimeline = """
                <temporal_evolution_timeline>
                #1/2 [DeltaFromRef=9d] Trader Joe's groceries purchase
                #2/2 [DeltaFromRef=5d] Thrive Market order $150
                All Dated Sessions Index: session_0(2023/05/21, -9d); session_2(2023/05/25, -5d)
                </temporal_evolution_timeline>""";
        String metaAudit = """
                <materialized_episodic_view_audit grade="COMPLETE_VERIFIED" entity_cov="1.00" num_recall="1.00">All entities verified.</materialized_episodic_view_audit>""";

        String turn1Raw = repeatedPassage + "\n\n" + metaTimeline + "\n\n" + metaAudit;
        String turn2Raw = repeatedPassage + "\n\n"
                + "New unique session_2 content discussing organic snacks purchased from Thrive Market for $150. ".repeat(2)
                + "\n\n" + metaTimeline + "\n\n" + metaAudit;

        var res1 = deduplicator.filterToolObservation("req-cache", 1, "search_hippocampal_graph", turn1Raw);
        assertEquals(0, res1.receipt().savedChars(), "首轮出现的所有元块和段落应完整交付");
        assertFalse(res1.receipt().prefixMutationOccurred());

        var res2 = deduplicator.filterToolObservation("req-cache", 2, "search_hippocampal_graph", turn2Raw);
        assertFalse(res2.receipt().prefixMutationOccurred(), "严禁篡改前序前缀，prefixMutationOccurred 必须恒为 false");
        assertTrue(res2.receipt().deduplicatedBlocks() >= 1, "重复长段落必须折叠为 [DUP_REF ...] 句柄");
        assertTrue(res2.receipt().suppressedRepeatedMetaSections() >= 2, "未变化的静态元块必须压缩为 [OBS_UNCHANGED]");
        assertTrue(res2.receipt().savedChars() > 200, "重复观测必须产生显著字符压缩");
        assertTrue(res2.emittedOutput().contains("[DUP_REF block="));
        assertTrue(res2.emittedOutput().contains("[OBS_UNCHANGED:"));
        assertTrue(res2.emittedOutput().contains("Thrive Market"), "全新独有证据绝不能被误删");
    }

    @Test
    void errorObservationsAreExemptFromDeduplicationAndDeliveredSpanTrackingWorks() {
        IncrementalDeltaObservationDeduplicator deduplicator = new IncrementalDeltaObservationDeduplicator();
        String err = "[TOOL_ERROR: python sandbox failed with ValueError when parsing table row 3 because column value had currency symbol]";

        var r1 = deduplicator.filterToolObservation("req-err", 1, "execute_verified_codeact", err);
        var r2 = deduplicator.filterToolObservation("req-err", 2, "execute_verified_codeact", err);
        assertEquals(err, r1.emittedOutput());
        assertEquals(err, r2.emittedOutput(), "遵循 EMNLP 2026 报错免折叠律，错误观察必须完整保留");

        deduplicator.recordDeliveredSpan("req-err", "session_0", 2000, 3100, "h1");
        assertTrue(deduplicator.isPassageSpanDelivered("req-err", "session_0", 2050, 3050));
        assertFalse(deduplicator.isPassageSpanDelivered("req-err", "session_0", 9000, 10100));
    }

    @Test
    void prefixConservingFolderAvoidsPrematureFoldingUntilHardCapThenRestoresLosslessly() {
        HystereticDualZoneContextFolder folder = new HystereticDualZoneContextFolder(6000, 10000, "p11-fold");
        folder.lockZone0Prefix("req-pc", "STATIC_ZONE0_SYSTEM_PROMPT_V6");

        // 常规多轮：累计 ~12,000 tokens（超过旧 H_hi=10,000 但远低于 20,000 硬顶）
        for (int i = 1; i <= 4; i++) {
            var rec = folder.appendWithPrefixConservingPolicy(
                    "req-pc",
                    new CausalEpisodeNode(
                            "ep-" + i,
                            CausalEpisodeNode.EpisodeType.EXPLORATORY,
                            "",
                            "Episode content " + i,
                            3000,
                            List.of(),
                            LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("ep " + i, 1024),
                            false,
                            ColdStandbyTier.ACTIVE_HOT,
                            System.currentTimeMillis()
                    ),
                    true,
                    HystereticDualZoneContextFolder.DEFAULT_V6_APPEND_ONLY_GUARD_TOKENS
            );
            assertFalse(rec.hysteresisTriggered(), "在 20k 硬顶内必须保持 Append-Only 零折叠以保护 Prefix Cache");
            assertEquals(0, rec.foldedEpisodeCount());
            assertTrue(rec.zone0PrefixPreserved());
        }
        assertFalse(folder.hasFoldedEpisodes("req-pc"));

        // 突破 20,000 硬顶后触发受控折叠，并支持 100% 无损时间旅行还原
        var triggerRec = folder.appendWithPrefixConservingPolicy(
                "req-pc",
                new CausalEpisodeNode(
                        "ep-5",
                        CausalEpisodeNode.EpisodeType.EXPLORATORY,
                        "",
                        "Overflow episode content 5",
                        9500,
                        List.of(),
                        LocalitySensitiveFeatureHasher.projectToUnitSphereFloat("ep 5", 1024),
                        false,
                        ColdStandbyTier.ACTIVE_HOT,
                        System.currentTimeMillis()
                ),
                true,
                HystereticDualZoneContextFolder.DEFAULT_V6_APPEND_ONLY_GUARD_TOKENS
        );
        assertTrue(triggerRec.hysteresisTriggered());
        assertTrue(triggerRec.foldedEpisodeCount() >= 1);
        assertTrue(folder.hasFoldedEpisodes("req-pc"));
        String firstFoldedId = triggerRec.foldedEpisodeIds().get(0);
        assertTrue(folder.restoreFoldedEpisode("req-pc", firstFoldedId), "折叠节点必须可 100% 无损恢复");
    }
}
