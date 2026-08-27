package dev.mintychochip.sql;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NamedSqlTest {

    @Test
    void loadsExtractedEnrollmentSqlAndRepeatsNamedParametersInAppearanceOrder() throws Exception {
        NamedSql sql = NamedSql.load(NamedSql.class, "dev/mintychochip/guilds/sql");
        ParsedSql parsed = sql.sql("bank/open-enrollment.sql");

        assertEquals("""
                INSERT INTO guild_bank_enrollments (guild_id, player_uuid, active, enrolled_at, updated_at)
                VALUES (?, ?, TRUE, ?, ?)
                ON CONFLICT (guild_id, player_uuid) DO UPDATE SET active = TRUE, updated_at = EXCLUDED.updated_at
                WHERE EXISTS (SELECT 1 FROM guild_residents WHERE guild_id = ? AND resident_uuid = ?)""",
                parsed.jdbcSql(Map.of()));
        assertEquals(
                List.of("guild_id", "player_uuid", "now", "now", "guild_id", "player_uuid"),
                parsed.parameterNames(Map.of()));
        assertFalse(parsed.jdbcSql(Map.of()).contains(":guild_id"));
        assertFalse(parsed.jdbcSql(Map.of()).contains(":now"));

        PreparedStatement statement = mock(PreparedStatement.class);
        parsed.bind(statement, Map.of(
                "guild_id", "guild-1",
                "player_uuid", "player-1",
                "now", "2026-08-17T00:00:00"));

        verify(statement).setObject(1, "guild-1");
        verify(statement).setObject(2, "player-1");
        verify(statement).setObject(3, "2026-08-17T00:00:00");
        verify(statement).setObject(4, "2026-08-17T00:00:00");
        verify(statement).setObject(5, "guild-1");
        verify(statement).setObject(6, "player-1");
    }

    @Test
    void leavesPostgresCastsAloneAndExpandsInListPlaceholders() throws Exception {
        NamedSql sql = NamedSql.load(NamedSql.class, "dev/mintychochip/sql");
        ParsedSql parsed = sql.sql("cast-and-in-list.sql");
        Map<String, Object> params = Map.of(
                "owner_id", "owner-1",
                "tags", List.of("a", "b", "c"));

        String jdbc = parsed.jdbcSql(params);
        assertTrue(jdbc.contains("doc::jsonb"), jdbc);
        assertTrue(jdbc.contains("tag IN (?, ?, ?)"), jdbc);
        assertTrue(jdbc.contains("label = ?"), jdbc);
        assertEquals(List.of("owner_id", "tags", "tags", "tags", "owner_id"), parsed.parameterNames(params));

        PreparedStatement statement = mock(PreparedStatement.class);
        parsed.bind(statement, params);
        verify(statement).setObject(1, "owner-1");
        verify(statement).setObject(2, "a");
        verify(statement).setObject(3, "b");
        verify(statement).setObject(4, "c");
        verify(statement).setObject(5, "owner-1");
    }
}
