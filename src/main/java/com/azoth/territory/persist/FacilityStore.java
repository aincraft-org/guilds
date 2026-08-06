package com.azoth.territory.persist;

import com.azoth.territory.model.FacilityType;
import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.registry.FacilityRegistry;
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
import java.util.List;

/** JSON persistence for settlement facility metadata. */
public final class FacilityStore {
    private final Path file;
    private final Gson pretty = new GsonBuilder().setPrettyPrinting().create();

    public FacilityStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public void save(FacilityRegistry registry) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray facilities = new JsonArray();
        for (SettlementFacility facility : registry.list()) {
            facilities.add(toJson(facility));
        }
        root.add("facilities", facilities);

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

    public void loadInto(FacilityRegistry registry) throws IOException {
        if (!Files.isRegularFile(file)) {
            registry.replaceAll(List.of());
            return;
        }
        List<SettlementFacility> loaded;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                throw new IOException("facility file root must be an object");
            }
            JsonElement rawFacilities = parsed.getAsJsonObject().get("facilities");
            if (rawFacilities == null || rawFacilities.isJsonNull()) {
                loaded = List.of();
            } else if (!rawFacilities.isJsonArray()) {
                throw new IOException("facilities must be an array");
            } else {
                loaded = fromJson(rawFacilities.getAsJsonArray());
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("invalid facility file: " + file, e);
        }
        registry.replaceAll(loaded);
    }

    private static JsonObject toJson(SettlementFacility facility) {
        JsonObject object = new JsonObject();
        object.addProperty("id", facility.id());
        object.addProperty("name", facility.name());
        object.addProperty("territoryId", facility.territoryId());
        object.addProperty("type", facility.type().name());
        object.addProperty("worldId", facility.worldId());
        object.addProperty("x", facility.x());
        object.addProperty("y", facility.y());
        object.addProperty("z", facility.z());
        return object;
    }

    private static List<SettlementFacility> fromJson(JsonArray array) {
        List<SettlementFacility> loaded = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = element.getAsJsonObject();
            loaded.add(new SettlementFacility(
                    object.get("id").getAsString(),
                    object.get("name").getAsString(),
                    object.get("territoryId").getAsString(),
                    FacilityType.valueOf(object.get("type").getAsString()),
                    object.get("worldId").getAsString(),
                    object.get("x").getAsInt(),
                    object.get("y").getAsInt(),
                    object.get("z").getAsInt()));
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
