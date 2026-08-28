package org.aincraft.guilds.commands;

import org.aincraft.guilds.models.Alliance;
import org.aincraft.guilds.models.Guild;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Shared ranking helpers for {@code /g top} and {@code /guilds top}.
 */
public final class TopRankings {

    private TopRankings() {
    }

    public record GuildRanking(Guild guild, int value) {
    }

    public static List<GuildRanking> guildsByResidentCount(Collection<Guild> guilds) {
        return rankGuilds(guilds, Guild::getResidentCount);
    }

    public static List<GuildRanking> guildsByLandCount(
            Collection<Guild> guilds, ToIntFunction<Guild> landCount) {
        return rankGuilds(guilds, landCount);
    }

    private static List<GuildRanking> rankGuilds(
            Collection<Guild> guilds, ToIntFunction<Guild> valueFunction) {
        return guilds.stream()
                .map(guild -> new GuildRanking(guild, valueFunction.applyAsInt(guild)))
                .sorted(Comparator.comparingInt(GuildRanking::value).reversed()
                        .thenComparing(ranking -> ranking.guild().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ranking -> ranking.guild().getName()))
                .toList();
    }

    public static List<Alliance> alliancesByGuildCount(Collection<Alliance> alliances) {
        return alliances.stream()
                .sorted(Comparator.comparingInt(Alliance::getGuildCount).reversed()
                        .thenComparing(Alliance::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
