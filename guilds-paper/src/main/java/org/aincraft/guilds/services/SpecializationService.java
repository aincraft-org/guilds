package org.aincraft.guilds.services;

import org.aincraft.guilds.models.GuildSpecialization;

import java.util.List;
import java.util.Optional;

public interface SpecializationService {
    Optional<GuildSpecialization> getSpecialization(String guildId);
    boolean canSpecialize(String guildId);
    void setSpecialization(String guildId, GuildSpecialization specialization);
    void removeSpecialization(String guildId);
    List<GuildSpecialization> getAvailableSpecializations(String guildId);
    GuildSpecialization fromString(String name);
}