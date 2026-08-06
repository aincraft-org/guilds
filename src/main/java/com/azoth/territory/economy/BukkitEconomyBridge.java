package com.azoth.territory.economy;

import org.bukkit.OfflinePlayer;

import java.util.Objects;

/** Bukkit-friendly facade delegating OfflinePlayer transactions to UUID domain APIs. */
public final class BukkitEconomyBridge {

    private final EconomyBridge delegate;

    public BukkitEconomyBridge(EconomyBridge delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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

}
