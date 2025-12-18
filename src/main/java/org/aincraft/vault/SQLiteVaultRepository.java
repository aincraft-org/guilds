package org.aincraft.vault;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.aincraft.multiblock.Rotation;
import org.bukkit.inventory.ItemStack;

/**
 * SQLite-based implementation of VaultRepository.
 */
public class SQLiteVaultRepository implements VaultRepository {
    private final String connectionString;

    @Inject
    public SQLiteVaultRepository(@Named("databasePath") String dbPath) {
        this.connectionString = "jdbc:sqlite:" + dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS guild_vaults (
                id TEXT PRIMARY KEY,
                guild_id TEXT NOT NULL UNIQUE,
                world TEXT NOT NULL,
                origin_x INTEGER NOT NULL,
                origin_y INTEGER NOT NULL,
                origin_z INTEGER NOT NULL,
                rotation TEXT NOT NULL,
                created_by TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                storage_data TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_vault_guild ON guild_vaults(guild_id);
            CREATE INDEX IF NOT EXISTS idx_vault_location ON guild_vaults(world, origin_x, origin_y, origin_z);
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             Statement stmt = conn.createStatement()) {
            for (String sql : createTableSQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize guild_vaults table", e);
        }
    }

    @Override
    public void save(Vault vault) {
        Objects.requireNonNull(vault, "Vault cannot be null");

        String upsertSQL = """
            INSERT OR REPLACE INTO guild_vaults
            (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(upsertSQL)) {
            pstmt.setString(1, vault.getId());
            pstmt.setString(2, vault.getGuildId());
            pstmt.setString(3, vault.getWorld());
            pstmt.setInt(4, vault.getOriginX());
            pstmt.setInt(5, vault.getOriginY());
            pstmt.setInt(6, vault.getOriginZ());
            pstmt.setString(7, vault.getRotation().name());
            pstmt.setString(8, vault.getCreatedBy().toString());
            pstmt.setLong(9, vault.getCreatedAt());
            pstmt.setString(10, serializeContents(vault.getContents()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save vault", e);
        }
    }

    @Override
    public void delete(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        String deleteSQL = "DELETE FROM guild_vaults WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(deleteSQL)) {
            pstmt.setString(1, vaultId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vault", e);
        }
    }

    @Override
    public Optional<Vault> findById(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        String selectSQL = "SELECT * FROM guild_vaults WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, vaultId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guild_vaults WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

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

        String selectSQL = "SELECT * FROM guild_vaults WHERE world = ? AND origin_x = ? AND origin_y = ? AND origin_z = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, world);
            pstmt.setInt(2, x);
            pstmt.setInt(3, y);
            pstmt.setInt(4, z);
            ResultSet rs = pstmt.executeQuery();

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

        String updateSQL = "UPDATE guild_vaults SET storage_data = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
            pstmt.setString(1, serializeContents(contents));
            pstmt.setString(2, vaultId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update vault contents", e);
        }
    }

    @Override
    public boolean existsForGuild(String guildId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");

        String selectSQL = "SELECT COUNT(*) FROM guild_vaults WHERE guild_id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check vault existence", e);
        }

        return false;
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

    @Override
    public ItemStack[] getFreshContents(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        String selectSQL = "SELECT storage_data FROM guild_vaults WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(connectionString);
             PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
            pstmt.setString(1, vaultId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return deserializeContents(rs.getString("storage_data"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get fresh vault contents", e);
        }

        return null;
    }
}
