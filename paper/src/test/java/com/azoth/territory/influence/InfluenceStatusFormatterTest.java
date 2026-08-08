package com.azoth.territory.influence;

import com.azoth.territory.influence.Declaration;
import com.azoth.territory.influence.InfluenceBar;
import com.azoth.territory.influence.InfluenceEngine;
import com.azoth.territory.influence.TerritoryInfluenceState;
import com.azoth.territory.model.Boundary;
import com.azoth.territory.model.BlockPos;
import com.azoth.territory.model.Government;
import com.azoth.territory.model.Territory;
import com.azoth.territory.model.ZoneType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfluenceStatusFormatterTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long HOUR = 60L * 60L * 1_000L;

    private final InfluenceEngine engine = mock(InfluenceEngine.class);
    private final InfluenceStatusFormatter formatter = new InfluenceStatusFormatter();
    private final Territory territory = new Territory(
            "everfall", "Everfall", "world",
            Boundary.ofPolygon(List.of(
                    new BlockPos(0, 0), new BlockPos(100, 0),
                    new BlockPos(100, 100), new BlockPos(0, 100))),
            List.of(), ZoneType.WILDERNESS, Government.anarchy(), List.of(), "everfall-town");

    @Test
    void uncontainedLocation_hasExplicitStatus() {
        String rendered = plain(formatter.format(Optional.empty(), Optional.empty(), engine, NOW));

        assertTrue(rendered.contains("No territory"), rendered);
    }

    @Test
    void contestedTerritory_sortsBarsAndMarksDeclarableLeader() {
        when(engine.cap()).thenReturn(100.0);
        when(engine.isDeclarable("everfall", "rival-guild", NOW)).thenReturn(true);
        TerritoryInfluenceState state = new TerritoryInfluenceState(
                "everfall", "everfall-town", 0,
                List.of(new InfluenceBar("zeta-guild", 40.0), new InfluenceBar("rival-guild", 100.0)),
                null);

        String rendered = plain(formatter.format(Optional.of(territory), Optional.of(state), engine, NOW));

        assertTrue(rendered.contains("Owner: everfall-town"), rendered);
        assertTrue(rendered.contains("Top attacker: rival-guild"), rendered);
        assertTrue(rendered.contains("rival-guild=100% [DECLARABLE]"), rendered);
        assertTrue(rendered.contains("zeta-guild=40%"), rendered);
        assertTrue(rendered.indexOf("rival-guild=100%") < rendered.indexOf("zeta-guild=40%"), rendered);
    }

    @Test
    void activeDeclaration_showsFlipCountdown() {
        when(engine.cap()).thenReturn(100.0);
        TerritoryInfluenceState state = new TerritoryInfluenceState(
                "everfall", "everfall-town", 0,
                List.of(new InfluenceBar("rival-guild", 100.0)),
                new Declaration("rival-guild", NOW - HOUR, NOW + 2 * HOUR));

        String rendered = plain(formatter.format(Optional.of(territory), Optional.of(state), engine, NOW));

        assertTrue(rendered.contains("Declaration by rival-guild"), rendered);
        assertTrue(rendered.contains("flips in 2h"), rendered);
    }

    @Test
    void cooldown_showsRemainingTime() {
        when(engine.cap()).thenReturn(100.0);
        when(engine.isCooldownActive("everfall", NOW)).thenReturn(true);
        TerritoryInfluenceState state = new TerritoryInfluenceState(
                "everfall", "everfall-town", NOW + 3 * HOUR,
                List.of(), null);

        String rendered = plain(formatter.format(Optional.of(territory), Optional.of(state), engine, NOW));

        assertTrue(rendered.contains("Cooldown"), rendered);
        assertTrue(rendered.contains("3h remaining"), rendered);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
