package com.azoth.territory.persist;

import com.azoth.territory.economy.ExpenseEntry;
import com.azoth.territory.economy.ExpenseJournalState;
import com.azoth.territory.economy.ExpenseKind;
import com.azoth.territory.economy.ExpenseOutcome;
import com.google.gson.Gson;
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

/** PostgreSQL persistence for treasury expense idempotency records. */
public final class PostgresExpenseStore {
    private final PostgresDatabase database;
    private final Gson gson = new Gson();

    public PostgresExpenseStore(PostgresDatabase database) {
        this.database = database;
    }

    public void save(Collection<ExpenseEntry> entries) throws IOException {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement clear = c.prepareStatement("DELETE FROM expenses")) {
                    clear.executeUpdate();
                }
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO expenses (idempotency_key, doc) VALUES (?, ?::jsonb)")) {
                    for (ExpenseEntry entry : entries) {
                        insert.setString(1, entry.idempotencyKey());
                        insert.setString(2, gson.toJson(toJson(entry)));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save expenses to PostgreSQL", e);
        }
    }

    public List<ExpenseEntry> load() throws IOException {
        List<ExpenseEntry> loaded = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT doc FROM expenses ORDER BY idempotency_key");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonElement parsed = JsonParser.parseString(rs.getString("doc"));
                loaded.add(fromJson(parsed.getAsJsonObject()));
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load expenses from PostgreSQL", e);
        }
        return List.copyOf(loaded);
    }

    private static JsonObject toJson(ExpenseEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("idempotencyKey", entry.idempotencyKey());
        object.addProperty("territoryId", entry.territoryId());
        object.addProperty("kind", entry.kind().name());
        object.addProperty("amount", entry.amount());
        object.addProperty("state", entry.state().name());
        object.addProperty("outcome", entry.outcome().name());
        return object;
    }

    private static ExpenseEntry fromJson(JsonElement element) {
        JsonObject object = element.getAsJsonObject();
        return new ExpenseEntry(
                object.get("idempotencyKey").getAsString(),
                object.get("territoryId").getAsString(),
                ExpenseKind.valueOf(object.get("kind").getAsString()),
                object.get("amount").getAsDouble(),
                ExpenseJournalState.valueOf(object.get("state").getAsString()),
                ExpenseOutcome.valueOf(object.get("outcome").getAsString()));
    }
}
