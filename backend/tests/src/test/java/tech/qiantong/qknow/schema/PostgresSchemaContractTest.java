package tech.qiantong.qknow.schema;

import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbChatMessageDO;
import tech.qiantong.qknow.module.kb.dal.dataobject.conversation.KbConversationDO;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresSchemaContractTest {

    private static final Path REPO_ROOT = findRepoRoot();
    private static final Path SCHEMA = REPO_ROOT.resolve("deploy/sql/postgresql/01-schema.sql");
    private static final Path MIGRATION = REPO_ROOT.resolve(
            "deploy/sql/postgresql/migrations/V015__runtime_contract_fixes.sql");

    @Test
    void chatFlagsMatchJavaIntegerContracts() throws Exception {
        String schema = Files.readString(SCHEMA);

        assertFlagColumns(tableDefinition(schema, "conversation"), "conversation");
        assertFlagColumns(tableDefinition(schema, "chat_message"), "chat_message");
        assertFlagFields(KbConversationDO.class);
        assertFlagFields(KbChatMessageDO.class);
    }

    @Test
    void migrationRepairsRuntimeContractsIdempotently() throws IOException {
        String migration = Files.readString(MIGRATION).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        for (String required : List.of(
                "('conversation', 'ck_conversation_valid_flag_01', 'ck_conversation_del_flag_01')",
                "('chat_message', 'ck_chat_message_valid_flag_01', 'ck_chat_message_del_flag_01')",
                "type_clauses := array[]::text[]",
                "if valid_type = 'boolean' then",
                "if del_type = 'boolean' then",
                "alter column valid_flag type smallint using case when valid_flag is null then null when valid_flag then 1 else 0 end",
                "alter column del_flag type smallint using case when del_flag is null then null when del_flag then 1 else 0 end",
                "if cardinality(type_clauses) > 0 then",
                "array_to_string(type_clauses, ', ')",
                "update %i.%i set valid_flag = coalesce(valid_flag, 1), del_flag = coalesce(del_flag, 0)",
                "alter column valid_flag set default 1",
                "alter column valid_flag set not null",
                "alter column del_flag set default 0",
                "alter column del_flag set not null",
                "if not exists (",
                "from pg_constraint",
                "conrelid =",
                "add constraint %i check (%i in (0, 1)) not valid",
                "validate constraint %i",
                "table_name = 'dag_checkpoints'",
                "data_type = 'bigint'",
                "type timestamp using to_timestamp",
                "execute format('alter table if exists %i.kmc_document add column if not exists sync_version bigint not null default 0', current_schema())")) {
            assertTrue(migration.contains(required), () -> "migration must contain " + required);
        }
        assertEquals(1, migration.split(Pattern.quote("array_to_string(type_clauses, ', ')"), -1).length - 1);
        assertFalse(migration.contains("alter table if exists kmc_document"));
    }

    @Test
    void migrationRunsTwiceAgainstIsolatedLegacySchema() throws Exception {
        String jdbcUrl = System.getenv("QKNOW_SCHEMA_TEST_JDBC_URL");
        assumeTrue(jdbcUrl != null && !jdbcUrl.isBlank(), "QKNOW_SCHEMA_TEST_JDBC_URL is not configured");
        assertFalse(jdbcUrl.toLowerCase(Locale.ROOT).contains("/ai_agent"),
                "schema migration test refuses the application database");

        String schema = "qknow_schema_test_" + UUID.randomUUID().toString().replace("-", "");
        String migration = Files.readString(MIGRATION);
        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET LOCAL search_path TO " + schema);
                    statement.execute("SET LOCAL TIME ZONE 'UTC'");
                    statement.execute("""
                            CREATE TABLE conversation (
                                id BIGINT PRIMARY KEY,
                                valid_flag BOOLEAN DEFAULT TRUE,
                                del_flag BOOLEAN DEFAULT FALSE
                            )
                            """);
                    statement.execute("""
                            CREATE TABLE chat_message (
                                id BIGINT PRIMARY KEY,
                                valid_flag SMALLINT DEFAULT 1,
                                del_flag BOOLEAN DEFAULT FALSE
                            )
                            """);
                    statement.execute("""
                            CREATE TABLE dag_checkpoints (
                                runtime_id VARCHAR(255) PRIMARY KEY,
                                flow_id VARCHAR(255) NOT NULL,
                                group_index INT NOT NULL,
                                completed_results TEXT,
                                created_at BIGINT NOT NULL,
                                updated_at BIGINT
                            )
                            """);
                    statement.execute("CREATE TABLE kmc_document (id BIGINT PRIMARY KEY)");
                    statement.execute("""
                            INSERT INTO conversation VALUES
                                (1, TRUE, FALSE), (2, FALSE, TRUE), (3, NULL, NULL)
                            """);
                    statement.execute("""
                            INSERT INTO chat_message VALUES
                                (1, 1, FALSE), (2, 0, TRUE), (3, NULL, NULL)
                            """);
                    statement.execute("""
                            INSERT INTO dag_checkpoints VALUES
                                ('runtime-1', 'flow-1', 0, NULL, 1700000000000, 1700000005000)
                            """);
                    statement.execute("INSERT INTO kmc_document (id) VALUES (1)");

                    statement.execute(migration);
                    statement.execute(migration);
                    statement.execute("INSERT INTO conversation (id) VALUES (4)");
                    statement.execute("INSERT INTO chat_message (id) VALUES (4)");
                }

                for (String table : List.of("conversation", "chat_message")) {
                    assertColumnContract(connection, schema, table, "valid_flag", "smallint", false, "1");
                    assertColumnContract(connection, schema, table, "del_flag", "smallint", false, "0");
                    assertCheckConstraint(connection, schema, table, "ck_" + table + "_valid_flag_01", "valid_flag");
                    assertCheckConstraint(connection, schema, table, "ck_" + table + "_del_flag_01", "del_flag");
                    assertFlagRows(connection, schema, table);
                }
                assertColumnContract(connection, schema, "dag_checkpoints", "created_at",
                        "timestamp without time zone", false, "current_timestamp");
                assertColumnContract(connection, schema, "dag_checkpoints", "updated_at",
                        "timestamp without time zone", true, null);
                assertColumnContract(connection, schema, "kmc_document", "sync_version", "bigint", false, "0");
                assertDagTimestamps(connection, schema);
                assertSyncVersion(connection, schema);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void migrationAvoidsLargeDataRewrites() throws IOException {
        String migration = Files.readString(MIGRATION).toLowerCase(Locale.ROOT);

        for (String forbidden : List.of(
                "kmc_document_" + "segment",
                "vector_" + "store",
                "embed" + "ding",
                "trun" + "cate",
                "re" + "index")) {
            assertFalse(migration.contains(forbidden), () -> "migration must not contain " + forbidden);
        }
    }

    private static String tableDefinition(String schema, String table) {
        Matcher matcher = Pattern.compile(
                        "CREATE TABLE IF NOT EXISTS " + table + " \\((.*?)\\);",
                        Pattern.DOTALL)
                .matcher(schema);
        assertTrue(matcher.find(), () -> "missing table " + table);
        return matcher.group(1);
    }

    private static void assertFlagColumns(String tableDefinition, String table) {
        assertTrue(tableDefinition.matches("(?s).*valid_flag\\s+SMALLINT\\s+NOT NULL\\s+DEFAULT 1.*"));
        assertTrue(tableDefinition.matches("(?s).*del_flag\\s+SMALLINT\\s+NOT NULL\\s+DEFAULT 0.*"));
        for (String flag : List.of("valid_flag", "del_flag")) {
            String constraint = "ck_" + table + "_" + flag + "_01";
            assertTrue(tableDefinition.matches(
                    "(?s).*CONSTRAINT\\s+" + constraint
                            + "\\s+CHECK\\s*\\(\\s*" + flag + "\\s+IN\\s*\\(\\s*0\\s*,\\s*1\\s*\\)\\s*\\).*"),
                    () -> "missing " + constraint);
        }
    }

    private static void assertFlagFields(Class<?> model) throws NoSuchFieldException {
        Field validFlag = model.getDeclaredField("validFlag");
        Field delFlag = model.getDeclaredField("delFlag");
        assertEquals(Integer.class, validFlag.getType());
        assertEquals(Integer.class, delFlag.getType());
        assertNotNull(delFlag.getAnnotation(TableLogic.class));
    }

    private static void assertColumnContract(Connection connection, String schema, String table, String column,
                                             String dataType, boolean nullable, String defaultFragment)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), () -> "missing column " + table + "." + column);
                assertEquals(dataType, result.getString("data_type"));
                assertEquals(nullable ? "YES" : "NO", result.getString("is_nullable"));
                String columnDefault = result.getString("column_default");
                if (defaultFragment == null) {
                    assertNull(columnDefault);
                } else {
                    assertNotNull(columnDefault);
                    assertTrue(columnDefault.toLowerCase(Locale.ROOT).contains(defaultFragment));
                }
                assertFalse(result.next());
            }
        }
    }

    private static void assertCheckConstraint(Connection connection, String schema, String table,
                                              String constraint, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT constraint_record.convalidated, pg_get_constraintdef(constraint_record.oid)
                FROM pg_constraint constraint_record
                JOIN pg_class table_record ON table_record.oid = constraint_record.conrelid
                JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
                WHERE schema_record.nspname = ? AND table_record.relname = ?
                  AND constraint_record.conname = ? AND constraint_record.contype = 'c'
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, constraint);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), () -> "missing constraint " + constraint);
                assertTrue(result.getBoolean(1), () -> "constraint is not validated: " + constraint);
                String definition = result.getString(2).toLowerCase(Locale.ROOT);
                assertTrue(definition.contains(column));
                assertTrue(definition.contains("0"));
                assertTrue(definition.contains("1"));
                assertFalse(result.next());
            }
        }
    }

    private static void assertFlagRows(Connection connection, String schema, String table) throws SQLException {
        int[][] expected = {{1, 0}, {0, 1}, {1, 0}, {1, 0}};
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT valid_flag, del_flag FROM " + schema + "." + table + " ORDER BY id")) {
            for (int[] flags : expected) {
                assertTrue(result.next());
                assertEquals(flags[0], result.getInt("valid_flag"));
                assertEquals(flags[1], result.getInt("del_flag"));
            }
            assertFalse(result.next());
        }
    }

    private static void assertDagTimestamps(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT created_at, updated_at FROM " + schema + ".dag_checkpoints")) {
            assertTrue(result.next());
            assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1700000000000L), ZoneOffset.UTC),
                    result.getObject("created_at", LocalDateTime.class));
            assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1700000005000L), ZoneOffset.UTC),
                    result.getObject("updated_at", LocalDateTime.class));
            assertFalse(result.next());
        }
    }

    private static void assertSyncVersion(Connection connection, String schema) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT sync_version FROM " + schema + ".kmc_document WHERE id = 1")) {
            assertTrue(result.next());
            assertEquals(0L, result.getLong(1));
            assertFalse(result.next());
        }
    }

    private static Path findRepoRoot() {
        for (Path path = Paths.get("").toAbsolutePath(); path != null; path = path.getParent()) {
            if (Files.exists(path.resolve("deploy/sql/postgresql/01-schema.sql"))) {
                return path;
            }
        }
        throw new IllegalStateException("repository root not found");
    }
}
