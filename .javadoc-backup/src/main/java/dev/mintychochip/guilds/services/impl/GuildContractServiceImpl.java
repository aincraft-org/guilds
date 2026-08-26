package dev.mintychochip.guilds.services.impl;

import dev.mintychochip.guilds.database.DatabaseManager;
import dev.mintychochip.guilds.models.Guild;
import dev.mintychochip.guilds.models.GuildContract;
import dev.mintychochip.guilds.services.GuildContractService;
import dev.mintychochip.guilds.services.GuildService;
import dev.mintychochip.sql.NamedSql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    /** The sql constant. */
    private static final NamedSql SQL = NamedSql.guilds();

    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(GuildContractServiceImpl.class.getName());

    /** The database manager. */
    private final DatabaseManager databaseManager;
    /** The guild service. */
    private final GuildService guildService;

    /**
     * Creates a new guild contract service impl instance.
     * @param databaseManager the database manager
     * @param guildService the guild service
     */
    public GuildContractServiceImpl(DatabaseManager databaseManager, GuildService guildService) {
        this.databaseManager = databaseManager;
        this.guildService = guildService;
    }

    /**
     * Creates a new contract.
     * @param contractingGuildId the contracting guild id
     * @param resourceType the resource type
     * @param amount the amount
     * @param payment the payment
     * @return the result
     */
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

    /**
     * Returns the open contracts.
     * @return the result
     */
    @Override
    public List<GuildContract> getOpenContracts() {
        return queryContracts("contracts/select-open.sql", Map.of());
    }

    /**
     * Returns the contracts for guild.
     * @param guildId the guild id
     * @return the result
     */
    @Override
    public List<GuildContract> getContractsForGuild(String guildId) {
        return queryContracts("contracts/select-by-guild.sql", Map.of("guild_id", guildId));
    }

    /**
     * Returns the contract.
     * @param contractId the contract id
     * @return the result
     */
    @Override
    public Optional<GuildContract> getContract(String contractId) {
        if (contractId == null) {
            return Optional.empty();
        }
        List<GuildContract> results = queryContracts("contracts/select-by-id.sql", Map.of("id", contractId));
        return results.stream().findFirst();
    }

    /**
     * Performs the fulfill contract operation.
     * @param contractId the contract id
     * @param fulfillingGuildId the fulfilling guild id
     * @return the result
     */
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

    /**
     * Returns whether cel contract.
     * @param contractId the contract id
     * @param contractingGuildId the contracting guild id
     * @return the result
     */
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

    /**
     * Inserts the contract.
     * @param connection the connection
     * @param id the id
     * @param contractingGuildId the contracting guild id
     * @param resourceType the resource type
     * @param amount the amount
     * @param payment the payment
     * @param createdAt the created at
     * @throws SQLException if an error occurs
     */
    private void insertContract(Connection connection, String id, String contractingGuildId,
                                String resourceType, int amount, double payment, String createdAt) throws SQLException {
        try (PreparedStatement statement = SQL.prepare(connection, "contracts/insert.sql", Map.of(
                "id", id,
                "contracting_guild_id", contractingGuildId,
                "resource_type", resourceType,
                "amount", amount,
                "payment", payment,
                "created_at", createdAt))) {
            statement.executeUpdate();
        }
    }

    /**
     * Performs the mark fulfilled operation.
     * @param connection the connection
     * @param contractId the contract id
     * @param fulfillingGuildId the fulfilling guild id
     * @param fulfilledAt the fulfilled at
     * @throws SQLException if an error occurs
     */
    private void markFulfilled(Connection connection, String contractId, String fulfillingGuildId,
                               String fulfilledAt) throws SQLException {
        try (PreparedStatement statement = SQL.prepare(connection, "contracts/mark-fulfilled.sql", Map.of(
                "fulfilling_guild_id", fulfillingGuildId,
                "fulfilled_at", fulfilledAt,
                "id", contractId))) {
            statement.executeUpdate();
        }
    }

    /**
     * Performs the mark cancelled operation.
     * @param connection the connection
     * @param contractId the contract id
     * @throws SQLException if an error occurs
     */
    private void markCancelled(Connection connection, String contractId) throws SQLException {
        try (PreparedStatement statement = SQL.prepare(connection, "contracts/mark-cancelled.sql", Map.of(
                "id", contractId))) {
            statement.executeUpdate();
        }
    }

    /**
     * Atomically add {@code amount} of {@code resourceType} to a guild's {@code upgrade_progress}
     * JSON column, preserving any existing entries.
     */
    private void addToUpgradeProgress(Connection connection, String guildId, String resourceType, int amount) throws SQLException {
        String currentJson = "{}";

        try (PreparedStatement statement = SQL.prepare(connection, "contracts/select-upgrade-progress.sql", Map.of(
                "guild_id", guildId))) {
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

        try (PreparedStatement statement = SQL.prepare(connection, "contracts/update-upgrade-progress.sql", Map.of(
                "upgrade_progress", serializeProgressJson(progress),
                "guild_id", guildId))) {
            statement.executeUpdate();
        }
    }

    /**
     * Increment a guild's balance by {@code delta} (negative to debit) and return the new balance.
     */
    private double updateBalance(Connection connection, String guildId, double delta) throws SQLException {
        try (PreparedStatement statement = SQL.prepare(connection, "contracts/update-balance.sql", Map.of(
                "delta", delta,
                "guild_id", guildId))) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getDouble(1);
                }
            }
        }
        throw new SQLException("Guild not found: " + guildId);
    }

    /**
     * Returns whether open.
     * @param connection the connection
     * @param contractId the contract id
     * @return the result
     * @throws SQLException if an error occurs
     */
    private boolean isOpen(Connection connection, String contractId) throws SQLException {
        try (PreparedStatement statement = SQL.prepare(connection, "contracts/select-status.sql", Map.of(
                "id", contractId))) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return "OPEN".equals(resultSet.getString("status"));
                }
            }
        }
        return false;
    }

    /**
     * Performs the query contracts operation.
     * @param sqlPath the sql path
     * @param params the params
     * @return the result
     */
    private List<GuildContract> queryContracts(String sqlPath, Map<String, ?> params) {
        List<GuildContract> contracts = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = SQL.prepare(connection, sqlPath, params)) {

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

    /**
     * Performs the map result set to contract operation.
     * @param resultSet the result set
     * @return the result
     * @throws SQLException if an error occurs
     */
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
        /**
         * Creates a new contract conflict exception instance.
         * @param message the message
         */
        ContractConflictException(String message) {
            super(message);
        }
    }
}
