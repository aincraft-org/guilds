package org.aincraft.guilds.commands;

import org.aincraft.guilds.models.Alliance;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Shared ranking helpers for {@code /g top} and {@code /guilds top}.
 */
public final class TopRankings {

    private TopRankings() {
    }

    public static List<Alliance> alliancesByGuildCount(Collection<Alliance> alliances) {
        return alliances.stream()
                .sorted(Comparator.comparingInt(Alliance::getGuildCount).reversed()
                        .thenComparing(Alliance::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
