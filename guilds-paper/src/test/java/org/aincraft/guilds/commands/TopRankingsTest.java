package org.aincraft.guilds.commands;

import org.aincraft.guilds.models.Alliance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopRankingsTest {

    @Test
    void ranksAlliancesByMemberGuildCountDescending() {
        UUID king = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Alliance small = new Alliance("Small", "capital-a", king);
        Alliance large = new Alliance("Large", "capital-b", king);
        large.addGuild("member-1");
        large.addGuild("member-2");
        Alliance medium = new Alliance("Medium", "capital-c", king);
        medium.addGuild("member-3");

        List<Alliance> ranked = TopRankings.alliancesByGuildCount(List.of(small, large, medium));

        assertEquals(List.of("Large", "Medium", "Small"),
                ranked.stream().map(Alliance::getName).toList());
    }
}
