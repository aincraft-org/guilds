package dev.mintychochip.territory.persist;

import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.territory.economy.EconomyBridge;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL persistence for settlement reconciliation entries. */
public final class PostgresReconciliationStore {
    private static final NamedSql SQL = NamedSql.territory();
    private static final String KEY = "state";
    private final Database database;
    private final Gson gson = new Gson();

    public PostgresReconciliationStore(Database database) {
        this.database = database;
    }

    public void save(Collection<EconomyBridge.UnresolvedTransaction> entries) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray transactions = new JsonArray();
        if (entries != null) {
            for (EconomyBridge.UnresolvedTransaction entry : entries) {
                transactions.add(toJson(entry));
            }
        }
        root.add("transactions", transactions);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().documentUpsertSql("reconciliation_entries", "idempotency_key"))) {
            ps.setString(1, KEY);
            ps.setString(2, gson.toJson(root));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save reconciliation state to PostgreSQL", e);
        }
    }

    public List<EconomyBridge.UnresolvedTransaction> load() throws IOException {
        try (Connection c = database.connection();
             PreparedStatement ps = SQL.prepare(c, "persist/select-reconciliation.sql", Map.of(
                     "idempotency_key", KEY))) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return List.of();
                }
                JsonElement parsed = JsonParser.parseString(rs.getString("doc"));
                if (!parsed.isJsonObject()) {
                    throw new IOException("reconciliation state root must be an object");
                }
                JsonElement raw = parsed.getAsJsonObject().get("transactions");
                if (raw == null || raw.isJsonNull()) {
                    return List.of();
                }
                if (!raw.isJsonArray()) {
                    throw new IOException("reconciliation transactions must be an array");
                }
                List<EconomyBridge.UnresolvedTransaction> result = new ArrayList<>();
                for (JsonElement element : raw.getAsJsonArray()) {
                    result.add(fromJson(element));
                }
                return List.copyOf(result);
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load reconciliation state from PostgreSQL", e);
        }
    }

    private static JsonObject toJson(EconomyBridge.UnresolvedTransaction entry) {
        JsonObject object = new JsonObject();
        object.addProperty("territoryId", entry.territoryId());
        object.addProperty("payerUuid", entry.payerUuid().toString());
        object.addProperty("amount", entry.amount());
        object.addProperty("timestampEpochMs", entry.timestampEpochMs());
        object.addProperty("reason", entry.reason());
        return object;
    }

    private static EconomyBridge.UnresolvedTransaction fromJson(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        return new EconomyBridge.UnresolvedTransaction(
                object.get("territoryId").getAsString(),
                UUID.fromString(object.get("payerUuid").getAsString()),
                object.get("amount").getAsDouble(),
                object.get("timestampEpochMs").getAsLong(),
                object.get("reason").getAsString());
    }
}
