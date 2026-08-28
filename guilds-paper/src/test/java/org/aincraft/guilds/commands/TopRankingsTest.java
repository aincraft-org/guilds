package org.aincraft.guilds.commands;

import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;
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

    @Test
    void ranksGuildsByResidentCountDescendingWithNameTieBreak() {
        Guild beta = guild("Beta", 3);
        Guild alpha = guild("Alpha", 3);
        Guild small = guild("Small", 1);

        List<TopRankings.GuildRanking> ranked =
                TopRankings.guildsByResidentCount(List.of(beta, small, alpha));

        assertEquals(List.of("Alpha", "Beta", "Small"),
                ranked.stream().map(ranking -> ranking.guild().getName()).toList());
        assertEquals(List.of(3, 3, 1), ranked.stream().map(TopRankings.GuildRanking::value).toList());
    }

    @Test
    void ranksGuildsByLandCountDescendingWithNameTieBreak() {
        Guild beta = guild("Beta", 0);
        Guild alpha = guild("Alpha", 0);
        Guild small = guild("Small", 0);

        List<TopRankings.GuildRanking> ranked = TopRankings.guildsByLandCount(
                List.of(beta, small, alpha),
                guild -> switch (guild.getName()) {
                    case "Alpha", "Beta" -> 4;
                    case "Small" -> 1;
                    default -> 0;
                });

        assertEquals(List.of("Alpha", "Beta", "Small"),
                ranked.stream().map(ranking -> ranking.guild().getName()).toList());
        assertEquals(List.of(4, 4, 1), ranked.stream().map(TopRankings.GuildRanking::value).toList());
    }

    private static Guild guild(String name, int residentCount) {
        Guild guild = new Guild(name, UUID.randomUUID());
        for (int i = 1; i < residentCount; i++) {
            guild.addResident(UUID.randomUUID());
        }
        return guild;
    }

}
