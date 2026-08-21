package org.aincraft.guilds.territory.building;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.model.Territory;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/** Cancellable integration point for a validated active storage interaction. */
public final class StorageFacilityInteractEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SettlementFacility facility;
    private final Territory territory;
    private final String guildId;
    private boolean cancelled;

    public StorageFacilityInteractEvent(Player player, SettlementFacility facility,
                                        Territory territory, String guildId) {
        this.player = Objects.requireNonNull(player, "player");
        this.facility = Objects.requireNonNull(facility, "facility");
        this.territory = Objects.requireNonNull(territory, "territory");
        this.guildId = Objects.requireNonNull(guildId, "guildId");
    }

    public Player player() { return player; }
    public SettlementFacility facility() { return facility; }
    public Territory territory() { return territory; }
    public String guildId() { return guildId; }
    public Optional<String> governingGuildId() { return Optional.of(guildId); }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
