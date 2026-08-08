package org.aincraft.guilds.services.impl;

import org.aincraft.guilds.database.DatabaseManager;
import org.aincraft.guilds.models.Guild;
import org.aincraft.guilds.models.GuildContract;
import org.aincraft.guilds.services.GuildContractService;
import org.aincraft.guilds.services.GuildService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of {@link GuildContractService}.
 *
 * <p>Escrow semantics: creating a contract debits the payment from the contracting guild's
 * balance and holds it in the contract row. Fulfillment releases the escrow to the
 * fulfilling guild and credits the material amount to the contracting guild's
 * {@code upgrade_progress}. Cancellation refunds the escrow to the contracting guild.
 * Each state transition runs inside a single database transaction.
 */
public class GuildContractServiceImpl implements GuildContractService {

    private static final Logger LOGGER = Logger.getLogger(GuildContractServiceImpl.class.getName());

    private final DatabaseManager databaseManager;
    private final GuildService guildService;

    public GuildContractServiceImpl(DatabaseManager databaseManager, GuildService guildService) {
        this.databaseManager = databaseManager;
        this.guildService = guildService;
    }

    @Override
    public ContractResult createContract(String contractingGuildId, String resourceType, int amount, double payment) {
        if (contractingGuildId == null || contractingGuildId.isBlank()) {
            return new ContractResult(false, ContractStatus.GUILD_NOT_FOUND, "Contracting guild not specified", null);
        }
        if (resourceType == null || resourceType.isBlank()) {
            return new ContractResult(false, ContractStatus.INVALID_AMOUNT, "Resource type not specified", null);
        }
        if (amount <= 0) {
            return new ContractResult(false, ContractStatus.INVALID_AMOUNT, "Amount must be positive", null);
        }
        if (payment <= 0) {
            return new ContractResult(false, ContractStatus.INVALID_PAYMENT, "Payment must be positive", null);
        }

        Optional<Guild> contractingOpt = guildService.getGuildById(contractingGuildId);
        if (contractingOpt.isEmpty()) {
            return new ContractResult(false, ContractStatus.GUILD_NOT_FOUND, "Contracting guild not found", null);
        }

        Guild contracting = contractingOpt.get();
        if (contracting.getBalance() < payment) {
            return new ContractResult(false, ContractStatus.INSUFFICIENT_FUNDS,
                    "Contracting guild cannot afford the payment", null);
        }

        String contractId = UUID.randomUUID().toString();
        String normalizedResource = resourceType.toUpperCase(Locale.ROOT);
        String now = LocalDateTime.now().toString();

        final double[] newBalance = {0.0};
        boolean committed = databaseManager.executeTransactionWithResult(connection -> {
            // Debit the payment from the contracting guild's balance (escrow).
            newBalance[0] = updateBalance(connection, contractingGuildId, -payment);
            insertContract(connection, contractId, contractingGuildId, normalizedResource, amount, payment, now);
            return true;
        }).orElse(false);

        if (!committed) {
            return new ContractResult(false, ContractStatus.INSUFFICIENT_FUNDS,
                    "Failed to create contract", null);
        }

        GuildContract contract = new GuildContract(contractId, contractingGuildId, normalizedResource, amount, payment);
        contract.setCreatedAt(LocalDateTime.parse(now));
        return new ContractResult(true, ContractStatus.CREATED,
                "Contract created", contract);
    }

    @Override
    public List<GuildContract> getOpenContracts() {
        return queryContracts("SELECT * FROM guild_contracts WHERE status = 'OPEN' ORDER BY created_at");
    }

    @Override
    public List<GuildContract> getContractsForGuild(String guildId) {
        return queryContracts("SELECT * FROM guild_contracts WHERE contracting_guild_id = ? ORDER BY created_at", guildId);
    }

    @Override
    public Optional<GuildContract> getContract(String contractId) {
        if (contractId == null) {
            return Optional.empty();
        }
        List<GuildContract> results = queryContracts("SELECT * FROM guild_contracts WHERE id = ?", contractId);
        return results.stream().findFirst();
    }

