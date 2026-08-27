package dev.mintychochip.territory.permission;

import dev.mintychochip.guilds.GovernanceSource;
import dev.mintychochip.guilds.Guild;
import dev.mintychochip.guilds.alliances.Alliance;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link GovernanceSource} for tests: register guilds and alliances
 * directly, exactly like the old registry maps.
 */
public final class FakeGovernanceSource implements GovernanceSource {

    private final List<Guild> guilds = new ArrayList<>();
    private final List<Alliance> alliances = new ArrayList<>();

    public FakeGovernanceSource putGuild(Guild guild) {
        guilds.removeIf(g -> g.id().equals(guild.id()));
        guilds.add(guild);
        return this;
    }

    public FakeGovernanceSource putAlliance(Alliance alliance) {
        alliances.removeIf(a -> a.id().equals(alliance.id()));
        alliances.add(alliance);
        return this;
    }

    @Override
    public Optional<Guild> guild(String guildId) {
        return guilds.stream().filter(g -> g.id().equals(guildId)).findFirst();
    }

    @Override
    public List<Guild> guildsForMember(String holderId) {
        List<Guild> matches = new ArrayList<>();
        for (Guild g : guilds) {
            if (g.containsMember(holderId)) {
                matches.add(g);
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    @Override
    public Optional<Alliance> allianceContainingGuild(String guildId) {
        return alliances.stream().filter(a -> a.containsGuild(guildId)).findFirst();
    }

    @Override
    public List<Guild> allGuilds() {
        return List.copyOf(guilds);
    }

    @Override
    public List<Alliance> allAlliances() {
        return List.copyOf(alliances);
    }
}
