package dev.mintychochip.territory.persist;

import dev.mintychochip.territory.decree.DecreeEffects;
import dev.mintychochip.territory.decree.DecreeEffectsCodec;
import dev.mintychochip.territory.model.BlockPos;
import dev.mintychochip.territory.model.Boundary;
import dev.mintychochip.territory.model.ChunkPos;
import dev.mintychochip.territory.model.Government;
import dev.mintychochip.territory.model.GovernmentForm;
import dev.mintychochip.territory.model.GovernmentSeat;
import dev.mintychochip.territory.model.Policy;
import dev.mintychochip.territory.model.PolicyStatus;
import dev.mintychochip.territory.model.PolicyVote;
import dev.mintychochip.territory.model.SeatRole;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.model.VoteChoice;
import dev.mintychochip.territory.model.Zone;
import dev.mintychochip.territory.model.ZoneType;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared Gson codec for territories — used by file persistence and the web API.
 */
public final class TerritoryJson {
    private final Gson gson;
    private final Gson pretty;

    public TerritoryJson() {
        this.gson = new GsonBuilder().create();
        this.pretty = new GsonBuilder().setPrettyPrinting().create();
    }

    public Gson gson() {
        return gson;
    }

    public Gson pretty() {
        return pretty;
    }

    public JsonObject registryToJson(TerritoryRegistry registry) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray territories = new JsonArray();
        for (Territory t : registry.list()) {
            territories.add(toJson(t));
        }
        root.add("territories", territories);
        return root;
    }

    public List<Territory> registryFromJson(JsonObject root) {
        List<Territory> loaded = new ArrayList<>();
        JsonArray arr = root.has("territories") ? root.getAsJsonArray("territories") : new JsonArray();
        for (JsonElement el : arr) {
            loaded.add(fromJson(el.getAsJsonObject()));
        }
        return loaded;
    }

    public JsonObject toJson(Territory t) {
        JsonObject o = new JsonObject();
        o.addProperty("id", t.id());
        o.addProperty("name", t.name());
        o.addProperty("world", t.worldId());
        o.addProperty("defaultZoneType", t.defaultZoneType().name());
        o.add("boundary", boundaryToJson(t.boundary()));
        JsonArray zones = new JsonArray();
        for (Zone z : t.zones()) {
            zones.add(zoneToJson(z));
        }
        o.add("zones", zones);
        o.add("government", governmentToJson(t.government()));
        t.governedByGuildId().ifPresent(guildId -> o.addProperty("governedByGuildId", guildId));
        JsonArray policies = new JsonArray();
        for (Policy p : t.policies()) {
            policies.add(policyToJson(p));
        }
        o.add("policies", policies);
        return o;
    }

    public Territory fromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        String name = o.has("name") ? o.get("name").getAsString() : id;
        String world = o.get("world").getAsString();
        ZoneType def = o.has("defaultZoneType")
                ? ZoneType.fromString(o.get("defaultZoneType").getAsString())
                : ZoneType.WILDERNESS;
        Boundary boundary = boundaryFromJson(o.getAsJsonObject("boundary"));
        List<Zone> zones = new ArrayList<>();
        if (o.has("zones")) {
            for (JsonElement el : o.getAsJsonArray("zones")) {
                zones.add(zoneFromJson(el.getAsJsonObject()));
            }
        }
        Government government = o.has("government") && o.get("government").isJsonObject()
                ? governmentFromJson(o.getAsJsonObject("government"))
                : Government.anarchy();
        String governedByGuildId = o.has("governedByGuildId")
                ? o.get("governedByGuildId").getAsString()
                : null;
        List<Policy> policies = new ArrayList<>();
        if (o.has("policies")) {
            for (JsonElement el : o.getAsJsonArray("policies")) {
                policies.add(policyFromJson(el.getAsJsonObject()));
            }
        }
        return new Territory(id, name, world, boundary, zones, def, government, policies, governedByGuildId);
    }

    public JsonObject policyToJson(Policy p) {
        JsonObject o = new JsonObject();
        o.addProperty("id", p.id());
        o.addProperty("title", p.title());
        o.addProperty("body", p.body());
        o.addProperty("proposerId", p.proposerId());
        o.addProperty("status", p.status().name());
        p.proposedAtEpochMs().ifPresent(t -> o.addProperty("proposedAtEpochMs", t));
        p.resolvedAtEpochMs().ifPresent(t -> o.addProperty("resolvedAtEpochMs", t));
        JsonArray votes = new JsonArray();
        for (PolicyVote v : p.votes()) {
            JsonObject vo = new JsonObject();
            vo.addProperty("voterId", v.voterId());
            vo.addProperty("choice", v.choice().name());
            vo.addProperty("castAtEpochMs", v.castAtEpochMs());
            votes.add(vo);
        }
        o.add("votes", votes);
        o.add("effects", DecreeEffectsCodec.toJson(p.effects()));
        return o;
    }

    public Policy policyFromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        String title = o.has("title") ? o.get("title").getAsString() : "";
        String body = o.has("body") ? o.get("body").getAsString() : "";
        String proposer = o.get("proposerId").getAsString();
        PolicyStatus status = o.has("status")
                ? PolicyStatus.fromString(o.get("status").getAsString())
                : PolicyStatus.PROPOSED;
        Long proposed = o.has("proposedAtEpochMs") ? o.get("proposedAtEpochMs").getAsLong() : null;
        Long resolved = o.has("resolvedAtEpochMs") ? o.get("resolvedAtEpochMs").getAsLong() : null;
        List<PolicyVote> votes = new ArrayList<>();
        if (o.has("votes")) {
            for (JsonElement el : o.getAsJsonArray("votes")) {
                JsonObject vo = el.getAsJsonObject();
                votes.add(new PolicyVote(
                        vo.get("voterId").getAsString(),
                        VoteChoice.fromString(vo.get("choice").getAsString()),
                        vo.get("castAtEpochMs").getAsLong()
                ));
            }
        }
        DecreeEffects effects = o.has("effects") && o.get("effects").isJsonObject()
                ? DecreeEffectsCodec.fromJson(o.getAsJsonObject("effects"))
                : DecreeEffects.empty();
        return new Policy(id, title, body, proposer, status, votes, resolved, proposed, effects);
    }

    public JsonObject governmentToJson(Government g) {
        JsonObject o = new JsonObject();
        o.addProperty("form", g.form().name());
        JsonArray seats = new JsonArray();
        for (GovernmentSeat s : g.seats()) {
            seats.add(seatToJson(s));
        }
        o.add("seats", seats);
        return o;
    }

    public Government governmentFromJson(JsonObject o) {
        GovernmentForm form = o.has("form")
                ? GovernmentForm.fromString(o.get("form").getAsString())
                : GovernmentForm.ANARCHY;
        List<GovernmentSeat> seats = new ArrayList<>();
        if (o.has("seats")) {
            for (JsonElement el : o.getAsJsonArray("seats")) {
                seats.add(seatFromJson(el.getAsJsonObject()));
            }
        }
        return Government.of(form, seats);
    }

    private JsonObject seatToJson(GovernmentSeat s) {
        JsonObject o = new JsonObject();
        o.addProperty("id", s.seatId());
        o.addProperty("role", s.role().name());
        s.holderId().ifPresent(h -> o.addProperty("holderId", h));
        s.termEndsAtEpochMs().ifPresent(t -> o.addProperty("termEndsAtEpochMs", t));
        return o;
    }

    private GovernmentSeat seatFromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        SeatRole role = SeatRole.fromString(o.get("role").getAsString());
        String holder = o.has("holderId") && !o.get("holderId").isJsonNull()
                ? o.get("holderId").getAsString()
                : null;
        Long term = o.has("termEndsAtEpochMs") && !o.get("termEndsAtEpochMs").isJsonNull()
                ? o.get("termEndsAtEpochMs").getAsLong()
                : null;
        return new GovernmentSeat(id, role, holder, term);
    }

    public Territory fromJsonString(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    public JsonObject zoneToJson(Zone z) {
        JsonObject o = new JsonObject();
        o.addProperty("id", z.id());
        o.addProperty("name", z.name());
        o.addProperty("type", z.type().name());
        o.addProperty("priority", z.priority());
        o.add("boundary", boundaryToJson(z.boundary()));
        return o;
    }

    public Zone zoneFromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        String name = o.has("name") ? o.get("name").getAsString() : id;
        ZoneType type = ZoneType.fromString(o.get("type").getAsString());
        int priority = o.has("priority") ? o.get("priority").getAsInt() : 0;
        Boundary boundary = boundaryFromJson(o.getAsJsonObject("boundary"));
        return new Zone(id, name, type, boundary, priority);
    }

    public JsonObject boundaryToJson(Boundary b) {
        JsonObject o = new JsonObject();
        JsonArray poly = new JsonArray();
        for (BlockPos p : b.polygon()) {
            JsonObject v = new JsonObject();
            v.addProperty("x", p.x());
            v.addProperty("z", p.z());
            poly.add(v);
        }
        o.add("polygon", poly);
        JsonArray chunks = new JsonArray();
        for (ChunkPos c : b.chunks()) {
            JsonObject ch = new JsonObject();
            ch.addProperty("cx", c.chunkX());
            ch.addProperty("cz", c.chunkZ());
            chunks.add(ch);
        }
        o.add("chunks", chunks);
        return o;
    }

    public Boundary boundaryFromJson(JsonObject o) {
        List<BlockPos> poly = new ArrayList<>();
        if (o.has("polygon")) {
            for (JsonElement el : o.getAsJsonArray("polygon")) {
                JsonObject v = el.getAsJsonObject();
                poly.add(new BlockPos(v.get("x").getAsInt(), v.get("z").getAsInt()));
            }
        }
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        if (o.has("chunks")) {
            for (JsonElement el : o.getAsJsonArray("chunks")) {
                JsonObject ch = el.getAsJsonObject();
                chunks.add(new ChunkPos(ch.get("cx").getAsInt(), ch.get("cz").getAsInt()));
            }
        }
        return Boundary.of(poly, chunks);
    }
}
