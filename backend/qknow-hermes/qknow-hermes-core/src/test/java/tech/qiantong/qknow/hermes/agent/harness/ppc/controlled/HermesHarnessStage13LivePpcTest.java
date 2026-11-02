package tech.qiantong.qknow.hermes.agent.harness.ppc.controlled;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.hermes.agent.harness.evolution.AlgebraicHarnessMutator.Operator;
import tech.qiantong.qknow.hermes.agent.harness.experience.FileSystemExperienceStore;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
class HermesHarnessStage13LivePpcTest {
    @Test
    void runsOnlyOnExplicitOptInWithAnIsolatedDatabaseAndVerifiedKey() throws Exception {
        assertTrue(Boolean.getBoolean("stage13.live"), "set -Dstage13.live=true for this paid test only");
        String url = System.getProperty("stage13.jdbcUrl");
        String key = System.getenv("DEEPSEEK_API_KEY");
        assertNotNull(url, "isolated stage13.jdbcUrl is required");
        assertNotNull(key, "DEEPSEEK_API_KEY must be supplied securely");
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("AGENTS.md"))) root = root.getParent();
        assertNotNull(root, "repository root not found");
        try (var connection = DriverManager.getConnection(url)) {
            var run = new ControlledPpcRunner(new JdbcControlledFixtureSource(connection),
                    root.resolve("experiment/outputs/harness11b2a")).runLive(key);
            System.out.printf("Stage13 status=%s probes=%d modelCalls=%d tokens=%d costCny=%s selected=%s path=%s%n",
                    run.status(), run.probes().size(), run.modelCalls(), run.totalTokens(),
                    run.costCny(), run.selectedOperator(), run.directory());
            assertTrue(run.modelCalls() <= 100 && run.totalTokens() <= 200_000
                    && run.costCny() != null && run.costCny() <= 10.0D);
            assertTrue(run.status() == ControlledPpcRunner.Status.ADOPTED
                    || run.status() == ControlledPpcRunner.Status.NO_ADOPTED_EXPERIENCE,
                    "live evidence did not reach a complete quality verdict: " + run.status());
            assertNotNull(run.directory());
            for (String line : Files.readAllLines(run.directory().resolve("checksums.sha256"),
                    StandardCharsets.UTF_8)) {
                String[] columns = line.split("  ", 2);
                assertEquals(2, columns.length);
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(run.directory().resolve(columns[1]))));
                assertEquals(columns[0], actual, columns[1]);
            }
            var store = new FileSystemExperienceStore(run.directory().resolve("experience"));
            if (run.status() == ControlledPpcRunner.Status.ADOPTED) {
                assertEquals(50, run.probes().size());
                assertEquals(1, store.list(ControlledPpcRunner.DOMAIN).size());
                assertTrue(Files.isRegularFile(run.directory().resolve("provenance.json")));
                Operator selected = run.selectedOperator();
                assertTrue(selected == Operator.CONCISE_SYSTEM_PROMPT
                        || selected == Operator.ADAPTIVE_MEMORY_WINDOW);
            } else {
                assertTrue(store.list(ControlledPpcRunner.DOMAIN).isEmpty());
            }
        }
    }
}
