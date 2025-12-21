package org.aincraft.claim;

import com.google.inject.Inject;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Displays action bar notifications when players enter/exit guild claims.
 * Shows ownership changes, type changes, and transitions between guilds/wilderness.
 */
public class ClaimEntryNotifier implements Listener {
    private final SubregionTypeRegistry typeRegistry;

    @Inject
    public ClaimEntryNotifier(SubregionTypeRegistry typeRegistry) {
        this.typeRegistry = Objects.requireNonNull(typeRegistry, "Type registry cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClaimEnter(PlayerEnterClaimEvent event) {
        Player player = event.getPlayer();
        ClaimState newState = event.getNewState();
        ClaimState previousState = event.getPreviousState();

        // Build message based on transition type
        Component message = buildMessage(newState, previousState);
        if (message != null) {
            player.sendActionBar(message);
        }
    }

    /**
     * Constructs message based on ownership/type change.
     * Returns null if no meaningful change detected.
     */
    private Component buildMessage(ClaimState newState, ClaimState previousState) {
        boolean ownershipChanged = newState.ownershipChangedFrom(previousState);
        boolean typeChanged = newState.typeChangedFrom(previousState);

        if (!ownershipChanged && !typeChanged) {
            return null; // No relevant change
        }

        // Both ownership and type changed
        if (ownershipChanged && typeChanged) {
            return buildDualChangeMessage(newState, previousState);
        }

        // Only ownership changed
        if (ownershipChanged) {
            return buildOwnershipChangeMessage(newState, previousState);
        }

        // Only type changed (entered/exited subregion of same guild)
        return buildTypeChangeMessage(newState);
    }

    /**
     * Builds message for ownership change only.
     * Format: "GuildName - RegionName - RegionType" or "Wilderness"
     */
    private Component buildOwnershipChangeMessage(ClaimState newState, ClaimState previousState) {
        if (newState.guildId() == null) {
            // Exiting to wilderness
            return Component.text("Wilderness", NamedTextColor.GREEN);
        } else {
            // Entering guild territory
            TextColor guildNameColor = getGuildColor(newState.guildColor());
            Component result = Component.text(newState.displayName(), guildNameColor);

            // Add region type if present
            if (newState.subregionType() != null) {
                String typeName = typeRegistry.getType(newState.subregionType())
                        .map(t -> t.getDisplayName())
                        .orElse(newState.subregionType());
                result = result.append(Component.text(" - ", NamedTextColor.GRAY));
                result = result.append(Component.text(typeName, NamedTextColor.GRAY));
            }

            return result;
        }
    }

    /**
     * Builds message for type change only.
     * Format: "GuildName - RegionName - RegionType" or "GuildName - RegionName" when exiting
     */
    private Component buildTypeChangeMessage(ClaimState newState) {
        TextColor guildColor = getGuildColor(newState.guildColor());
        Component result = Component.text(newState.displayName(), guildColor);

        // Add region type if present and not null
        if (newState.subregionType() != null) {
            String typeName = typeRegistry.getType(newState.subregionType())
                    .map(t -> t.getDisplayName())
                    .orElse(newState.subregionType());
            result = result.append(Component.text(" - ", NamedTextColor.GRAY));
            result = result.append(Component.text(typeName, NamedTextColor.GRAY));
        }

        return result;
    }

    /**
     * Builds message for both ownership and type changes.
     * Format: "GuildName - RegionName - RegionType" or "Wilderness" when exiting
     */
    private Component buildDualChangeMessage(ClaimState newState, ClaimState previousState) {
        // If exiting to wilderness
        if (newState.guildId() == null) {
            return Component.text("Wilderness", NamedTextColor.GREEN);
        }

        // Entering a guild
        TextColor newColor = getGuildColor(newState.guildColor());
        Component result = Component.text(newState.displayName(), newColor);

        // Add region type if present
        if (newState.subregionType() != null) {
            String typeName = typeRegistry.getType(newState.subregionType())
                    .map(t -> t.getDisplayName())
                    .orElse(newState.subregionType());
            result = result.append(Component.text(" - ", NamedTextColor.GRAY));
            result = result.append(Component.text(typeName, NamedTextColor.GRAY));
        }

        return result;
    }

    /**
     * Converts a guild color (hex or named color) to a TextColor for display.
     * Falls back to GOLD if no color is set or invalid.
     *
     * @param color the color string (hex #RRGGBB or named color like "red", "blue")
     * @return a TextColor to use for display
     */
    private TextColor getGuildColor(String color) {
        if (color == null) {
            return NamedTextColor.GOLD;
        }

        // Check if hex format
        if (color.startsWith("#") && color.length() == 7) {
            try {
                return TextColor.fromHexString(color);
            } catch (IllegalArgumentException e) {
                // Fall through to named color check
            }
        }

        // Check if named color
        TextColor namedColor = NamedTextColor.NAMES.value(color.toLowerCase());
        if (namedColor != null) {
            return namedColor;
        }

        // Default to GOLD if invalid
        return NamedTextColor.GOLD;
    }
}
