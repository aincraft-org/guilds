package com.azoth.territory.persist;

import com.azoth.territory.economy.ExpenseEntry;
import com.azoth.territory.economy.ExpenseJournalState;
import com.azoth.territory.economy.ExpenseKind;
import com.azoth.territory.economy.ExpenseOutcome;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** JSON persistence for treasury expense idempotency records. */
public final class ExpenseStore {
    private final Path file;
    private final Gson pretty = new GsonBuilder().setPrettyPrinting().create();

    public ExpenseStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void save(Collection<ExpenseEntry> entries) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray expenses = new JsonArray();
        for (ExpenseEntry entry : entries) {
            expenses.add(toJson(entry));
        }
        root.add("expenses", expenses);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            pretty.toJson(root, writer);
        }
        moveIntoPlace(temp);
    }

    public List<ExpenseEntry> load() throws IOException {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("expense file root must be an object");
            }
            JsonElement rawExpenses = parsed.getAsJsonObject().get("expenses");
            if (rawExpenses == null || rawExpenses.isJsonNull()) {
                return List.of();
            }
            if (!rawExpenses.isJsonArray()) {
                throw new IOException("expenses must be an array");
            }
            return fromJson(rawExpenses.getAsJsonArray());
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("invalid expense file: " + file, e);
        }
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

    private static List<ExpenseEntry> fromJson(JsonArray array) {
        List<ExpenseEntry> loaded = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            loaded.add(new ExpenseEntry(
                    object.get("idempotencyKey").getAsString(),
                    object.get("territoryId").getAsString(),
                    ExpenseKind.valueOf(object.get("kind").getAsString()),
                    object.get("amount").getAsDouble(),
                    ExpenseJournalState.valueOf(object.get("state").getAsString()),
                    ExpenseOutcome.valueOf(object.get("outcome").getAsString())));
        }
        return List.copyOf(loaded);
    }

    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
