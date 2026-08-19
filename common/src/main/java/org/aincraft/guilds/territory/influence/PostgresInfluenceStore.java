package org.aincraft.guilds.territory.influence;

import org.aincraft.guilds.territory.persist.Database;
import org.aincraft.guilds.territory.persist.SqlStatements;
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

/** PostgreSQL persistence for influence race state. */
public final class PostgresInfluenceStore {
    private static final String SELECT_SQL = SqlStatements.load("influence/select.sql");
    private final Database database;

    public PostgresInfluenceStore(Database database) {
        this.database = database;
    }

    public void save(InfluenceState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", InfluenceState.VERSION);
        JsonObject territories = new JsonObject();
        for (Map.Entry<String, TerritoryEntry> entry : state.entries.entrySet()) {
            territories.add(entry.getKey(), toJson(entry.getValue()));
        }
        root.add("territories", territories);
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(database.dialect().singletonUpsertSql("influence_state", "id"))) {
            ps.setInt(1, 1);
            ps.setString(2, new GsonBuilder().create().toJson(root));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IOException("Failed to save influence state to PostgreSQL", e);
        }
    }

    public InfluenceState load() throws IOException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return new InfluenceState();
            }
            return fromJson(JsonParser.parseString(rs.getString("doc")));
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load influence state from PostgreSQL", e);
        }
    }

    private static JsonObject toJson(TerritoryEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("ownerGuildId", entry.ownerGuildId);
        object.addProperty("cooldownUntilEpochMs", entry.cooldownUntilEpochMs);
        JsonObject bars = new JsonObject();
        for (Map.Entry<String, Double> bar : entry.bars.entrySet()) {
            bars.addProperty(bar.getKey(), bar.getValue());
        }
        object.add("bars", bars);
        if (entry.declaration != null) {
            JsonObject declaration = new JsonObject();
            declaration.addProperty("guildId", entry.declaration.guildId());
            declaration.addProperty("declaredAtEpochMs", entry.declaration.declaredAtEpochMs());
            declaration.addProperty("flipAtEpochMs", entry.declaration.flipAtEpochMs());
            object.add("declaration", declaration);
        }
        if (entry.pendingFlip != null) {
            JsonObject pending = new JsonObject();
            pending.addProperty("territoryId", entry.pendingFlip.territoryId());
            pending.addProperty("oldOwnerGuildId", entry.pendingFlip.oldOwnerGuildId());
            pending.addProperty("newOwnerGuildId", entry.pendingFlip.newOwnerGuildId());
            pending.addProperty("flipAtEpochMs", entry.pendingFlip.flipAtEpochMs());
            pending.addProperty("cooldownUntilEpochMs", entry.pendingFlip.cooldownUntilEpochMs());
            object.add("pendingFlip", pending);
        }
        return object;
    }

    private static InfluenceState fromJson(JsonElement parsed) throws IOException {
        if (!parsed.isJsonObject()) {
            throw new IOException("influence state root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonElement version = root.get("version");
        if (version == null || version.getAsInt() != InfluenceState.VERSION) {
            throw new IOException("unsupported influence state version");
        }
        InfluenceState state = new InfluenceState();
        JsonElement rawTerritories = root.get("territories");
        if (rawTerritories == null || rawTerritories.isJsonNull()) {
            return state;
        }
        if (!rawTerritories.isJsonObject()) {
            throw new IOException("influence territories must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : rawTerritories.getAsJsonObject().entrySet()) {
            state.entries.put(entry.getKey(), fromEntry(entry.getValue().getAsJsonObject()));
        }
        return state;
    }

    private static TerritoryEntry fromEntry(JsonObject object) {
        TerritoryEntry entry = new TerritoryEntry();
        JsonElement owner = object.get("ownerGuildId");
        entry.ownerGuildId = owner == null || owner.isJsonNull() ? null : owner.getAsString();
        JsonElement cooldown = object.get("cooldownUntilEpochMs");
        entry.cooldownUntilEpochMs = cooldown == null || cooldown.isJsonNull() ? 0L : cooldown.getAsLong();
        JsonElement bars = object.get("bars");
        if (bars != null && bars.isJsonObject()) {
            for (Map.Entry<String, JsonElement> bar : bars.getAsJsonObject().entrySet()) {
                entry.bars.put(bar.getKey(), bar.getValue().getAsDouble());
            }
        }
        JsonElement rawDeclaration = object.get("declaration");
        if (rawDeclaration != null && rawDeclaration.isJsonObject()) {
            JsonObject declaration = rawDeclaration.getAsJsonObject();
            entry.declaration = new Declaration(
                    declaration.get("guildId").getAsString(),
                    declaration.get("declaredAtEpochMs").getAsLong(),
                    declaration.get("flipAtEpochMs").getAsLong());
        }
        JsonElement rawPending = object.get("pendingFlip");
        if (rawPending != null && rawPending.isJsonObject()) {
            JsonObject pending = rawPending.getAsJsonObject();
            entry.pendingFlip = new PendingFlip(
                    pending.get("territoryId").getAsString(),
                    pending.get("oldOwnerGuildId").getAsString(),
                    pending.get("newOwnerGuildId").getAsString(),
                    pending.get("flipAtEpochMs").getAsLong(),
                    pending.get("cooldownUntilEpochMs").getAsLong());
        }
        return entry;
    }
}
