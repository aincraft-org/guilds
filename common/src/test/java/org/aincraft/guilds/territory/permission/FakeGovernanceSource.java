package org.aincraft.guilds.territory.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link GovernanceSource} for tests: register guilds and alliances
 * directly, exactly like the old registry maps.
 */
public final class FakeGovernanceSource implements GovernanceSource {

    private final List<GuildBody> guilds = new ArrayList<>();
    private final List<AllianceBody> alliances = new ArrayList<>();

    public FakeGovernanceSource putGuild(GuildBody guild) {
        guilds.removeIf(g -> g.id().equals(guild.id()));
        guilds.add(guild);
        return this;
    }

    public FakeGovernanceSource putAlliance(AllianceBody alliance) {
        alliances.removeIf(a -> a.id().equals(alliance.id()));
        alliances.add(alliance);
        return this;
    }

    @Override
    public Optional<GuildBody> guild(String guildId) {
        return guilds.stream().filter(g -> g.id().equals(guildId)).findFirst();
    }

    @Override
    public List<GuildBody> guildsForMember(String holderId) {
        List<GuildBody> matches = new ArrayList<>();
        for (GuildBody g : guilds) {
            if (g.containsMember(holderId)) {
                matches.add(g);
            }
        }
        matches.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(matches);
    }

    @Override
    public Optional<AllianceBody> allianceContainingGuild(String guildId) {
        return alliances.stream().filter(a -> a.containsGuild(guildId)).findFirst();
    }

    @Override
    public List<GuildBody> allGuilds() {
        return List.copyOf(guilds);
    }

    @Override
    public List<AllianceBody> allAlliances() {
        return List.copyOf(alliances);
    }
}
