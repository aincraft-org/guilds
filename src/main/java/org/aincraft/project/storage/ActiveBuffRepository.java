package org.aincraft.project.storage;

import org.aincraft.project.ActiveBuff;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.UUID;

public interface ActiveBuffRepository {

    void save(ActiveBuff buff);

    Optional<ActiveBuff> findById(String buffId);

    List<ActiveBuff> findByGuildId(UUID guildId);

    Optional<ActiveBuff> findActiveByGuildId(UUID guildId);

    void delete(String buffId);

    void deleteByGuildId(UUID guildId);

    void deleteExpired();
}
