package org.aincraft.guilds.territory.economy;

import org.aincraft.guilds.territory.model.SettlementFacility;
import org.aincraft.guilds.territory.registry.FacilityRegistry;
import org.bukkit.OfflinePlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import org.aincraft.guilds.territory.economy.AsyncTaxSettlement;

/** Bukkit-friendly facade delegating OfflinePlayer transactions to UUID domain APIs. */
public final class BukkitEconomyBridge {

    private final EconomyBridge delegate;
    private final FacilityRegistry facilities;

    public BukkitEconomyBridge(EconomyBridge delegate) {
        this(delegate, null);
    }

    public BukkitEconomyBridge(EconomyBridge delegate, FacilityRegistry facilities) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.facilities = facilities;
    }

    public TaxReport reportSale(
            OfflinePlayer payer,
            String worldId,
            int blockX,
            int blockZ,
            String goodId,
            double grossAmount
    ) {
        return delegate.reportSale(
                payer == null ? null : payer.getUniqueId(),
                worldId,
                blockX,
                blockZ,
                goodId,
                grossAmount);
    }
    public CompletionStage<TaxReport> reportSaleAsync(OfflinePlayer payer, String worldId, int blockX, int blockZ,
                                                        String goodId, double grossAmount, String eventKey,
                                                        AsyncTaxSettlement settlement) {
        return delegate.reportSaleAsync(payer == null ? null : payer.getUniqueId(), worldId, blockX, blockZ,
                goodId, grossAmount, eventKey, settlement);
    }

    public CompletionStage<TaxReport> reportCraftAsync(OfflinePlayer payer, String worldId, int blockX, int blockZ,
                                                        String outputGoodId, int outputQuantity, double grossValue,
                                                        String eventKey, AsyncTaxSettlement settlement) {
        return delegate.reportCraftAsync(payer == null ? null : payer.getUniqueId(), worldId, blockX, blockZ,
                outputGoodId, outputQuantity, grossValue, eventKey, settlement);
    }
    public TaxReport reportCraft(
            OfflinePlayer payer,
            String worldId,
            int blockX,
            int blockZ,
            String outputGoodId,
            int outputQuantity,
            double grossValue
    ) {
        return delegate.reportCraft(
                payer == null ? null : payer.getUniqueId(),
                worldId,
                blockX,
                blockZ,
                outputGoodId,
                outputQuantity,
                grossValue);
    }
    public Optional<SettlementFacility> resolveFacility(String worldId, int x, int y, int z) {
        return facilities == null
                ? Optional.empty()
                : facilities.resolve(worldId, x, y, z);
    }


}
