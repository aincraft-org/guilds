package dev.mintychochip.guilds.services;

import dev.mintychochip.guilds.models.GuildSpecialization;

import java.util.List;
import java.util.Optional;

/** Defines operations for specialization service. */
public interface SpecializationService {
    /**
     * Returns the specialization.
     * @param guildId the guild id
     * @return the result
     */
    Optional<GuildSpecialization> getSpecialization(String guildId);
    /**
     * Returns whether specialize.
     * @param guildId the guild id
     * @return the result
     */
    boolean canSpecialize(String guildId);
    /**
     * Sets the specialization.
     * @param guildId the guild id
     * @param specialization the specialization
     */
    void setSpecialization(String guildId, GuildSpecialization specialization);
    /**
     * Removes the specialization.
     * @param guildId the guild id
     */
    void removeSpecialization(String guildId);
    /**
     * Returns the available specializations.
     * @param guildId the guild id
     * @return the result
     */
    List<GuildSpecialization> getAvailableSpecializations(String guildId);
    /**
     * Performs the from string operation.
     * @param name the name
     * @return the result
     */
    GuildSpecialization fromString(String name);
}