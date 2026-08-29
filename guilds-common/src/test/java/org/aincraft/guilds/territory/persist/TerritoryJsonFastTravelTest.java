package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.FastTravelMode;
import org.aincraft.guilds.territory.model.FastTravelPolicy;
import org.aincraft.guilds.territory.model.FacilityType;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Territory;
import org.aincraft.guilds.territory.model.ZoneType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerritoryJsonFastTravelTest {
    private static Boundary square() {
        return Boundary.ofPolygon(List.of(
                new BlockPos(0, 0), new BlockPos(100, 0),
                new BlockPos(100, 100), new BlockPos(0, 100)));
    }

    @Test
    void policyRoundTripsWithDeterministicEnumOrdering() {
        Map<FacilityType, Integer> quotas = new LinkedHashMap<>();
        quotas.put(FacilityType.BOAT, 2);
        quotas.put(FacilityType.AIRSHIP, 1);
        Set<FastTravelMode> modes = new LinkedHashSet<>();
        modes.add(FastTravelMode.CRYSTAL);
        modes.add(FastTravelMode.BOAT);
        Territory territory = new Territory("t1", "Territory", "world", square(), List.of(),
                ZoneType.WILDERNESS, Government.anarchy(), List.of(), null,
                new FastTravelPolicy(quotas, modes));
        TerritoryJson codec = new TerritoryJson();

        JsonObject encoded = codec.toJson(territory);
        assertEquals("{\"AIRSHIP\":1,\"BOAT\":2}", encoded.getAsJsonObject("fastTravelPolicy")
                .getAsJsonObject("facilityQuotas").toString());
        assertEquals("[\"BOAT\",\"CRYSTAL\"]", encoded.getAsJsonObject("fastTravelPolicy")
                .getAsJsonArray("crossTerritoryModes").toString());
        assertEquals(territory, codec.fromJson(encoded));
    }

    @Test
    void legacyDocumentGetsValidatedDefaultPolicy() {
        TerritoryJson codec = new TerritoryJson();
        Territory decoded = codec.fromJsonString("""
                {"id":"legacy","name":"Legacy","world":"world",
                 "boundary":{"polygon":[{"x":0,"z":0},{"x":100,"z":0},{"x":100,"z":100},{"x":0,"z":100}]},
                 "zones":[],"policies":[]}
                """);

        assertEquals(FastTravelPolicy.defaults(), decoded.fastTravelPolicy());
    }

    @Test
    void malformedPresentPolicyIsRejected() {
        TerritoryJson codec = new TerritoryJson();
        JsonObject legacy = codec.toJson(new Territory("legacy", "Legacy", "world", square()));
        legacy.getAsJsonObject("fastTravelPolicy").remove("crossTerritoryModes");

        assertThrows(RuntimeException.class, () -> codec.fromJson(legacy));
    }

    @Test
    void nonIntegralQuotaIsRejectedWithoutFloatingPointRounding() {
        TerritoryJson codec = new TerritoryJson();
        JsonObject legacy = codec.toJson(new Territory("legacy", "Legacy", "world", square()));
        legacy.getAsJsonObject("fastTravelPolicy").getAsJsonObject("facilityQuotas")
                .add("BOAT", JsonParser.parseString("1.0000000000000001"));

        assertThrows(RuntimeException.class, () -> codec.fromJson(legacy));
    }
}
