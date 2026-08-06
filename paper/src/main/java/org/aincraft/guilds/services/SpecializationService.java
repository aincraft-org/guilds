package org.aincraft.guilds.services;

import org.aincraft.guilds.models.TownSpecialization;

import java.util.List;
import java.util.Optional;

public interface SpecializationService {
    Optional<TownSpecialization> getSpecialization(String townId);
    boolean canSpecialize(String townId);
    void setSpecialization(String townId, TownSpecialization specialization);
    void removeSpecialization(String townId);
    List<TownSpecialization> getAvailableSpecializations(String townId);
    TownSpecialization fromString(String name);
}