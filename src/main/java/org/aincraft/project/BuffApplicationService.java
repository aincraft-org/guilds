package org.aincraft.project;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.project.storage.ActiveBuffRepository;

import java.util.Objects;
import java.util.Optional;

@Singleton
public class BuffApplicationService {

    private final ActiveBuffRepository buffRepository;

    @Inject
    public BuffApplicationService(ActiveBuffRepository buffRepository) {
        this.buffRepository = Objects.requireNonNull(buffRepository);
    }

    public double getXpMultiplier(String guildId) {
        return getBuffValue(guildId, BuffCategory.XP_MULTIPLIER, 1.0);
    }

    public double getLuckBonus(String guildId) {
        return getBuffValue(guildId, BuffCategory.LUCK_BONUS, 1.0);
    }

    public double getCropGrowthMultiplier(String guildId) {
        return getBuffValue(guildId, BuffCategory.CROP_GROWTH_SPEED, 1.0);
    }

    public double getMobSpawnMultiplier(String guildId) {
        return getBuffValue(guildId, BuffCategory.MOB_SPAWN_RATE, 1.0);
    }

    public double getProtectionMultiplier(String guildId) {
        return getBuffValue(guildId, BuffCategory.PROTECTION_BOOST, 1.0);
    }

    public double getBuffValue(String guildId, BuffCategory category, double defaultValue) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return defaultValue;
        }

        ActiveBuff buff = buffOpt.get();
        if (buff.isExpired()) {
            return defaultValue;
        }

        if (buff.category() == category) {
            return buff.value();
        }

        return defaultValue;
    }

    public boolean hasBuff(String guildId, BuffCategory category) {
        Objects.requireNonNull(guildId, "Guild ID cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");

        Optional<ActiveBuff> buffOpt = buffRepository.findActiveByGuildId(guildId);
        if (buffOpt.isEmpty()) {
            return false;
        }

        ActiveBuff buff = buffOpt.get();
        return !buff.isExpired() && buff.category() == category;
    }

    public void cleanupExpiredBuffs() {
        buffRepository.deleteExpired();
    }
}
