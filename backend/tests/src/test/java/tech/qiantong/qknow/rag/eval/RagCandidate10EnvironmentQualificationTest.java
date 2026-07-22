package tech.qiantong.qknow.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tech.qiantong.qknow.module.kmc.service.rag.nlp.JiebaNative;
import tech.qiantong.qknow.module.kmc.service.rag.rerank.ColbertNative;
import tech.qiantong.qknow.module.kmc.service.rag.sim.VecSimNative;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagCandidate10EnvironmentQualificationTest {

    private static final String QUALIFICATION_PROPERTY =
            "rag.eval.candidate10.environment-qualification";
    private static final String POSTGRES_IMAGE_NAME =
            "pgvector/pgvector:0.8.1-pg16@sha256:"
                    + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(POSTGRES_IMAGE_NAME)
            .asCompatibleSubstituteFor("postgres");
    private static final String DATABASE_NAME = "candidate10_qualification";
    private static final Path NO_NATIVE_PATH = Path.of(
            "/Users/achilles/Documents/许子祺/Agent/backend/tests/target/rag-eval/no-native");

    @Test
    @EnabledIfSystemProperty(named = QUALIFICATION_PROPERTY, matches = "true")
    void environmentIsQualified() throws Exception {
        JvmState initialState = currentJvmState();
        assertExpectedJvmState(initialState);

        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(DATABASE_NAME)
                .withUsername(DATABASE_NAME)
                .withPassword(DATABASE_NAME)
                .withEnv("TZ", "UTC")
                .withEnv("LANG", "C")
                .withEnv("LC_ALL", "C")
                .withEnv("POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale=C")
                .withReuse(false);
        try {
            DockerClientFactory.instance().client().pingCmd().exec();
            container.start();

            assertTrue(container.isRunning());
            assertEquals(DATABASE_NAME, container.getDatabaseName());
            assertEquals(POSTGRES_IMAGE.asCanonicalNameString(),
                    container.getDockerImageName());
            verifyPostgreSql(container);
        } finally {
            try {
                container.stop();
            } finally {
                assertFalse(container.isRunning());
                assertEquals(initialState, currentJvmState());
            }
        }
    }

    private static void assertExpectedJvmState(JvmState state) {
        assertEquals("true", System.getProperty(QUALIFICATION_PROPERTY));
        assertEquals("UTF-8", state.fileEncoding());
        assertEquals("en", state.language());
        assertEquals("US", state.country());
        assertEquals("", state.script());
        assertEquals("", state.variant());
        assertEquals("en-US", state.localeTag());
        assertEquals("UTC", state.timezone());
        assertEquals("", state.nativeLibraryDirectory());
        assertEquals(NO_NATIVE_PATH, Path.of(state.javaLibraryPath()).normalize());
        assertFalse(JiebaNative.isAvailable());
        assertFalse(VecSimNative.isAvailable());
        assertFalse(ColbertNative.isAvailable());
    }

    private static void verifyPostgreSql(PostgreSQLContainer<?> container)
            throws Exception {
        try (Connection connection = container.createConnection("");
             Statement statement = connection.createStatement()) {
            assertTrue(singleValue(statement, "SHOW server_version").startsWith("16."));
            assertEquals("UTF8", singleValue(statement, "SHOW server_encoding"));
            assertEquals("C", singleValue(statement, """
                    SELECT datcollate
                    FROM pg_catalog.pg_database
                    WHERE datname = current_database()
                    """));
            assertEquals("C", singleValue(statement, """
                    SELECT datctype
                    FROM pg_catalog.pg_database
                    WHERE datname = current_database()
                    """));
            assertEquals(ZoneOffset.UTC, ZoneId.of(
                    singleValue(statement, "SHOW TimeZone")).normalized());
            assertEquals("0", singleValue(statement, """
                    SELECT count(*)
                    FROM pg_catalog.pg_tables
                    WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                    """));
        }
    }

    private static String singleValue(Statement statement, String sql)
            throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            String value = resultSet.getString(1);
            assertFalse(resultSet.next());
            return value;
        }
    }

    private static JvmState currentJvmState() {
        return new JvmState(
                System.getProperty("file.encoding"),
                System.getProperty("user.language"),
                System.getProperty("user.country"),
                System.getProperty("user.script"),
                System.getProperty("user.variant"),
                Locale.getDefault().toLanguageTag(),
                TimeZone.getDefault().getID(),
                System.getProperty("qknow.native.lib.dir"),
                System.getProperty("java.library.path"));
    }

    private record JvmState(
            String fileEncoding,
            String language,
            String country,
            String script,
            String variant,
            String localeTag,
            String timezone,
            String nativeLibraryDirectory,
            String javaLibraryPath) {
    }
}
