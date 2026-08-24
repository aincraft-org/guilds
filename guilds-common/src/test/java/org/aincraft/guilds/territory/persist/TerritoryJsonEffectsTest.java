package org.aincraft.guilds.territory.persist;

import org.aincraft.guilds.territory.decree.DecreeEffects;
import org.aincraft.guilds.territory.decree.TaxEffect;
import org.aincraft.guilds.territory.model.BlockPos;
import org.aincraft.guilds.territory.model.Boundary;
import org.aincraft.guilds.territory.model.Government;
import org.aincraft.guilds.territory.model.Policy;
import org.aincraft.guilds.territory.model.Territory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Policy.effects survives the TerritoryJson codec, with back-compat for absent keys. */
class TerritoryJsonEffectsTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final TerritoryJson JSON = new TerritoryJson();

    private static final Boundary SQUARE = Boundary.ofPolygon(List.of(
            new BlockPos(0, 0), new BlockPos(10, 0), new BlockPos(10, 10), new BlockPos(0, 10)
    ));

    private static DecreeEffects carrotTax() {
        return DecreeEffects.ofTax(new TaxEffect(List.of("carrot"), 15.0));
    }

    private static Territory territoryWith(DecreeEffects effects) {
        Territory t = new Territory("t1", "T", "world", SQUARE)
                .withGovernment(Government.monarchy("king:arthur"));
        return t.proposePolicy("tax", "Tax", "B", "king:arthur", NOW, effects);
    }

    @Test
    void effectsRoundTripThroughPolicyJson() {
        Policy p = territoryWith(carrotTax()).policy("tax").orElseThrow();
        Policy round = JSON.policyFromJson(JSON.policyToJson(p));
        assertEquals(carrotTax(), round.effects());
    }

    @Test
    void effectsRoundTripThroughTerritoryJson() {
        Territory t = territoryWith(carrotTax());
        Territory round = JSON.fromJson(JSON.toJson(t));
        assertEquals(carrotTax(), round.policy("tax").orElseThrow().effects());
    }

    @Test
    void policyWithoutEffectsRoundTripsAsEmpty() {
        Territory plain = new Territory("t", "T", "w", SQUARE).withGovernment(Government.monarchy("k"));
        Territory proposed = plain.proposePolicy("p", "P", "B", "k", NOW, DecreeEffects.empty());
        Policy round = JSON.policyFromJson(JSON.policyToJson(proposed.policy("p").orElseThrow()));
        assertEquals(DecreeEffects.empty(), round.effects());
    }
}
