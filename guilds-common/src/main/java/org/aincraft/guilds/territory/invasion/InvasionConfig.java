package org.aincraft.guilds.territory.invasion;

import java.util.List;
import java.util.Objects;

public record InvasionConfig(long blockBudget, List<Wave> waves) {
    public InvasionConfig {
        if (blockBudget <= 0) throw new IllegalArgumentException("blockBudget must be positive");
        Objects.requireNonNull(waves, "waves");
        if (waves.size() != 3) throw new IllegalArgumentException("exactly three waves are required");
        waves = List.copyOf(waves);
    }
}
