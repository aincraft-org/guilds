package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.multiblock.Rotation;
import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultRepository;
import org.bukkit.inventory.ItemStack;

/**
 * JDBC-based implementation of VaultRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcVaultRepository implements VaultRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcVaultRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void save(Vault vault) {
        Objects.requireNonNull(vault, "Vault cannot be null");

        String sql = getUpsertSql();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vault.getId());
            ps.setString(2, vault.getGuildId());
            ps.setString(3, vault.getWorld());
            ps.setInt(4, vault.getOriginX());
            ps.setInt(5, vault.getOriginY());
            ps.setInt(6, vault.getOriginZ());
            ps.setString(7, vault.getRotation().name());
            ps.setString(8, vault.getCreatedBy().toString());
            ps.setLong(9, vault.getCreatedAt());
            ps.setString(10, serializeContents(vault.getContents()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save vault", e);
        }
    }

    private String getUpsertSql() {
        return switch (dbType) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                world = VALUES(world), origin_x = VALUES(origin_x), origin_y = VALUES(origin_y),
                origin_z = VALUES(origin_z), rotation = VALUES(rotation), storage_data = VALUES(storage_data)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                world = EXCLUDED.world, origin_x = EXCLUDED.origin_x, origin_y = EXCLUDED.origin_y,
                origin_z = EXCLUDED.origin_z, rotation = EXCLUDED.rotation, storage_data = EXCLUDED.storage_data
                """;
            case H2 -> """
                MERGE INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    @Override
    public void delete(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_vaults WHERE id = ?")) {
            ps.setString(1, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vault", e);
        }
    }

    @Override
    public Optional<Vault> findById(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_vaults WHERE id = ?")) {
            ps.setString(1, vaultId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find vault by ID", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Vault> findByGuildId(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_vaults WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find vault by guild ID", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Vault> findByLocation(String world, int x, int y, int z) {
        Objects.requireNonNull(world, "World cannot be null");

        String sql = "SELECT * FROM guild_vaults WHERE world = ? AND origin_x = ? AND origin_y = ? AND origin_z = ?";

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find vault by location", e);
        }

        return Optional.empty();
    }

    @Override
    public void updateContents(String vaultId, ItemStack[] contents) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE guild_vaults SET storage_data = ? WHERE id = ?")) {
            ps.setString(1, serializeContents(contents));
            ps.setString(2, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update vault contents", e);
        }
    }

    @Override
    public boolean existsForGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM guild_vaults WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check vault existence", e);
        }

        return false;
    }

    @Override
    public ItemStack[] getFreshContents(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT storage_data FROM guild_vaults WHERE id = ?")) {
            ps.setString(1, vaultId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return deserializeContents(rs.getString("storage_data"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get fresh vault contents", e);
        }

        return null;
    }

    private Vault mapResultSet(ResultSet rs) throws SQLException {
        return new Vault(
            rs.getString("id"),
            rs.getString("guild_id"),
            rs.getString("world"),
            rs.getInt("origin_x"),
            rs.getInt("origin_y"),
            rs.getInt("origin_z"),
            Rotation.valueOf(rs.getString("rotation")),
            UUID.fromString(rs.getString("created_by")),
            rs.getLong("created_at"),
            deserializeContents(rs.getString("storage_data"))
        );
    }

    private String serializeContents(ItemStack[] contents) {
        if (contents == null) {
            return null;
        }
        byte[] bytes = ItemStack.serializeItemsAsBytes(contents);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private ItemStack[] deserializeContents(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[Vault.STORAGE_SIZE];
        }
        byte[] bytes = Base64.getDecoder().decode(data);
        return ItemStack.deserializeItemsFromBytes(bytes);
    }
}
