package com.azoth.territory.influence;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON persistence for influence race state (spec §6).
 * <p>
 * File format (literal JSON, version 1):
 * <pre>
 * { "version": 1, "territories": { "&lt;id&gt;": {
 *     "ownerGuildId": "...", "cooldownUntilEpochMs": 0,
 *     "bars": { "guild": 62.5 },
 *     "declaration": { "guildId": "...", "declaredAtEpochMs": 0, "flipAtEpochMs": 0 },
 *     "pendingFlip": { "territoryId": "...", "oldOwnerGuildId": "...",
 *         "newOwnerGuildId": "...", "flipAtEpochMs": 0, "cooldownUntilEpochMs": 0 } } } }
 * </pre>
 * All writes go through a temp file + atomic move.
 */
public final class InfluenceStore {

    private final Path file;

    public InfluenceStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void save(InfluenceState state) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", InfluenceState.VERSION);
        JsonObject territories = new JsonObject();
        List<String> keys = new ArrayList<>(state.entries.keySet());
        Collections.sort(keys); // stable file format for tooling
        for (String key : keys) {
            territories.add(key, toJson(state.entries.get(key)));
        }
        root.add("territories", territories);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        moveIntoPlace(temp);
    }

    public InfluenceState load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return new InfluenceState();
        }
        InfluenceState state = new InfluenceState();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("influence file root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement rawVersion = root.get("version");
            if (rawVersion == null || rawVersion.getAsInt() != InfluenceState.VERSION) {
                throw new IOException("unsupported influence file version: "
                        + (rawVersion == null ? "missing" : rawVersion.getAsInt()));
            }
            JsonElement rawTerritories = root.get("territories");
            if (rawTerritories == null || rawTerritories.isJsonNull()) {
                return state;
            }
            if (!rawTerritories.isJsonObject()) {
                throw new IOException("territories must be an object");
            }
            for (Map.Entry<String, JsonElement> e : rawTerritories.getAsJsonObject().entrySet()) {
                state.entries.put(e.getKey(), fromJson(e.getValue().getAsJsonObject()));
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("invalid influence file: " + file, e);
        }
        return state;
    }

    private static JsonObject toJson(TerritoryEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty("ownerGuildId", entry.ownerGuildId);
        object.addProperty("cooldownUntilEpochMs", entry.cooldownUntilEpochMs);
        JsonObject bars = new JsonObject();
        for (Map.Entry<String, Double> b : entry.bars.entrySet()) {
            bars.addProperty(b.getKey(), b.getValue());
        }
        object.add("bars", bars);
        if (entry.declaration != null) {
            JsonObject d = new JsonObject();
            d.addProperty("guildId", entry.declaration.guildId());
            d.addProperty("declaredAtEpochMs", entry.declaration.declaredAtEpochMs());
            d.addProperty("flipAtEpochMs", entry.declaration.flipAtEpochMs());
            object.add("declaration", d);
        }
        if (entry.pendingFlip != null) {
            JsonObject p = new JsonObject();
            p.addProperty("territoryId", entry.pendingFlip.territoryId());
            p.addProperty("oldOwnerGuildId", entry.pendingFlip.oldOwnerGuildId());
            p.addProperty("newOwnerGuildId", entry.pendingFlip.newOwnerGuildId());
            p.addProperty("flipAtEpochMs", entry.pendingFlip.flipAtEpochMs());
            p.addProperty("cooldownUntilEpochMs", entry.pendingFlip.cooldownUntilEpochMs());
            object.add("pendingFlip", p);
        }
        return object;
    }

    private static TerritoryEntry fromJson(JsonObject object) {
        TerritoryEntry entry = new TerritoryEntry();
        JsonElement owner = object.get("ownerGuildId");
        entry.ownerGuildId = owner == null || owner.isJsonNull() ? null : owner.getAsString();
        JsonElement cooldown = object.get("cooldownUntilEpochMs");
        entry.cooldownUntilEpochMs = cooldown == null || cooldown.isJsonNull() ? 0L : cooldown.getAsLong();
        JsonElement rawBars = object.get("bars");
        if (rawBars != null && rawBars.isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : rawBars.getAsJsonObject().entrySet()) {
                entry.bars.put(b.getKey(), b.getValue().getAsDouble());
            }
        }
        JsonElement rawDeclaration = object.get("declaration");
        if (rawDeclaration != null && rawDeclaration.isJsonObject()) {
            JsonObject d = rawDeclaration.getAsJsonObject();
            entry.declaration = new Declaration(
                    d.get("guildId").getAsString(),
                    d.get("declaredAtEpochMs").getAsLong(),
                    d.get("flipAtEpochMs").getAsLong());
        }
        JsonElement rawPending = object.get("pendingFlip");
        if (rawPending != null && rawPending.isJsonObject()) {
            JsonObject p = rawPending.getAsJsonObject();
            entry.pendingFlip = new PendingFlip(
                    p.get("territoryId").getAsString(),
                    p.get("oldOwnerGuildId").getAsString(),
                    p.get("newOwnerGuildId").getAsString(),
                    p.get("flipAtEpochMs").getAsLong(),
                    p.get("cooldownUntilEpochMs").getAsLong());
        }
        return entry;
    }

    /**
     * Move the corrupt file aside for manual recovery (spec §6). The backup
     * name is unique per millisecond, so no earlier backup is overwritten.
     */
    public Path backupCorrupt() throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-"
                + System.currentTimeMillis());
        Files.move(file, backup);
        return backup;
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
