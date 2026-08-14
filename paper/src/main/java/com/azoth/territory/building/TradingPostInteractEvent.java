package com.azoth.territory.building;

import com.azoth.territory.model.SettlementFacility;
import com.azoth.territory.model.Territory;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/** Cancellable integration point for validated active trading-post interactions. */
public final class TradingPostInteractEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final SettlementFacility facility;
    private final Territory territory;
    private boolean cancelled;

    public TradingPostInteractEvent(Player player, SettlementFacility facility, Territory territory) {
        this.player = Objects.requireNonNull(player, "player");
        this.facility = Objects.requireNonNull(facility, "facility");
        this.territory = Objects.requireNonNull(territory, "territory");
    }

    public Player player() { return player; }
    public SettlementFacility facility() { return facility; }
    public Territory territory() { return territory; }
    public Optional<String> governingGuildId() { return territory.governedByGuildId(); }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
