package dev.mintychochip.territory.invasion;

import java.util.List;
import java.util.Objects;

public record Wave(List<MobEntry> mobs) {
    public Wave {
        Objects.requireNonNull(mobs, "mobs");
        mobs = List.copyOf(mobs);
    }
}
