package com.azoth.territory.economy;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/** Loads the {@code economy:} block from config.yml. */
public final class EconomyConfig {

    public enum Mode {
        VAULT,
        SIMULATION
    }

    private final Mode mode;

    public EconomyConfig(Mode mode) {
        this.mode = mode == null ? Mode.VAULT : mode;
    }

    public Mode mode() {
        return mode;
    }

    public static EconomyConfig fromBukkit(FileConfiguration cfg) {
        String raw = cfg == null ? null : cfg.getString("economy.mode", "VAULT");
        if (raw == null) {
            return new EconomyConfig(Mode.VAULT);
        }
        try {
            return new EconomyConfig(Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return new EconomyConfig(Mode.VAULT);
        }
    }
}