    @Override
    public FulfillResult fulfillContract(String contractId, String fulfillingGuildId) {
        if (contractId == null || fulfillingGuildId == null || fulfillingGuildId.isBlank()) {
            return new FulfillResult(false, FulfillStatus.NOT_FOUND, "Contract or guild not specified", 0.0);
        }

        Optional<GuildContract> contractOpt = getContract(contractId);
        if (contractOpt.isEmpty()) {
            return new FulfillResult(false, FulfillStatus.NOT_FOUND, "Contract not found", 0.0);
        }

        GuildContract contract = contractOpt.get();
        if (!contract.isOpen()) {
            return new FulfillResult(false, FulfillStatus.NOT_OPEN, "Contract is no longer open", 0.0);
        }
        if (fulfillingGuildId.equals(contract.getContractingGuildId())) {
            return new FulfillResult(false, FulfillStatus.SELF_FULFILL,
                    "A guild cannot fulfill its own contract", 0.0);
        }

        Optional<Guild> fulfillingOpt = guildService.getGuildById(fulfillingGuildId);
        if (fulfillingOpt.isEmpty()) {
            return new FulfillResult(false, FulfillStatus.NOT_FOUND, "Fulfilling guild not found", 0.0);
        }

        double payment = contract.getPayment();
        String resourceType = contract.getResourceType();
        int amount = contract.getAmount();
        String contractingGuildId = contract.getContractingGuildId();
        String fulfilledAt = LocalDateTime.now().toString();

        boolean committed = databaseManager.executeTransactionWithResult(connection -> {
            // Re-check status inside the transaction in case it changed concurrently.
            if (!isOpen(connection, contractId)) {
                throw new ContractConflictException("Contract is no longer open");
            }

            // Credit the material amount to the contracting guild's upgrade progress.
            addToUpgradeProgress(connection, contractingGuildId, resourceType, amount);

            // Release the escrowed payment to the fulfilling guild.
            updateBalance(connection, fulfillingGuildId, payment);

            // Mark the contract fulfilled.
            markFulfilled(connection, contractId, fulfillingGuildId, fulfilledAt);
            return true;
        }).orElse(false);

        if (!committed) {
            return new FulfillResult(false, FulfillStatus.NOT_OPEN,
                    "Failed to fulfill contract", 0.0);
        }

        return new FulfillResult(true, FulfillStatus.FULFILLED,
                "Contract fulfilled", payment);
    }

    @Override
    public boolean cancelContract(String contractId, String contractingGuildId) {
        if (contractId == null || contractingGuildId == null) {
            return false;
        }

        Optional<GuildContract> contractOpt = getContract(contractId);
        if (contractOpt.isEmpty()) {
            return false;
        }

        GuildContract contract = contractOpt.get();
        if (!contract.isOpen() || !contractingGuildId.equals(contract.getContractingGuildId())) {
            return false;
        }

        double payment = contract.getPayment();
        return databaseManager.executeTransactionWithResult(connection -> {
            // Re-check status inside the transaction.
            if (!isOpen(connection, contractId)) {
                throw new ContractConflictException("Contract is no longer open");
            }
            // Refund the escrowed payment to the contracting guild.
            updateBalance(connection, contractingGuildId, payment);
            markCancelled(connection, contractId);
            return true;
        }).orElse(false);
    }

    // ----- persistence helpers -----

