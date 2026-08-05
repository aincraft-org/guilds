package com.azoth.territory.persist;

import com.azoth.territory.economy.EconomyBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Durable JSON persistence for settlement reconciliation entries. */
public final class ReconciliationStore {
    public static final String DEFAULT_FILE_NAME = "reconciliation.json";

    private final Path file;
    private final Gson pretty = new GsonBuilder().setPrettyPrinting().create();

    public ReconciliationStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
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

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            pretty.toJson(root, writer);
        }
    }

    public List<EconomyBridge.UnresolvedTransaction> load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("reconciliation file root must be an object");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement rawTransactions = root.get("transactions");
            if (rawTransactions == null || rawTransactions.isJsonNull()) {
                return List.of();
            }
            if (!rawTransactions.isJsonArray()) {
                throw new IOException("reconciliation transactions must be an array");
            }
            List<EconomyBridge.UnresolvedTransaction> entries = new ArrayList<>();
            for (JsonElement element : rawTransactions.getAsJsonArray()) {
                entries.add(fromJson(element));
            }
            return List.copyOf(entries);
        } catch (RuntimeException e) {
            throw new IOException("invalid reconciliation file: " + file, e);
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
