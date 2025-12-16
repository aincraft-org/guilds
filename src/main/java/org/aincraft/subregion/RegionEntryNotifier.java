package org.aincraft.subregion;

import com.google.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.aincraft.subregion.events.PlayerEnterSubregionEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

/**
 * Listens for region entry events and displays action bar notifications.
 */
public class RegionEntryNotifier implements Listener {
    private final SubregionTypeRegistry typeRegistry;

    @Inject
    public RegionEntryNotifier(SubregionTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRegionEnter(PlayerEnterSubregionEvent event) {
        Subregion region = event.getSubregion();
        String typeId = region.getType();

        // Only show notification if region has a type
        if (typeId == null) {
            return;
        }

        Optional<SubregionType> typeOpt = typeRegistry.getType(typeId);
        if (typeOpt.isEmpty()) {
            return;
        }

        SubregionType type = typeOpt.get();

        // Check if this type wants notifications
        if (!type.showEnterNotification()) {
            return;
        }

        Player player = event.getPlayer();

        // Build action bar message: "Entering [Type]: Region Name"
        Component message = Component.text("Entering ", NamedTextColor.GRAY)
                .append(Component.text("[" + type.getDisplayName() + "]", NamedTextColor.GOLD))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(region.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD));

        player.sendActionBar(message);
    }
}
