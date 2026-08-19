package org.aincraft.guilds.database.migration;

import org.aincraft.guilds.territory.persist.SqlStatements;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlResourceMigrationTest {
    @Test
    void schemaInitializerLoadsVersionQueriesFromResources() {
        String count = SqlStatements.load("migrations/select-schema-version.sql");
        assertTrue(count.toUpperCase(Locale.ROOT).contains("SELECT COUNT(*)"));
        assertTrue(count.contains("schema_migrations"));
        String applied = SqlStatements.load("migrations/select-applied-schema-migrations.sql");
        assertTrue(applied.toUpperCase(Locale.ROOT).contains("SELECT"));
        assertTrue(applied.contains("checksum"));
    }

    @Test
    void schemaInitializerSourceDoesNotEmbedSql() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/org/aincraft/guilds/database/migration/SchemaInitializer.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("SELECT COUNT(*)"), source);
        assertFalse(source.contains("CREATE TABLE"), source);
        assertTrue(source.contains("SqlStatements.load"));
    }

    @Test
    void leftoverJavaMigrationsDoNotEmbedDdl() throws Exception {
        Path dir = Path.of("src/main/java/org/aincraft/guilds/database/migration");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("AddGuildRenameMigration.java")
                        || name.equals("AddAllianceRenameMigration.java")
                        || name.equals("AlterResidentLastOnlineMigration.java")
                        || name.equals("PermissionMigration.java")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(source.contains("CREATE TABLE"), name + " still embeds CREATE TABLE");
                assertFalse(source.contains("ALTER TABLE"), name + " still embeds ALTER TABLE");
            }
        }
    }
}
