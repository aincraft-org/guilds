package org.aincraft.claim;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.claim.events.PlayerEnterClaimEvent;
import org.aincraft.subregion.SubregionTypeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Objects;

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
     * Format: "Entering GuildName" or "Entering Wilderness"
     */
    private Component buildOwnershipChangeMessage(ClaimState newState, ClaimState previousState) {
        if (newState.guildId() == null) {
            // Exiting to wilderness
            return Component.text("~Wilderness", NamedTextColor.GREEN);
        } else {
            // Entering guild territory - use guild color if set
            TextColor guildNameColor = getGuildColor(newState.guildColor());
            return Component.text("~" + newState.displayName(), guildNameColor);
        }
    }

    /**
     * Builds message for type change only.
     * Format: "~Guild" (when exiting subregion) or "[Type]" (when entering subregion)
     */
    private Component buildTypeChangeMessage(ClaimState newState) {
        if (newState.subregionType() == null) {
            // Exiting subregion - show the guild name
            TextColor guildColor = getGuildColor(newState.guildColor());
            return Component.text("~" + newState.displayName(), guildColor);
        }

        // Entering subregion - show the type
        String typeName = typeRegistry.getType(newState.subregionType())
                .map(t -> t.getDisplayName())
                .orElse(newState.subregionType());

        return Component.text("[" + typeName + "]", NamedTextColor.AQUA);
    }

    /**
     * Builds message for both ownership and type changes.
     * Format: "~Guild [Type]" or just "~Wilderness" when exiting
     */
    private Component buildDualChangeMessage(ClaimState newState, ClaimState previousState) {
        // If exiting to wilderness, just show wilderness without arrow
        if (newState.guildId() == null) {
            Component result = Component.text("~Wilderness", NamedTextColor.GREEN);
            if (newState.subregionType() != null) {
                String typeName = typeRegistry.getType(newState.subregionType())
                        .map(t -> t.getDisplayName())
                        .orElse(newState.subregionType());
                result = result.append(Component.text(" [", NamedTextColor.GRAY));
                result = result.append(Component.text(typeName, NamedTextColor.AQUA));
                result = result.append(Component.text("]", NamedTextColor.GRAY));
            }
            return result;
        }

        // Entering a guild with type change
        TextColor newColor = getGuildColor(newState.guildColor());
        Component result = Component.text("~" + newState.displayName(), newColor);

        // Add type if present
        if (newState.subregionType() != null) {
            String typeName = typeRegistry.getType(newState.subregionType())
                    .map(t -> t.getDisplayName())
                    .orElse(newState.subregionType());
            result = result.append(Component.text(" [", NamedTextColor.GRAY));
            result = result.append(Component.text(typeName, NamedTextColor.AQUA));
            result = result.append(Component.text("]", NamedTextColor.GRAY));
        }

        return result;
    }

    /**
     * Converts a guild color (hex format) to a TextColor for display.
     * Falls back to GOLD if no color is set.
     *
     * @param hexColor the hex color (#RRGGBB) or null
     * @return a TextColor to use for display
     */
    private TextColor getGuildColor(String hexColor) {
        if (hexColor != null && hexColor.startsWith("#") && hexColor.length() == 7) {
            try {
                return TextColor.fromHexString(hexColor);
            } catch (IllegalArgumentException e) {
                // Fall through to default
            }
        }
        // Default to GOLD if no color set
        return NamedTextColor.GOLD;
    }
}
