package org.aincraft.storage;

import org.aincraft.vault.Vault;
import org.aincraft.vault.VaultRepository;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of VaultRepository for testing.
 * Uses ConcurrentHashMap for thread-safety in concurrent tests.
 */
public class InMemoryVaultRepository implements VaultRepository {
    private final Map<String, Vault> vaultsById = new ConcurrentHashMap<>();
    private final Map<String, Vault> vaultsByGuildId = new ConcurrentHashMap<>();
    private final Map<String, Vault> vaultsByLocation = new ConcurrentHashMap<>();

    @Override
    public void save(Vault vault) {
        vaultsById.put(vault.getId(), vault);
        vaultsByGuildId.put(vault.getGuildId(), vault);
        vaultsByLocation.put(locationKey(vault), vault);
    }

    @Override
    public void delete(String vaultId) {
        Vault vault = vaultsById.remove(vaultId);
        if (vault != null) {
            vaultsByGuildId.remove(vault.getGuildId());
            vaultsByLocation.remove(locationKey(vault));
        }
    }

    @Override
    public Optional<Vault> findById(String vaultId) {
        return Optional.ofNullable(vaultsById.get(vaultId));
    }

    @Override
    public Optional<Vault> findByGuildId(String guildId) {
        return Optional.ofNullable(vaultsByGuildId.get(guildId));
    }

    @Override
    public Optional<Vault> findByLocation(String world, int x, int y, int z) {
        return Optional.ofNullable(vaultsByLocation.get(locationKey(world, x, y, z)));
    }

    @Override
    public void updateContents(String vaultId, ItemStack[] contents) {
        Vault vault = vaultsById.get(vaultId);
        if (vault != null) {
            vault.setContents(contents);
        }
    }

    @Override
    public boolean existsForGuild(String guildId) {
        return vaultsByGuildId.containsKey(guildId);
    }

    @Override
    public ItemStack[] getFreshContents(String vaultId) {
        Vault vault = vaultsById.get(vaultId);
        if (vault == null) {
            return null;
        }
        // Return a copy to simulate fresh DB read
        ItemStack[] original = vault.getContents();
        if (original == null) {
            return new ItemStack[Vault.STORAGE_SIZE];
        }
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] != null ? original[i].clone() : null;
        }
        return copy;
    }

    private String locationKey(Vault vault) {
        return locationKey(vault.getWorld(), vault.getOriginX(), vault.getOriginY(), vault.getOriginZ());
    }

    private String locationKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public void clear() {
        vaultsById.clear();
        vaultsByGuildId.clear();
        vaultsByLocation.clear();
    }

    public int size() {
        return vaultsById.size();
    }
}
