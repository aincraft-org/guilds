package org.aincraft.guilds.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Boundary converter for legacy {@code §}-coded player messages. Callers send
 * the returned {@link Component} instead of raw strings so every audience
 * receives native Adventure text; the coded literals themselves stay as the
 * single source of truth for color/decoration.
 */
public final class LegacyText {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private LegacyText() {
    }

    public static Component of(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(legacy);
    }
}
