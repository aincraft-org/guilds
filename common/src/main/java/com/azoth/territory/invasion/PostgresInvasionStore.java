package com.azoth.territory.invasion;

import com.azoth.territory.persist.PostgresDatabase;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.google.gson.Gson;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** PostgreSQL JSONB snapshot store for the singleton invasion state. */
public final class PostgresInvasionStore implements InvasionStore {
    private static final int VERSION = 1;
    private final PostgresDatabase database;
    private final Gson gson = new Gson();

    public PostgresInvasionStore(PostgresDatabase database) {
        this.database = database;
    }

    @Override
    public void save(Collection<InvasionRecord> records) {
        try {
            saveChecked(records);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void saveChecked(Collection<InvasionRecord> records) throws IOException {
        if (records == null) throw new IOException("invasion state records must not be null");
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject guildDamage = new JsonObject();
        JsonArray invasions = new JsonArray();
        for (InvasionRecord record : records) {
            if (record == null) throw new IOException("invasion record must not be null");
            JsonObject damage = new JsonObject();
            damage.addProperty("destroyedBlocks", record.damage().destroyedBlocks());
            damage.addProperty("percent", record.damage().percent());
            guildDamage.add(record.guildId(), damage);
            invasions.add(toJson(record));
        }
        root.add("guildDamage", guildDamage);
        root.add("invasions", invasions);
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO invasion_state (id, doc) VALUES (1, ?::jsonb)
                     ON CONFLICT (id) DO UPDATE SET doc = EXCLUDED.doc
                     """)) {
            statement.setString(1, gson.toJson(root));
            statement.executeUpdate();
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save invasion state to PostgreSQL", e);
        }
    }

    @Override
    public List<InvasionRecord> load() {
        try {
            return loadChecked();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private List<InvasionRecord> loadChecked() throws IOException {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT doc FROM invasion_state WHERE id = 1");
             ResultSet results = statement.executeQuery()) {
            if (!results.next()) return List.of();
            try {
                return fromJson(JsonParser.parseString(results.getString("doc")));
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IOException("Malformed invasion state document", e);
            }
        } catch (SQLException e) {
            throw new IOException("Failed to load invasion state from PostgreSQL", e);
        }
    }


    private static JsonObject toJson(InvasionRecord record) {
        JsonObject object = new JsonObject();
        object.addProperty("invasionId", record.invasionId().toString());
        object.addProperty("guildId", record.guildId());
        object.addProperty("guildName", record.guildName());
        object.addProperty("worldId", record.worldId());
        object.addProperty("x", record.x());
        object.addProperty("y", record.y());
        object.addProperty("z", record.z());
        object.addProperty("status", record.status().name());
        object.addProperty("wave", record.wave());
        JsonArray entities = new JsonArray();
        for (UUID entity : record.currentWaveEntities()) entities.add(entity.toString());
        object.add("currentWaveEntities", entities);
        JsonObject damage = new JsonObject();
        damage.addProperty("destroyedBlocks", record.damage().destroyedBlocks());
        damage.addProperty("percent", record.damage().percent());
        object.add("damage", damage);
        object.addProperty("updatedAt", record.updatedAt());
        return object;
    }

    private static List<InvasionRecord> fromJson(JsonElement parsed) throws IOException {
        if (parsed == null || !parsed.isJsonObject()) throw new IOException("invasion state root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        int versionNumber = integer(root.get("version"), "invasion state version");
        if (versionNumber != VERSION)
            throw new IOException("unsupported invasion state version: " + versionNumber);
        JsonElement rawDamage = root.get("guildDamage");
        JsonElement rawInvasions = root.get("invasions");
        if (rawDamage == null || !rawDamage.isJsonObject()) throw new IOException("invasion guildDamage must be an object");
        if (rawInvasions == null || !rawInvasions.isJsonArray()) throw new IOException("invasion invasions must be an array");
        for (Map.Entry<String, JsonElement> entry : rawDamage.getAsJsonObject().entrySet()) {
            if (entry.getKey().isBlank()) throw new IOException("invasion guildDamage key is invalid");
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) throw new IOException("invasion guildDamage value is invalid");
            JsonObject damage = value.getAsJsonObject();
            long destroyedBlocks = longValue(damage, "destroyedBlocks");
            int percent = integer(damage, "percent");
            if (destroyedBlocks < 0 || percent < 0 || percent > 100)
                throw new IOException("invasion guildDamage counters are invalid");
        }
        List<InvasionRecord> result = new ArrayList<>();
        for (JsonElement raw : rawInvasions.getAsJsonArray()) {
            if (!raw.isJsonObject()) throw new IOException("invasion record must be an object");
            result.add(fromRecord(raw.getAsJsonObject()));
        }
        return List.copyOf(result);
    }

    private static InvasionRecord fromRecord(JsonObject object) throws IOException {
        try {
            UUID invasionId = uuid(object, "invasionId");
            String guildId = text(object, "guildId");
            String guildName = text(object, "guildName");
            String worldId = text(object, "worldId");
            double x = number(object, "x");
            double y = number(object, "y");
            double z = number(object, "z");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IOException("invasion coordinates must be finite");
            String statusName = text(object, "status");
            InvasionStatus status;
            try { status = InvasionStatus.valueOf(statusName); }
            catch (IllegalArgumentException e) { throw new IOException("invalid invasion status", e); }
            int wave = integer(object, "wave");
            if (wave < 0) throw new IOException("invasion wave must be non-negative");
            JsonElement rawEntities = object.get("currentWaveEntities");
            if (rawEntities == null || !rawEntities.isJsonArray()) throw new IOException("invasion entities must be an array");
            List<UUID> entities = new ArrayList<>();
            for (JsonElement entity : rawEntities.getAsJsonArray()) {
                if (!entity.isJsonPrimitive()) throw new IOException("invasion entity UUID is invalid");
                entities.add(UUID.fromString(entity.getAsString()));
            }
            JsonObject damage = object.getAsJsonObject("damage");
            if (damage == null) throw new IOException("invasion damage must be an object");
            long destroyedBlocks = longValue(damage, "destroyedBlocks");
            int percent = integer(damage, "percent");
            if (destroyedBlocks < 0 || percent < 0) throw new IOException("invasion damage counters must be non-negative");
            return new InvasionRecord(invasionId, guildId, guildName, worldId, x, y, z, status, wave, entities,
                    new GuildDamage(destroyedBlocks, percent), longValue(object, "updatedAt"));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("Malformed invasion state record", e);
        }
    }


    private static String text(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank())
            throw new IOException("invasion field is invalid: " + name);
        return value.getAsString();
    }

    private static UUID uuid(JsonObject object, String name) throws IOException {
        try { return UUID.fromString(text(object, name)); }
        catch (IllegalArgumentException e) { throw new IOException("invasion UUID is invalid: " + name, e); }
    }

    private static double number(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IOException("invasion number is invalid: " + name);
        try { return Double.parseDouble(value.getAsString()); }
        catch (NumberFormatException e) { throw new IOException("invasion number is invalid: " + name, e); }
    }

    private static int integer(JsonObject object, String name) throws IOException {
        return integer(object == null ? null : object.get(name), name);
    }

    private static int integer(JsonElement value, String name) throws IOException {
        BigInteger exact = integral(value, name, "integer");
        if (exact.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0 || exact.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
            throw new IOException("invasion integer is invalid: " + name);
        return exact.intValue();
    }

    private static long longValue(JsonObject object, String name) throws IOException {
        BigInteger exact = integral(object.get(name), name, "long");
        if (exact.compareTo(BigInteger.valueOf(Long.MIN_VALUE)) < 0 || exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
            throw new IOException("invasion long is invalid: " + name);
        return exact.longValue();
    }

    private static BigInteger integral(JsonElement value, String name, String type) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IOException("invasion " + type + " is invalid: " + name);
        try { return new BigDecimal(value.getAsString()).toBigIntegerExact(); }
        catch (NumberFormatException | ArithmeticException e) {
            throw new IOException("invasion " + type + " is invalid: " + name, e);
        }
    }
}
