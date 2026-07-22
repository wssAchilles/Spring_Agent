package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagCandidate91DockerAuthorizationProbeTest {

    @Test
    @EnabledIfSystemProperty(
            named = RagCandidate91RecoverySupport.PROBE_PROPERTY,
            matches = "true")
    void probeDockerAuthorization() {
        RagCandidate91RecoverySupport.ProbeAudit audit =
                RagCandidate91RecoverySupport.probeDockerAuthorization();
        assertEquals(0, audit.probeResourceAccessCount());
        assertEquals(0, audit.recoveryNamespaceWriteCount());
        assertEquals(0, audit.containerStartCount());
        assertEquals(0, audit.databaseCreateDropCount());
        assertEquals(1, audit.dockerApiCalls());
    }
}
