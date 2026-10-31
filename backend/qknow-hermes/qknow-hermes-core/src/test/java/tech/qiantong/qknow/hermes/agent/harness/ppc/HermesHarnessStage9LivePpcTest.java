package tech.qiantong.qknow.hermes.agent.harness.ppc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import tech.qiantong.qknow.ai.deepseek.DeepSeekCompatibleChatModel;
import tech.qiantong.qknow.hermes.config.ChatModelFactory;

import static org.junit.jupiter.api.Assertions.*;

@Tag("live")
class HermesHarnessStage9LivePpcTest {
    @Test
    void runsBoundedPpcOnlyWithRealKeyAndVerifiesEvidenceManifest() throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        ChatModelFactory factory = new ChatModelFactory() {
            @Override
            public ChatModel getChatModel(String platform, String baseUrl, String ignoredKey, String modelName) {
                return new DeepSeekCompatibleChatModel("https://api.deepseek.com", key,
                        "deepseek-flash", null, 1024);
            }
        };
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("AGENTS.md"))) root = root.getParent();
        assertNotNull(root, "repository root not found");
        Path output = root.resolve("experiment/outputs/harness9-live");
        PpcRun run = new PpcOrchestrator(factory, output).run(key);
        if (key == null || key.isBlank()) {
            assertEquals(PpcMode.EMPIRICAL_BLOCKED, run.mode());
            assertEquals(0, run.totalModelCallsExecuted());
        } else {
            assertTrue(run.mode() == PpcMode.REAL_PPC || run.mode() == PpcMode.BUDGET_EXHAUSTED
                    || run.mode() == PpcMode.EMPIRICAL_BLOCKED);
            assertTrue(run.totalProbesExecuted() <= 20);
            assertTrue(run.totalModelCallsExecuted() <= 60);
        }
        Path directory = output.resolve(run.runId());
        for (var entry : run.checksumsSha256().entrySet()) {
            byte[] bytes = Files.readAllBytes(directory.resolve(entry.getKey()));
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(entry.getValue(), actual, entry.getKey());
        }
        String manifest = Files.readString(directory.resolve("checksums.sha256"), StandardCharsets.UTF_8);
        assertEquals(run.checksumsSha256().size(), manifest.lines().count());
        if (key != null && !key.isBlank()) {
            assertFalse(Files.readString(directory.resolve("ppc_summary.json")).contains(key));
        }
    }
}
