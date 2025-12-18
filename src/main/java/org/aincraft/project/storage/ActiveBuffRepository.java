package org.aincraft.project.storage;

import org.aincraft.project.ActiveBuff;

import java.util.List;
import java.util.Optional;

public interface ActiveBuffRepository {

    void save(ActiveBuff buff);

    Optional<ActiveBuff> findById(String buffId);

    List<ActiveBuff> findByGuildId(String guildId);

    Optional<ActiveBuff> findActiveByGuildId(String guildId);

    void delete(String buffId);

    void deleteByGuildId(String guildId);

    void deleteExpired();
}
