package com.azoth.territory.listener;

import com.azoth.territory.model.ZoneType;

import java.util.Optional;

public final class TerritoryTransitionTitleFormatter {
    private TerritoryTransitionTitleFormatter() {
    }

    public static Title enter(Optional<String> territoryName, ZoneType zoneType) {
        String territory = territoryName.filter(value -> !value.isBlank()).orElse("Territory");
        String zone = zoneType == null ? "Wilderness" : capitalize(zoneType.name());
        return new Title("Entering " + zone, territory);
    }

    public static Title leave() {
        return new Title("Leaving Territory", "Wilderness");
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    public record Title(String title, String subtitle) {
    }
}
