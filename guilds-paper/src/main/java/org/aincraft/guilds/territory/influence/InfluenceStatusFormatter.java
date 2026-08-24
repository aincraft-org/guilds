package org.aincraft.guilds.territory.influence;

import org.aincraft.guilds.territory.model.Territory;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Formats a player's current territory influence status without performing I/O. */
public final class InfluenceStatusFormatter {

    /**
     * Formats the supplied snapshot. The optional territory is empty when the
     * player is outside every registered territory; the optional state is empty
     * when the influence engine has no snapshot for the territory.
     */
    public Component format(
            Optional<Territory> territory,
            Optional<TerritoryInfluenceState> state,
            InfluenceEngine engine,
            long nowEpochMs
    ) {
        Objects.requireNonNull(territory, "territory");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(engine, "engine");

        if (territory.isEmpty()) {
            return Component.text("No territory");
        }

        Territory current = territory.get();
        if (state.isEmpty()) {
            return Component.text(current.name() + " — no active influence");
        }

        TerritoryInfluenceState snapshot = state.get();
        if (!isActive(snapshot, engine, nowEpochMs)) {
            return Component.text(current.name() + " — no active influence");
        }

        List<InfluenceBar> bars = new ArrayList<>(snapshot.bars());
        bars.sort(Comparator.comparingDouble(InfluenceBar::value)
                .reversed()
                .thenComparing(InfluenceBar::guildId));

        StringBuilder rendered = new StringBuilder("Territory: ").append(current.name())
                .append(" | Owner: ").append(displayGuild(snapshot.ownerGuildId()));
        if (!bars.isEmpty()) {
            InfluenceBar leader = bars.get(0);
            rendered.append(" | Top attacker: ").append(leader.guildId())
                    .append(" (").append(percent(leader.value(), engine.cap())).append("%)")
                    .append(" | Bars: ");
            for (int i = 0; i < bars.size(); i++) {
                if (i > 0) {
                    rendered.append(", ");
                }
                InfluenceBar bar = bars.get(i);
                rendered.append(bar.guildId()).append('=')
                        .append(percent(bar.value(), engine.cap())).append('%');
                if (engine.isDeclarable(current.id(), bar.guildId(), nowEpochMs)) {
                    rendered.append(" [DECLARABLE]");
                }
            }
        }

        Declaration declaration = snapshot.declaration();
        if (declaration != null) {
            rendered.append(" | Declaration by ").append(declaration.guildId())
                    .append(" — flips in ")
                    .append(formatDuration(declaration.flipAtEpochMs() - nowEpochMs));
        }

        boolean cooldownActive = snapshot.cooldownUntilEpochMs() > nowEpochMs
                || engine.isCooldownActive(current.id(), nowEpochMs);
        if (cooldownActive) {
            rendered.append(" | Cooldown — ")
                    .append(formatDuration(snapshot.cooldownUntilEpochMs() - nowEpochMs))
                    .append(" remaining");
        }
        return Component.text(rendered.toString());
    }

    /** Returns whether a snapshot should be shown in the action bar. */
    public boolean isActive(TerritoryInfluenceState state, InfluenceEngine engine, long nowEpochMs) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(engine, "engine");
        return !state.bars().isEmpty()
                || state.declaration() != null
                || state.cooldownUntilEpochMs() > nowEpochMs
                || engine.isCooldownActive(state.territoryId(), nowEpochMs);
    }

    private static String displayGuild(String guildId) {
        return guildId == null || guildId.isBlank() ? "none" : guildId;
    }

    private static long percent(double value, double cap) {
        if (!Double.isFinite(value) || !Double.isFinite(cap) || cap <= 0.0) {
            return 0L;
        }
        return Math.round(Math.max(0.0, Math.min(100.0, value / cap * 100.0)));
    }

    private static String formatDuration(long millis) {
        long safeMillis = Math.max(0L, millis);
        long seconds = safeMillis / 1_000L;
        if (safeMillis % 1_000L != 0 && seconds < Long.MAX_VALUE) {
            seconds++;
        }
        long hours = seconds / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (remainingSeconds > 0 ? " " + remainingSeconds + "s" : "");
        }
        return Math.max(1L, remainingSeconds) + "s";
    }
}
