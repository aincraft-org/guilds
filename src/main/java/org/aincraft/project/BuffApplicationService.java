package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.ActiveBuffRepository;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class BuffApplicationService {

    private final ActiveBuffRepository buffRepository;
    private final BuffCategoryRegistry buffCategoryRegistry;

    @Inject
    public BuffApplicationService(ActiveBuffRepository buffRepository, BuffCategoryRegistry buffCategoryRegistry) {
        this.buffRepository = Objects.requireNonNull(buffRepository);
        this.buffCategoryRegistry = Objects.requireNonNull(buffCategoryRegistry);
    }

    public double getXpMultiplier(String guildId) {
        return getBuffValue(guildId, "XP_MULTIPLIER", 1.0);
    }

    public double getLuckBonus(String guildId) {
        return getBuffValue(guildId, "LUCK_BONUS", 1.0);
    }

    public double getCropGrowthMultiplier(String guildId) {
        return getBuffValue(guildId, "CROP_GROWTH_SPEED", 1.0);
    }

    public double getMobSpawnMultiplier(String guildId) {
        return getBuffValue(guildId, "MOB_SPAWN_RATE", 1.0);
    }

    public double getProtectionMultiplier(String guildId) {
        return getBuffValue(guildId, "PROTECTION_BOOST", 1.0);
    }

    public double getBuffValue(String guildId, String categoryId, double defaultValue) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return defaultValue;
        }

        ActiveBuff buff = buffOpt.get();
        if (buff.isExpired()) {
            return defaultValue;
        }

        if (buff.categoryId().equals(categoryId)) {
            return buff.value();
        }

        return defaultValue;
    }

    public boolean hasBuff(String guildId, String categoryId) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(categoryId, "Category ID cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return false;
        }

        ActiveBuff buff = buffOpt.get();
        return !buff.isExpired() && buff.categoryId().equals(categoryId);
    }


    public void cleanupExpiredBuffs() {
        buffRepository.deleteExpired();
    }
}
