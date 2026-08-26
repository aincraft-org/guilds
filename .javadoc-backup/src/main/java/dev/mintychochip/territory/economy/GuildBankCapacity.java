package dev.mintychochip.territory.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Canonical guild-bank capacity calculation. */
public record GuildBankCapacity(BigDecimal perLevel, int scale) {

    public GuildBankCapacity() {
        this(new BigDecimal("1000.00"), 2);
    }

    public GuildBankCapacity {
        if (perLevel == null || perLevel.signum() < 0) throw new IllegalArgumentException("perLevel must be non-negative");
        if (scale < 0) throw new IllegalArgumentException("scale must be non-negative");
    }

    public BigDecimal forLevel(int guildLevel) {
        return perLevel.multiply(BigDecimal.valueOf(Math.max(0, guildLevel)))
                .setScale(scale, RoundingMode.HALF_UP);
    }
}
