package dev.mintychochip.territory.influence;

import org.bukkit.configuration.file.FileConfiguration;

/** Loads the {@code influence:} block from config.yml (spec §12). */
public final class InfluenceConfigLoader {

    private InfluenceConfigLoader() {
    }

    public static InfluenceConfig fromBukkit(FileConfiguration cfg) {
        InfluenceConfig defaults = InfluenceConfig.defaults();
        if (cfg == null) {
            return defaults;
        }
        try {
            return new InfluenceConfig(
                    cfg.getBoolean("influence.enabled", defaults.enabled()),
                    cfg.getDouble("influence.cap", defaults.cap()),
                    cfg.getDouble("influence.values.pvp-kill", defaults.pvpKill()),
                    cfg.getDouble("influence.values.pve-kill", defaults.pveKill()),
                    cfg.getDouble("influence.values.block-break", defaults.blockBreak()),
                    cfg.getDouble("influence.values.block-place", defaults.blockPlace()),
                    cfg.getDouble("influence.values.craft", defaults.craft()),
                    cfg.getDouble("influence.defender-multiplier", defaults.defenderMultiplier()),
                    cfg.getLong("influence.declare-countdown-hours", defaults.declareCountdownHours()),
                    cfg.getLong("influence.post-flip-cooldown-days", defaults.postFlipCooldownDays()),
                    cfg.getLong("influence.flush-seconds", defaults.flushSeconds())
            );
        } catch (IllegalArgumentException e) {
            return defaults;
        }
    }
}
