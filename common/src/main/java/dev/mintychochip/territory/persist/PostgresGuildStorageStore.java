package dev.mintychochip.territory.persist;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mintychochip.sql.NamedSql;
import dev.mintychochip.territory.storage.GuildStorageDocument;
import dev.mintychochip.territory.storage.GuildStorageStore;
import dev.mintychochip.territory.storage.OpaqueItemPayload;
import dev.mintychochip.territory.storage.StorageSlot;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** JSON document store for guild item banks. */
public final class PostgresGuildStorageStore implements GuildStorageStore {
    private static final NamedSql SQL = NamedSql.territory();
    private final Database database;
    private final Gson gson = new Gson();

    /**
     * Creates a store.
     *
     * @param database shared database
     */
    public PostgresGuildStorageStore(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<GuildStorageDocument> load(String guildId) throws IOException {
        if (guildId == null || guildId.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = SQL.prepare(connection, "storage/select-bank.sql",
                     Map.of("guildId", guildId.trim()));
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) {
                return Optional.empty();
            }
            JsonElement parsed = JsonParser.parseString(results.getString("doc"));
            if (!parsed.isJsonObject()) {
                throw new IOException("guild storage document must be an object");
            }
            return Optional.of(fromJson(parsed.getAsJsonObject()));
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load guild storage", e);
        }
    }

    @Override
    public void save(GuildStorageDocument document) throws IOException {
        Objects.requireNonNull(document, "document");
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     database.dialect().documentUpsertSql("guild_storage_banks", "guild_id"))) {
            statement.setString(1, document.guildId());
            statement.setString(2, gson.toJson(toJson(document)));
            statement.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save guild storage", e);
        }
    }

    private static JsonObject toJson(GuildStorageDocument document) {
        JsonObject object = new JsonObject();
        object.addProperty("guildId", document.guildId());
        object.addProperty("capacitySlots", document.capacitySlots());
        object.addProperty("revision", document.revision());
        JsonArray slots = new JsonArray();
        for (StorageSlot slot : document.slots()) {
            JsonObject row = new JsonObject();
            row.addProperty("index", slot.index());
            row.addProperty("schema", slot.item().schema());
            row.addProperty("fingerprint", slot.item().fingerprint());
            row.addProperty("payload", slot.item().payload());
            slots.add(row);
        }
        object.add("slots", slots);
        return object;
    }

    private static GuildStorageDocument fromJson(JsonObject object) {
        List<StorageSlot> slots = new ArrayList<>();
        JsonArray rows = object.getAsJsonArray("slots");
        if (rows != null) {
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                slots.add(new StorageSlot(row.get("index").getAsInt(), new OpaqueItemPayload(
                        row.get("schema").getAsString(),
                        row.get("fingerprint").getAsString(),
                        row.get("payload").getAsString())));
            }
        }
        return new GuildStorageDocument(
                object.get("guildId").getAsString(),
                object.get("capacitySlots").getAsInt(),
                object.get("revision").getAsInt(),
                slots);
    }
}
