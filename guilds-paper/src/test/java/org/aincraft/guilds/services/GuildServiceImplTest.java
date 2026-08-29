package org.aincraft.guilds.services;

import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Location;
import org.aincraft.guilds.services.impl.GuildServiceImpl;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GuildServiceImplTest {
    @Test
    void getGuildSpawnPreservesPersistedLocationForCrystalReconciliation() throws Exception {
        DatabaseManager database = mock(DatabaseManager.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getDouble("spawn_x")).thenReturn(10.5);
        when(result.getDouble("spawn_y")).thenReturn(64.0);
        when(result.getDouble("spawn_z")).thenReturn(-4.5);
        when(result.getFloat("spawn_yaw")).thenReturn(90.0f);
        when(result.getFloat("spawn_pitch")).thenReturn(15.0f);
        when(result.wasNull()).thenReturn(false);
        when(result.getString("spawn_world")).thenReturn("world");

        GuildServiceImpl service = new GuildServiceImpl(
                database, Logger.getLogger("guild-spawn-test"), mock(ResidentService.class));

        Optional<Location> spawn = service.getGuildSpawn("Builders");

        assertTrue(spawn.isPresent());
        assertEquals("world", spawn.get().getWorld());
        assertEquals(10.5, spawn.get().getX());
        assertEquals(64.0, spawn.get().getY());
        assertEquals(-4.5, spawn.get().getZ());
    }
}
