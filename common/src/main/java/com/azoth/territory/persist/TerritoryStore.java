package com.azoth.territory.persist;

import com.azoth.territory.model.Territory;
import com.azoth.territory.registry.TerritoryRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * JSON save/load for the territory registry.
 * <p>
 * Uses Gson (available on the Paper server classpath; test classpath provides it).
 * Format is intentional and stable for web-map tooling.
 */
public final class TerritoryStore implements TerritoryRepository {
    public static final String DEFAULT_FILE_NAME = "territories.json";

    private final TerritoryJson json = new TerritoryJson();
    private final Path file;

    public TerritoryStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public TerritoryJson json() {
        return json;
    }

    public void save(TerritoryRegistry registry) throws IOException {
        JsonObject root = json.registryToJson(registry);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            json.pretty().toJson(root, w);
        }
    }

    public void loadInto(TerritoryRegistry registry) throws IOException {
        if (!Files.isRegularFile(file)) {
            registry.clear();
            return;
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            registry.replaceAll(json.registryFromJson(root));
        }
    }

    public List<Territory> load() throws IOException {
        TerritoryRegistry tmp = new TerritoryRegistry();
        loadInto(tmp);
        return tmp.list();
    }

    @Override
    public void close() {
        // File-backed: nothing to release.
    }
}
