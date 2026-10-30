package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.kernel.HermesHarnessKernel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagAttributionContractTest {
    @Test
    void distinguishesNoRagUnusedPartialAndFull() {
        assertEquals(RagAttributionStatus.NO_RAG, RagAttributionSummary.noRag().status());
        assertEquals(RagAttributionStatus.UNUSED, RagAttributionSummary.of(2, 0, 0.7).status());
        assertEquals(1.0, RagAttributionSummary.of(2, 0, 0.7).ragUnderutilization());
        assertEquals(RagAttributionStatus.PARTIALLY_USED, RagAttributionSummary.of(2, 1, 0.7).status());
        assertEquals(RagAttributionStatus.FULLY_USED, RagAttributionSummary.of(2, 2, 0.7).status());
        assertEquals(0.0, RagAttributionSummary.of(2, 2, 0.7).ragUnderutilization());
        assertEquals("FULLY_USED:2:2:0.7:0.0", RagAttributionSummary.of(2, 2, 0.7).canonical());
    }

    @Test
    void sessionPassesActualCountersToAnalyzer() {
        var kernel = new HermesHarnessKernel(2, 2, 2, 3, 2);
        var voucher = new HermesHarnessAssembler().createDefaultProductionVoucher(7L);
        var session = kernel.openSession("rag-attribution", voucher);
        var attribution = session.evaluateTrajectory(20, 5, 2, 1, 0.7, List.of()).attribution();
        assertEquals(4.0, attribution.reasoningExpansion());
        assertEquals(0.5, attribution.ragUnderutilization());
        assertEquals(RagAttributionStatus.PARTIALLY_USED, attribution.ragStatus());
        assertEquals(0.7, attribution.ragScore());
        assertEquals(RagAttributionStatus.NO_RAG,
                session.evaluateTrajectory(List.of()).attribution().ragStatus());
    }
}
