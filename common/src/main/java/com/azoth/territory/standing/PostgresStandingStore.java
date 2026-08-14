package com.azoth.territory.standing;

import com.azoth.territory.persist.Database;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/** PostgreSQL persistence for standing state (single doc row, mirrors influence). */
public final class PostgresStandingStore {
    private final Database database;

    public PostgresStandingStore(Database database) {
        this.database = database;
    }

    public void save(StandingState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", StandingState.VERSION);
        JsonObject territories = new JsonObject();
        for (Map.Entry<String, StandingEntry> entry : state.entries.entrySet()) {
            territories.add(entry.getKey(), toJson(entry.getValue()));
        }
        root.add("territories", territories);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().singletonUpsertSql("standing_state", "id"))) {
            ps.setInt(1, 1);
            ps.setString(2, new GsonBuilder().create().toJson(root));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save standing state to PostgreSQL", e);
        }
    }

    public StandingState load() throws IOException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT doc FROM standing_state WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new StandingState();
            }
            return fromJson(JsonParser.parseString(rs.getString("doc")));
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load standing state from PostgreSQL", e);
        }
    }

    private static JsonObject toJson(StandingEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("ownerGuildId", entry.ownerGuildId);
        JsonObject bars = new JsonObject();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.addProperty(bar.getKey(), bar.getValue());
        }
        object.add("bars", bars);
        return object;
    }

    private static StandingState fromJson(JsonElement parsed) throws IOException {
        if (!parsed.isJsonObject()) {
            throw new IOException("standing state root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonElement version = root.get("version");
        if (version == null || version.getAsInt() != StandingState.VERSION) {
            throw new IOException("unsupported standing state version");
        }
        StandingState state = new StandingState();
        JsonElement rawTerritories = root.get("territories");
        if (rawTerritories == null || rawTerritories.isJsonNull()) {
            return state;
        }
        for (Map.Entry<String, JsonElement> e : rawTerritories.getAsJsonObject().entrySet()) {
            state.entries.put(e.getKey(), fromEntry(e.getValue().getAsJsonObject()));
        }
        return state;
    }

    private static StandingEntry fromEntry(JsonObject object) throws IOException {
        StandingEntry entry = new StandingEntry();
        JsonElement owner = object.get("ownerGuildId");
        if (owner == null || owner.isJsonNull()) {
            throw new IOException("standing entry missing ownerGuildId");
        }
        entry.ownerGuildId = owner.getAsString();
        JsonElement rawBars = object.get("bars");
        if (rawBars != null && rawBars.isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : rawBars.getAsJsonObject().entrySet()) {
                entry.bars.put(b.getKey(), b.getValue().getAsDouble());
            }
        }
        return entry;
    }
}
