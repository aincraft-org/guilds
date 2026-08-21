package org.aincraft.guilds.territory.persist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlSupportTest {
    @Test
    void mysqlUpsertUsesDuplicateKeyAndAlias() {
        String sql = SqlSupport.upsertSql(true,
                "INSERT INTO guilds (id, name) VALUES (?, ?)",
                "id",
                "name = EXCLUDED.name");
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.contains("name = VALUES(name)"));
        assertFalse(sql.contains("ON CONFLICT"));
    }

    @Test
    void mysqlDoNothingUsesInsertIgnore() {
        String sql = SqlSupport.upsertSql(true,
                "INSERT INTO guild_level_benefits (id) VALUES (?)",
                "id",
                null);
        assertTrue(sql.startsWith("INSERT IGNORE INTO"));
        assertFalse(sql.contains("ON CONFLICT"));
    }

    @Test
    void postgresUpsertKeepsOnConflict() {
        String sql = SqlSupport.upsertSql(false,
                "INSERT INTO guilds (id, name) VALUES (?, ?)",
                "id",
                "name = EXCLUDED.name");
        assertEquals(
                "INSERT INTO guilds (id, name) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name",
                sql);
    }

    @Test
    void mysqlStringAggUsesGroupConcat() {
        assertEquals("GROUP_CONCAT(DISTINCT nm.guild_id SEPARATOR ',')",
                SqlSupport.stringAggDistinct(true, "nm.guild_id", ","));
    }

    @Test
    void postgresStringAggKeepsStringAgg() {
        assertEquals("STRING_AGG(DISTINCT nm.guild_id, ',')",
                SqlSupport.stringAggDistinct(false, "nm.guild_id", ","));
    }

    @Test
    void mysqlIdTypeRewritesKeyedTextColumns() {
        String mysql = SqlSupport.withIdType(true,
                "CREATE TABLE residents (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, notes TEXT, status TEXT NOT NULL DEFAULT 'OPEN')");
        assertTrue(mysql.contains("uuid VARCHAR(255) PRIMARY KEY"));
        assertTrue(mysql.contains("name VARCHAR(255) NOT NULL"));
        assertTrue(mysql.contains("notes VARCHAR(255)"));
        assertTrue(mysql.contains("status VARCHAR(255) NOT NULL"));
        assertFalse(mysql.contains("DEFAULT 'OPEN'"));
    }

    @Test
    void mysqlIdTypeKeepsJsonTextAndRewritesBytea() {
        String mysql = SqlSupport.withIdType(true,
                "CREATE TABLE guild_levels (resource_costs_json TEXT NOT NULL DEFAULT '{}', schematic_data BYTEA, content TEXT)");
        assertTrue(mysql.contains("resource_costs_json TEXT NOT NULL"));
        assertTrue(mysql.contains("schematic_data LONGBLOB"));
        assertTrue(mysql.contains("content TEXT"));
        assertFalse(mysql.contains("DEFAULT '{}'"));
    }

    @Test
    void mysqlIdTypeRewritesItemPayloadToLongtext() {
        String mysql = SqlSupport.withIdType(true,
                "CREATE TABLE guild_storage_slots (item_payload TEXT NOT NULL, item_schema TEXT NOT NULL)");
        assertTrue(mysql.contains("item_payload LONGTEXT NOT NULL"));
        assertTrue(mysql.contains("item_schema VARCHAR(255) NOT NULL"));
    }

    @Test
    void mysqlIdTypeRewritesResultItemPayloadToLongtext() {
        String mysql = SqlSupport.withIdType(true,
                "CREATE TABLE guild_storage_operations (result_item_payload TEXT, result_item_schema TEXT NOT NULL)");
        assertTrue(mysql.contains("result_item_payload LONGTEXT"));
        assertTrue(mysql.contains("result_item_schema VARCHAR(255) NOT NULL"));
    }

    @Test
    void mysqlIdTypeRewritesRequestItemPayloadToLongtext() {
        String mysql = SqlSupport.withIdType(true,
                "CREATE TABLE guild_storage_operations (request_item_payload TEXT, request_item_schema TEXT NOT NULL)");
        assertTrue(mysql.contains("request_item_payload LONGTEXT"));
        assertTrue(mysql.contains("request_item_schema VARCHAR(255) NOT NULL"));
    }

    @Test
    void mysqlStringColumnTypeUsesLongtextForRequestPayload() {
        assertEquals("LONGTEXT", SqlSupport.stringColumnType(true, "request_item_payload"));
        assertEquals("VARCHAR(255)", SqlSupport.stringColumnType(true, "request_item_schema"));
    }

    @Test
    void postgresDoNothingKeepsOnConflict() {
        String sql = SqlSupport.upsertSql(false,
                "INSERT INTO guild_level_benefits (id) VALUES (?)",
                "id",
                "");
        assertTrue(sql.contains("ON CONFLICT (id) DO NOTHING"));
    }

    @Test
    void postgresInstantTextAtOrAfterUsesTimestamptzCast() {
        assertEquals(
                "(recorded_at::timestamptz >= ?::timestamptz)",
                SqlSupport.instantTextAtOrAfter(false, "recorded_at"));
    }

    @Test
    void mysqlInstantTextAtOrAfterUsesStrToDateComparison() {
        String predicate = SqlSupport.instantTextAtOrAfter(true, "recorded_at");
        assertTrue(predicate.contains("STR_TO_DATE"));
        assertTrue(predicate.contains("recorded_at"));
        assertTrue(predicate.contains("?"));
    }
}
