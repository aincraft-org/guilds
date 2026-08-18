package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.services.impl.GuildServiceImpl;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for guild service impl load. */
class GuildServiceImplLoadTest {

    /**
     * Returns the guild selects and maps level points and active project.
     * @throws Exception if an error occurs
     */
    @Test
    void getGuild_selectsAndMapsLevelPointsAndActiveProject() throws Exception {
        DatabaseManager database = mock(DatabaseManager.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        List<String> sql = new ArrayList<>();
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            sql.add(invocation.getArgument(0));
            return statement;
        });
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("id")).thenReturn("guild-1");
        when(result.getString("name")).thenReturn("Loaded Guild");
        when(result.getString("mayor_uuid")).thenReturn(UUID.randomUUID().toString());
        when(result.getDouble("balance")).thenReturn(0.0);
        when(result.getBoolean("is_open")).thenReturn(true);
        when(result.getString("created_at")).thenReturn(null);
        when(result.getInt("home_block_x")).thenReturn(0);
        when(result.wasNull()).thenReturn(false);
        when(result.getInt("home_block_z")).thenReturn(0);
        when(result.getString("home_block_world")).thenReturn(null);
        when(result.getDouble("spawn_x")).thenReturn(0.0);
        when(result.getDouble("spawn_y")).thenReturn(0.0);
        when(result.getDouble("spawn_z")).thenReturn(0.0);
        when(result.getFloat("spawn_yaw")).thenReturn(0.0f);
        when(result.getFloat("spawn_pitch")).thenReturn(0.0f);
        when(result.getString("spawn_world")).thenReturn(null);
        when(result.getBoolean("pvp_enabled")).thenReturn(true);
        when(result.getBoolean("fire_enabled")).thenReturn(true);
        when(result.getBoolean("explosions_enabled")).thenReturn(true);
        when(result.getBoolean("mobs_enabled")).thenReturn(true);
        when(result.getBoolean("public_enabled")).thenReturn(false);
        when(result.getInt("guild_level")).thenReturn(3);
        when(result.getInt("tech_points")).thenReturn(2);
        when(result.getString("active_project_id")).thenReturn("better_storage");

        GuildServiceImpl service = new GuildServiceImpl(
                database, Logger.getLogger("guild-load-test"), mock(ResidentService.class));
        Optional<Guild> loaded = service.getGuild("Loaded Guild");

        assertTrue(loaded.isPresent());
        assertEquals(3, loaded.get().getGuildLevel());
        assertEquals(2, loaded.get().getTechPoints());
        assertEquals("better_storage", loaded.get().getActiveProjectId());
        assertTrue(sql.getFirst().contains("guild_level"), sql.getFirst());
        assertTrue(sql.getFirst().contains("tech_points"), sql.getFirst());
        assertTrue(sql.getFirst().contains("active_project_id"), sql.getFirst());
    }

    /**
     * Performs the every guild mapping select includes progress columns operation.
     * @throws Exception if an error occurs
     */
    @Test
    void everyGuildMappingSelectIncludesProgressColumns() throws Exception {
        java.nio.file.Path sqlDir = java.nio.file.Path.of(
                "src/main/resources/dev/mintychochip/guilds/sql/guilds");
        int selects = 0;
        try (var files = java.nio.file.Files.list(sqlDir)) {
            for (java.nio.file.Path file : files.toList()) {
                if (!file.getFileName().toString().endsWith(".sql")) {
                    continue;
                }
                String source = java.nio.file.Files.readString(file);
                int from = 0;
                while (true) {
                    int start = source.indexOf("SELECT id, name, mayor_uuid", from);
                    if (start < 0) {
                        start = source.indexOf("SELECT t.id, t.name, t.mayor_uuid", from);
                    }
                    if (start < 0) {
                        break;
                    }
                    int end = source.indexOf("FROM guilds", start);
                    if (end < 0) {
                        break;
                    }
                    String select = source.substring(start, end);
                    assertTrue(select.contains("guild_level"), select);
                    assertTrue(select.contains("tech_points"), select);
                    assertTrue(select.contains("active_project_id"), select);
                    selects++;
                    from = end + 1;
                }
            }
        }
        assertTrue(selects >= 8, "expected every guild mapping query, found " + selects);
    }
}
