package dev.mintychochip.territory.influence;

import dev.mintychochip.territory.model.LookupResult;
import dev.mintychochip.territory.model.Territory;
import dev.mintychochip.territory.registry.TerritoryRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/** Sends active influence status to online players from one repeating server task. */
public final class InfluenceStatusTask implements Runnable {
    private final TerritoryRegistry registry;
    private final InfluenceEngine engine;
    private final InfluenceStatusFormatter formatter;
    private final LongSupplier clock;

    public InfluenceStatusTask(
            TerritoryRegistry registry,
            InfluenceEngine engine,
            InfluenceStatusFormatter formatter,
            LongSupplier clock
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public InfluenceStatusTask(
            TerritoryRegistry registry,
            InfluenceEngine engine,
            InfluenceStatusFormatter formatter
    ) {
        this(registry, engine, formatter, System::currentTimeMillis);
    }

    @Override
    public void run() {
        long nowEpochMs = clock.getAsLong();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            LookupResult lookup = registry.resolve(
                    player.getWorld().getName(),
                    location.getBlockX(),
                    location.getBlockZ());
            Optional<Territory> territory = lookup.territory();
            if (territory.isEmpty()) {
                continue;
            }
            Optional<TerritoryInfluenceState> state = engine.influence(territory.get().id());
            if (state.isEmpty() || !formatter.isActive(state.get(), engine, nowEpochMs)) {
                continue;
            }
            Component status = formatter.format(territory, state, engine, nowEpochMs);
            player.sendActionBar(status);
        }
    }
}