    private void insertContract(Connection connection, String id, String contractingGuildId,
                                String resourceType, int amount, double payment, String createdAt) throws SQLException {
        String sql = """
            INSERT INTO guild_contracts (id, contracting_guild_id, resource_type, amount, payment,
                filled, status, created_at)
            VALUES (?, ?, ?, ?, ?, 0, 'OPEN', ?)
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, contractingGuildId);
            statement.setString(3, resourceType);
            statement.setInt(4, amount);
            statement.setDouble(5, payment);
            statement.setString(6, createdAt);
            statement.executeUpdate();
        }
    }

    private void markFulfilled(Connection connection, String contractId, String fulfillingGuildId,
                               String fulfilledAt) throws SQLException {
        String sql = """
            UPDATE guild_contracts
            SET filled = amount, status = 'FULFILLED', fulfilled_by_guild_id = ?, fulfilled_at = ?
            WHERE id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fulfillingGuildId);
            statement.setString(2, fulfilledAt);
            statement.setString(3, contractId);
            statement.executeUpdate();
        }
    }

    private void markCancelled(Connection connection, String contractId) throws SQLException {
        String sql = "UPDATE guild_contracts SET status = 'CANCELLED' WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contractId);
            statement.executeUpdate();
        }
    }

    /**
     * Atomically add {@code amount} of {@code resourceType} to a guild's {@code upgrade_progress}
     * JSON column, preserving any existing entries.
     */
    private void addToUpgradeProgress(Connection connection, String guildId, String resourceType, int amount) throws SQLException {
        String selectSql = "SELECT upgrade_progress FROM guilds WHERE id = ?";
        String currentJson = "{}";

        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String raw = resultSet.getString("upgrade_progress");
                    if (raw != null && !raw.isBlank()) {
                        currentJson = raw;
                    }
                }
            }
        }

        Map<String, Integer> progress = parseProgressJson(currentJson);
        int existing = progress.getOrDefault(resourceType, 0);
        progress.put(resourceType, existing + amount);

        String updateSql = "UPDATE guilds SET upgrade_progress = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            statement.setString(1, serializeProgressJson(progress));
            statement.setString(2, guildId);
            statement.executeUpdate();
        }
    }

    /**
     * Increment a guild's balance by {@code delta} (negative to debit) and return the new balance.
     */
    private double updateBalance(Connection connection, String guildId, double delta) throws SQLException {
        String sql = "UPDATE guilds SET balance = balance + ? WHERE id = ? RETURNING balance";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, delta);
            statement.setString(2, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble(1);
                }
            }
        }
        throw new SQLException("Guild not found: " + guildId);
    }

    private boolean isOpen(Connection connection, String contractId) throws SQLException {
        String sql = "SELECT status FROM guild_contracts WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, contractId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return "OPEN".equals(resultSet.getString("status"));
                }
            }
        }
        return false;
    }

    private List<GuildContract> queryContracts(String sql, String... params) {
        List<GuildContract> contracts = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    contracts.add(mapResultSetToContract(resultSet));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query guild contracts", e);
        }
        return contracts;
    }

    private GuildContract mapResultSetToContract(ResultSet resultSet) throws SQLException {
        GuildContract contract = new GuildContract(
                resultSet.getString("id"),
                resultSet.getString("contracting_guild_id"),
                resultSet.getString("resource_type"),
                resultSet.getInt("amount"),
                resultSet.getDouble("payment")
        );
        contract.setFilled(resultSet.getInt("filled"));
        contract.setStatus(GuildContract.Status.valueOf(resultSet.getString("status")));
        String fulfilledBy = resultSet.getString("fulfilled_by_guild_id");
        if (fulfilledBy != null) {
            contract.setFulfilledByGuildId(fulfilledBy);
        }
        String createdAt = resultSet.getString("created_at");
        if (createdAt != null && !createdAt.isBlank()) {
            contract.setCreatedAt(LocalDateTime.parse(createdAt));
        }
        String fulfilledAt = resultSet.getString("fulfilled_at");
        if (fulfilledAt != null && !fulfilledAt.isBlank()) {
            contract.setFulfilledAt(LocalDateTime.parse(fulfilledAt));
        }
        return contract;
    }

    /**
     * Parse the simple {@code {"KEY":value}} JSON format used by {@code upgrade_progress}.
     */
    private Map<String, Integer> parseProgressJson(String json) {
        Map<String, Integer> progress = new HashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return progress;
        }
        try {
            String content = json.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1);
            }
            if (content.isBlank()) {
                return progress;
            }
            for (String pair : content.split(",")) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    progress.put(key, Integer.parseInt(keyValue[1].trim()));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse upgrade progress JSON: " + json, e);
        }
        return progress;
    }

    /**
     * Serialize a progress map to the simple {@code {"KEY":value}} JSON format.
     */
    private String serializeProgressJson(Map<String, Integer> progress) {
        if (progress == null || progress.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : progress.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    /**
     * Thrown inside a transaction callback to trigger a rollback without committing a stale
     * state. Extends {@link SQLException} so {@link DatabaseManager#executeTransactionWithResult}
     * rolls back the transaction and returns an empty result.
     */
    private static class ContractConflictException extends SQLException {
        ContractConflictException(String message) {
            super(message);
        }
    }
}