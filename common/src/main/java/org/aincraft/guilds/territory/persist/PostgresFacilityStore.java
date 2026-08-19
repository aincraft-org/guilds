package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** PostgreSQL persistence for settlement facility metadata. */
public final class PostgresFacilityStore implements FacilityStore {
    private static final String SELECT_SQL = SqlStatements.load("facility/select.sql");
    private static final String DELETE_SQL = SqlStatements.load("facility/delete.sql");
    private final Database database;
    private final Gson gson = new Gson();

    public PostgresFacilityStore(Database database) {
        this.database = database;
    }

    @Override
    public void save(Collection<SettlementFacility> facilities) throws IOException {
        List<SettlementFacility> snapshot = List.copyOf(facilities);
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement clear = c.prepareStatement(DELETE_SQL)) {
                    clear.executeUpdate();
                }
                try (PreparedStatement insert = c.prepareStatement(
                        database.dialect().documentUpsertSql("facilities", "id"))) {
                    for (SettlementFacility facility : snapshot) {
                        insert.setString(1, facility.id());
                        insert.setString(2, gson.toJson(toJson(facility)));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to save facilities to PostgreSQL", e);
        }
    }

    public void loadInto(FacilityRegistry registry) throws IOException {
        List<SettlementFacility> loaded = new ArrayList<>();
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(SELECT_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JsonElement parsed = JsonParser.parseString(rs.getString("doc"));
                if (!parsed.isJsonObject()) {
                    throw new IOException("facility document must be an object");
                }
                loaded.add(fromJson(parsed.getAsJsonObject()));
            }
        } catch (SQLException | RuntimeException e) {
            throw new IOException("Failed to load facilities from PostgreSQL", e);
        }
        try {
            registry.replaceAll(loaded);
        } catch (RuntimeException e) {
            throw new IOException("Failed to validate facilities loaded from PostgreSQL", e);
        }
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

    private static SettlementFacility fromJson(JsonObject object) {
        return new SettlementFacility(
                object.get("id").getAsString(),
                object.get("name").getAsString(),
                object.get("territoryId").getAsString(),
                FacilityType.valueOf(object.get("type").getAsString()),
                object.get("worldId").getAsString(),
                object.get("x").getAsInt(),
                object.get("y").getAsInt(),
                object.get("z").getAsInt());
    }
}
