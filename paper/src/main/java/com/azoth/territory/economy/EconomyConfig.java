package com.azoth.territory.economy;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/** Loads the {@code economy:} block from config.yml. */
public record EconomyConfig(
        Mode mode,
        String mintCurrency,
        String mintClientBinding,
        int mintScale
) {

    public enum Mode {
        SIMULATION,
        MINT
    }

    public EconomyConfig(Mode mode) {
        this(mode, "coins", "", 2);
    }

    public EconomyConfig {
        this.mode = mode == null ? Mode.SIMULATION : mode;
        this.mintCurrency = mintCurrency == null ? "coins" : mintCurrency;
        this.mintClientBinding = mintClientBinding == null ? "" : mintClientBinding;
        this.mintScale = mintScale < 0 ? 2 : mintScale;
    }

    public static EconomyConfig fromBukkit(FileConfiguration cfg) {
        String raw = cfg == null ? null : cfg.getString("economy.mode", "SIMULATION");
        Mode mode = Mode.SIMULATION;
        if (raw != null) {
            try {
                mode = Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                mode = Mode.SIMULATION;
            }
        }
        if (cfg == null) {
            return new EconomyConfig(mode);
        }
        return new EconomyConfig(
                mode,
                cfg.getString("economy.mint.currency", "coins"),
                cfg.getString("economy.mint.client-binding", ""),
                cfg.getInt("economy.mint.scale", 2)
        );
    }
}
