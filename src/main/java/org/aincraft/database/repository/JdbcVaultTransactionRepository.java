package org.aincraft.database.repository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.aincraft.database.ConnectionProvider;
import org.aincraft.database.DatabaseType;
import org.aincraft.vault.VaultTransaction;
import org.aincraft.vault.VaultTransactionRepository;
import org.bukkit.Material;

/**
 * JDBC-based implementation of VaultTransactionRepository.
 * Works with all supported database types.
 */
@Singleton
public class JdbcVaultTransactionRepository implements VaultTransactionRepository {
    private final ConnectionProvider connectionProvider;
    private final DatabaseType dbType;

    @Inject
    public JdbcVaultTransactionRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        this.dbType = connectionProvider.getDatabaseType();
    }

    @Override
    public void log(VaultTransaction transaction) {
        Objects.requireNonNull(transaction, "Transaction cannot be null");

        String sql = """
            INSERT INTO vault_transactions
            (vault_id, player_id, action, item_type, item_amount, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, transaction.vaultId());
            ps.setString(2, transaction.playerId().toString());
            ps.setString(3, transaction.action().name());
            ps.setString(4, transaction.itemType().name());
            ps.setInt(5, transaction.amount());
            ps.setLong(6, transaction.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log vault transaction", e);
        }
    }

    @Override
    public List<VaultTransaction> findByVaultId(String vaultId, int limit) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        String sql = """
            SELECT * FROM vault_transactions
            WHERE vault_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<VaultTransaction> transactions = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vaultId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                transactions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transactions by vault ID", e);
        }

        return transactions;
    }

    @Override
    public List<VaultTransaction> findByPlayer(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "Player ID cannot be null");

        String sql = """
            SELECT * FROM vault_transactions
            WHERE player_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;

        List<VaultTransaction> transactions = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                transactions.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transactions by player", e);
        }

        return transactions;
    }

    @Override
    public void deleteByVaultId(String vaultId) {
        Objects.requireNonNull(vaultId, "Vault ID cannot be null");

        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM vault_transactions WHERE vault_id = ?")) {
            ps.setString(1, vaultId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete vault transactions", e);
        }
    }

    private VaultTransaction mapResultSet(ResultSet rs) throws SQLException {
        return new VaultTransaction(
            rs.getLong("id"),
            rs.getString("vault_id"),
            UUID.fromString(rs.getString("player_id")),
            VaultTransaction.TransactionType.valueOf(rs.getString("action")),
            Material.valueOf(rs.getString("item_type")),
            rs.getInt("item_amount"),
            rs.getLong("timestamp")
        );
    }
}
