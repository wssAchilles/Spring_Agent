package tech.qiantong.qknow.hermes.agent.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HarnessEvidenceStage8Test {

    @Test
    void usageSupplierDistinguishesMeteredFromUnmeteredEvidence() {
        HarnessEvidenceCollector known = new HarnessEvidenceCollector("known", 1L, null);
        known.recordCostAndTokens(999, 9.0);
        known.setUsageSupplier(() -> new HarnessUsageSnapshot(
                UsageStatus.KNOWN, 120, 40, 160, 0, 0, 0.02, 1, 0));
        HarnessEvidenceRecord metered = known.finalizeEvidence();
        assertEquals(UsageStatus.KNOWN, metered.usageStatus());
        assertEquals(160, metered.estimatedTokens());
        assertEquals(0.02, metered.estimatedCostCny());

        HarnessEvidenceCollector unknown = new HarnessEvidenceCollector("unknown", 1L, null);
        unknown.recordCostAndTokens(999, 9.0);
        unknown.setUsageSupplier(HarnessUsageSnapshot::unknown);
        HarnessEvidenceRecord unmetered = unknown.finalizeEvidence();
        assertEquals(UsageStatus.UNKNOWN, unmetered.usageStatus());
        assertEquals(0, unmetered.estimatedTokens());
        assertEquals(0.0, unmetered.estimatedCostCny());

        HarnessEvidenceCollector legacy = new HarnessEvidenceCollector("legacy", 1L, null);
        legacy.recordCostAndTokens(999, 9.0);
        assertEquals(999, legacy.finalizeEvidence().estimatedTokens());
        assertEquals(UsageStatus.UNKNOWN, legacy.finalizeEvidence().usageStatus());
    }

    @Test
    void guardRejectionsCannotBeRecordedAsSuccessfulTools() {
        HarnessEvidenceCollector collector = new HarnessEvidenceCollector("guard", 1L, null);
        collector.recordToolExecution("tool", 1, true, "input", "[HARNESS_GUARD_REJECTED:PERMISSION]");
        collector.recordGuardBlockedToolExecution("tool", 0, "CYCLE", "input", "blocked");
        assertEquals(2, collector.toolExecutionsSnapshot().size());
        HarnessEvidenceRecord record = collector.finalizeEvidence();
        assertTrue(record.guardBlocked());
        assertEquals(2, record.guardBlockedCount());
        assertTrue(record.toolExecutions().stream().allMatch(entry -> entry.guardBlocked() && !entry.success()));
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessEvidenceRecord.ToolExecutionEntry("tool", 0, true, true, "CYCLE", "", ""));
    }

    @Test
    void newEvidenceFieldsAreDigestBoundAndLegacyConstructorDefaultsRemain() {
        HarnessEvidenceCollector collector = new HarnessEvidenceCollector("digest", 1L, null);
        collector.recordCapabilityTag("RAG");
        collector.setRagAttributionSupplier(() -> RagAttributionSummary.of(2, 1, 0.7));
        HarnessEvidenceRecord record = collector.finalizeEvidence();
        String digest = record.computeDigest();
        assertTrue(record.verifyDigest(digest));
        assertEquals("RAG", record.capabilityTag());
        assertEquals(RagAttributionStatus.PARTIALLY_USED, record.ragAttribution().status());

        HarnessEvidenceRecord changed = new HarnessEvidenceRecord(
                record.evidenceId(), record.requestId(), record.botId(), record.voucherFingerprint(),
                record.voucherVersion(), record.timestamp(), record.userInputSummary(),
                record.ragDocumentCount(), record.ragTopScore(), record.toolExecutions(),
                record.estimatedTokens(), record.estimatedCostCny(), record.executionDurationMs(),
                record.finalOutputSummary(), record.executionMode(), record.usageStatus(),
                "PROMPT_ONLY", record.guardBlocked(), record.guardBlockedCount(), record.ragAttribution());
        assertFalse(changed.verifyDigest(digest));

        HarnessEvidenceRecord legacy = new HarnessEvidenceRecord(
                record.evidenceId(), record.requestId(), record.botId(), record.voucherFingerprint(),
                record.voucherVersion(), record.timestamp(), record.userInputSummary(),
                record.ragDocumentCount(), record.ragTopScore(), record.toolExecutions(),
                record.estimatedTokens(), record.estimatedCostCny(), record.executionDurationMs(),
                record.finalOutputSummary(), record.executionMode());
        assertEquals(UsageStatus.UNKNOWN, legacy.usageStatus());
        assertEquals("PROMPT_ONLY", legacy.capabilityTag());
    }
}
