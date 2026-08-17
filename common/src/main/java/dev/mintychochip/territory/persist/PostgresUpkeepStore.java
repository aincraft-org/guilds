package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.economy.ExpenseOutcome;
import dev.mintychochip.territory.upkeep.UpkeepState;
import dev.mintychochip.territory.upkeep.UpkeepStatus;
import dev.mintychochip.territory.upkeep.UpkeepStore;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** PostgreSQL JSONB snapshot store for recurring upkeep state. */
public final class PostgresUpkeepStore implements UpkeepStore {
    private static final int VERSION = 1;
    private final Database database;
    private final Gson gson = new Gson();

    public PostgresUpkeepStore(Database database) {
        this.database = database;
    }

    @Override
    public void save(Collection<UpkeepState> states) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject territories = new JsonObject();
        Map<String, UpkeepState> unique = new LinkedHashMap<>();
        for (UpkeepState state : states) {
            if (unique.put(state.territoryId(), state) != null) {
                throw new IOException("duplicate upkeep territory: " + state.territoryId());
            }
        }
        for (UpkeepState state : unique.values()) {
            territories.add(state.territoryId(), toJson(state));
        }
        root.add("territories", territories);

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(database.dialect().singletonUpsertSql("upkeep_state", "id"))) {
            statement.setInt(1, 1);
            statement.setString(2, gson.toJson(root));
            statement.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save upkeep state to PostgreSQL", e);
        }
    }

    @Override
    public List<UpkeepState> load() throws IOException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT doc FROM upkeep_state WHERE id = 1");
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                return List.of();
            }
            return fromJson(JsonParser.parseString(results.getString("doc")));
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load upkeep state from PostgreSQL", e);
        }
    }

    private static JsonObject toJson(UpkeepState state) {
        JsonObject object = new JsonObject();
        object.addProperty("amount", state.amount());
        object.addProperty("status", state.status().name());
        object.addProperty("nextDueEpochMs", state.nextDueEpochMs());
        object.addProperty("graceDeadlineEpochMs", state.graceDeadlineEpochMs());
        if (state.lastPeriodKey() == null) {
            object.add("lastPeriodKey", JsonNull.INSTANCE);
        } else {
            object.addProperty("lastPeriodKey", state.lastPeriodKey());
        }
        if (state.lastOutcome() == null) {
            object.add("lastOutcome", JsonNull.INSTANCE);
        } else {
            object.addProperty("lastOutcome", state.lastOutcome().name());
        }
        return object;
    }

    private static List<UpkeepState> fromJson(JsonElement parsed) throws IOException {
        if (!parsed.isJsonObject()) {
            throw new IOException("upkeep state root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonElement version = root.get("version");
        if (version == null || version.getAsInt() != VERSION) {
            throw new IOException("unsupported upkeep state version");
        }
        JsonElement rawTerritories = root.get("territories");
        if (rawTerritories == null || rawTerritories.isJsonNull()) {
            return List.of();
        }
        if (!rawTerritories.isJsonObject()) {
            throw new IOException("upkeep territories must be an object");
        }
        List<UpkeepState> states = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : rawTerritories.getAsJsonObject().entrySet()) {
            UpkeepState state = fromEntry(entry.getKey(), entry.getValue().getAsJsonObject());
            states.add(state);
        }
        return List.copyOf(states);
    }

    private static UpkeepState fromEntry(String territoryId, JsonObject object) {
        JsonElement rawLastPeriod = object.get("lastPeriodKey");
        String lastPeriod = rawLastPeriod == null || rawLastPeriod.isJsonNull()
                ? null : rawLastPeriod.getAsString();
        JsonElement rawOutcome = object.get("lastOutcome");
        ExpenseOutcome lastOutcome = rawOutcome == null || rawOutcome.isJsonNull()
                ? null : ExpenseOutcome.valueOf(rawOutcome.getAsString());
        return new UpkeepState(
                territoryId,
                object.get("amount").getAsDouble(),
                UpkeepStatus.valueOf(object.get("status").getAsString()),
                object.get("nextDueEpochMs").getAsLong(),
                object.get("graceDeadlineEpochMs").getAsLong(),
                lastPeriod,
                lastOutcome);
    }
}
