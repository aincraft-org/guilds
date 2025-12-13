package org.aincraft.towny.models;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a town in the Towny system
 */
public class Town {

    private String id;
    private String name;
    private UUID mayorUuid;
    private Set<UUID> residents;
    private Set<UUID> assistants;
    private double balance;
    private TownBlock homeBlock;
    private Location spawnLocation;
    private Map<String, Double> taxRates;
    private boolean isOpen;
    private LocalDateTime createdAt;
    private Map<String, Boolean> permissions;

    // Town level system (composition)
    private TownLevelData levelData;

    // Town toggle system (composition)
    private TownToggles toggles;

    /**
     * Default constructor for database mapping
     */
    public Town() {
        this.residents = new HashSet<>();
        this.assistants = new HashSet<>();
        this.taxRates = new HashMap<>();
        this.permissions = new HashMap<>();
        this.balance = 0.0;
        this.isOpen = true;
        this.createdAt = LocalDateTime.now();

        // Initialize composition objects
        this.levelData = new TownLevelData();
        this.toggles = new TownToggles();
    }

    /**
     * Constructor for creating a new town
     * @param name Town name
     * @param mayorUuid Mayor's UUID
     */
    public Town(String name, UUID mayorUuid) {
        this();
        this.name = name;
        this.id = UUID.randomUUID().toString();
        this.mayorUuid = mayorUuid;
        // Mayor is automatically a resident
        this.residents.add(mayorUuid);

        // Set default tax rates
        this.taxRates.put("resident", 0.0);
        this.taxRates.put("plot", 0.0);
        this.taxRates.put("shop", 0.0);

        // Set default town permissions
        setDefaultPermissions();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getMayorUuid() {
        return mayorUuid;
    }

    public void setMayorUuid(UUID mayorUuid) {
        this.mayorUuid = mayorUuid;
    }

    public Set<UUID> getResidents() {
        return residents;
    }

    public void setResidents(Set<UUID> residents) {
        this.residents = residents != null ? residents : new HashSet<>();
    }

    public Set<UUID> getAssistants() {
        return assistants;
    }

    public void setAssistants(Set<UUID> assistants) {
        this.assistants = assistants != null ? assistants : new HashSet<>();
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public TownBlock getHomeBlock() {
        return homeBlock;
    }

    public void setHomeBlock(TownBlock homeBlock) {
        this.homeBlock = homeBlock;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public Map<String, Double> getTaxRates() {
        return taxRates;
    }

    public void setTaxRates(Map<String, Double> taxRates) {
        this.taxRates = taxRates != null ? taxRates : new HashMap<>();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, Boolean> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, Boolean> permissions) {
        this.permissions = permissions != null ? permissions : new HashMap<>();
    }

    // Town level system getters and setters (delegating to TownLevelData)
    public TownLevelData getLevelData() {
        return levelData;
    }

    public void setLevelData(TownLevelData levelData) {
        this.levelData = levelData != null ? levelData : new TownLevelData();
    }

    public int getTownLevel() {
        return levelData.getLevel();
    }

    public void setTownLevel(int townLevel) {
        levelData.setLevel(townLevel);
    }

    public int getTechPoints() {
        return levelData.getTechPoints();
    }

    public void setTechPoints(int techPoints) {
        levelData.setTechPoints(techPoints);
    }

    public Map<String, Integer> getUpgradeProgress() {
        return levelData.getUpgradeProgress();
    }

    public void setUpgradeProgress(Map<String, Integer> upgradeProgress) {
        levelData.setUpgradeProgress(upgradeProgress);
    }

    // Town toggle system getters and setters (delegating to TownToggles)
    public TownToggles getToggles() {
        return toggles;
    }

    public void setToggles(TownToggles toggles) {
        this.toggles = toggles != null ? toggles : new TownToggles();
    }

    public boolean isPvpEnabled() {
        return toggles.isPvpEnabled();
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        toggles.setPvpEnabled(pvpEnabled);
        // Update permissions map for backward compatibility
        this.permissions.put("pvp", pvpEnabled);
    }

    public boolean isFireEnabled() {
        return toggles.isFireEnabled();
    }

    public void setFireEnabled(boolean fireEnabled) {
        toggles.setFireEnabled(fireEnabled);
        // Update permissions map for backward compatibility
        this.permissions.put("fire", fireEnabled);
    }

    public boolean isExplosionsEnabled() {
        return toggles.isExplosionsEnabled();
    }

    public void setExplosionsEnabled(boolean explosionsEnabled) {
        toggles.setExplosionsEnabled(explosionsEnabled);
        // Update permissions map for backward compatibility
        this.permissions.put("explosions", explosionsEnabled);
    }

    public boolean isMobsEnabled() {
        return toggles.isMobsEnabled();
    }

    public void setMobsEnabled(boolean mobsEnabled) {
        toggles.setMobsEnabled(mobsEnabled);
        // Update permissions map for backward compatibility
        this.permissions.put("mobs", mobsEnabled);
    }

    public boolean isPublicEnabled() {
        return toggles.isPublicEnabled();
    }

    public void setPublicEnabled(boolean publicEnabled) {
        toggles.setPublicEnabled(publicEnabled);
        // Update permissions map for backward compatibility
        this.permissions.put("public", publicEnabled);
    }

    // Business methods

    /**
     * Add a resident to the town
     * @param residentUuid Resident UUID
     * @return True if added successfully
     */
    public boolean addResident(UUID residentUuid) {
        return residents.add(residentUuid);
    }

    /**
     * Remove a resident from the town
     * @param residentUuid Resident UUID
     * @return True if removed successfully
     */
    public boolean removeResident(UUID residentUuid) {
        // Remove from residents
        boolean removed = residents.remove(residentUuid);

        // Also remove from assistants if they were an assistant
        if (removed) {
            assistants.remove(residentUuid);
        }

        return removed;
    }

    /**
     * Check if a player is a resident of this town
     * @param residentUuid Resident UUID
     * @return True if is resident
     */
    public boolean isResident(UUID residentUuid) {
        return residents.contains(residentUuid);
    }

    /**
     * Check if a player is the mayor of this town
     * @param residentUuid Resident UUID
     * @return True if is mayor
     */
    public boolean isMayor(UUID residentUuid) {
        return mayorUuid.equals(residentUuid);
    }

    /**
     * Check if a player is an assistant in this town
     * @param residentUuid Resident UUID
     * @return True if is assistant
     */
    public boolean isAssistant(UUID residentUuid) {
        return assistants.contains(residentUuid);
    }

    /**
     * Add a town assistant
     * @param assistantUuid Assistant UUID
     * @return True if added successfully
     */
    public boolean addAssistant(UUID assistantUuid) {
        // Must be a resident first
        if (!isResident(assistantUuid)) {
            return false;
        }
        return assistants.add(assistantUuid);
    }

    /**
     * Remove a town assistant
     * @param assistantUuid Assistant UUID
     * @return True if removed successfully
     */
    public boolean removeAssistant(UUID assistantUuid) {
        return assistants.remove(assistantUuid);
    }

    /**
     * Get the number of residents
     * @return Resident count
     */
    public int getResidentCount() {
        return residents.size();
    }

    /**
     * Add funds to town balance
     * @param amount Amount to add
     * @return New balance
     */
    public double addFunds(double amount) {
        this.balance += amount;
        return this.balance;
    }

    /**
     * Remove funds from town balance
     * @param amount Amount to remove
     * @return True if successful, false if insufficient funds
     */
    public boolean withdrawFunds(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Get tax rate for specific type
     * @param type Tax type (resident, plot, shop, etc.)
     * @return Tax rate
     */
    public double getTaxRate(String type) {
        return taxRates.getOrDefault(type, 0.0);
    }

    /**
     * Set tax rate for specific type
     * @param type Tax type
     * @param rate Tax rate
     */
    public void setTaxRate(String type, double rate) {
        taxRates.put(type, Math.max(0.0, rate)); // Ensure non-negative
    }

    /**
     * Check if town has a specific permission
     * @param permission Permission node
     * @return True if has permission
     */
    public boolean hasPermission(String permission) {
        return permissions.getOrDefault(permission, false);
    }

    /**
     * Set a permission for this town
     * @param permission Permission node
     * @param value Permission value
     */
    public void setPermission(String permission, boolean value) {
        permissions.put(permission, value);
    }

    /**
     * Set default permissions for a new town
     */
    private void setDefaultPermissions() {
        permissions.put("pvp", false);
        permissions.put("fire", false);
        permissions.put("explosions", false);
        permissions.put("mobs", true);
        permissions.put("public", false);
    }

    // Town toggle system convenience methods (delegating to TownToggles)

    /**
     * Toggle PvP setting for the town
     * @return New PvP state after toggle
     */
    public boolean togglePvp() {
        boolean newState = toggles.togglePvp();
        permissions.put("pvp", newState); // Update backward compatibility map
        return newState;
    }

    /**
     * Toggle fire setting for the town
     * @return New fire state after toggle
     */
    public boolean toggleFire() {
        boolean newState = toggles.toggleFire();
        permissions.put("fire", newState);
        return newState;
    }

    /**
     * Toggle explosions setting for the town
     * @return New explosions state after toggle
     */
    public boolean toggleExplosions() {
        boolean newState = toggles.toggleExplosions();
        permissions.put("explosions", newState);
        return newState;
    }

    /**
     * Toggle mobs setting for the town
     * @return New mobs state after toggle
     */
    public boolean toggleMobs() {
        boolean newState = toggles.toggleMobs();
        permissions.put("mobs", newState);
        return newState;
    }

    /**
     * Toggle public setting for the town
     * @return New public state after toggle
     */
    public boolean togglePublic() {
        boolean newState = toggles.togglePublic();
        permissions.put("public", newState);
        return newState;
    }

    /**
     * Get all town toggle states
     * @return Map of toggle names to their current states
     */
    public Map<String, Boolean> getAllToggles() {
        return toggles.getAllToggles();
    }

    /**
     * Set a specific toggle state
     * @param toggleType The toggle type to set
     * @param value The new value for the toggle
     * @return True if toggle was set successfully, false if toggle type not found
     */
    public boolean setToggle(String toggleType, boolean value) {
        boolean success = toggles.setToggle(toggleType, value);
        if (success) {
            permissions.put(toggleType.toLowerCase(), value); // Update backward compatibility map
        }
        return success;
    }

    /**
     * Get the current state of a specific toggle
     * @param toggleType The toggle type to get
     * @return The toggle state, or false if toggle type not found
     */
    public boolean getToggle(String toggleType) {
        return toggles.getToggle(toggleType);
    }

    /**
     * Check if town is bankrupt (balance below 0)
     * @return True if bankrupt
     */
    public boolean isBankrupt() {
        return balance < 0;
    }

    // Town level system business methods (delegating to TownLevelData)

    /**
     * Add tech points to the town
     * @param points Tech points to add
     */
    public void addTechPoints(int points) {
        levelData.addTechPoints(points);
    }

    /**
     * Check if town can afford an upgrade to the next level
     * @param nextLevelRequirements Resource requirements for next level
     * @return Map of resource affordability
     */
    public Map<String, Boolean> canAffordUpgrade(Map<String, Integer> nextLevelRequirements) {
        return levelData.canAffordUpgrade(nextLevelRequirements);
    }

    /**
     * Check if all requirements for the next level are met
     * @param nextLevelRequirements Resource requirements for next level
     * @return True if all requirements are met
     */
    public boolean canUpgradeToNextLevel(Map<String, Integer> nextLevelRequirements) {
        return levelData.canUpgradeToNextLevel(nextLevelRequirements);
    }

    /**
     * Contribute resources to town upgrade progress
     * @param resourceType Type of resource (diamond, gold, iron, emerald, experience)
     * @param amount Amount to contribute
     */
    public void contributeToUpgrade(String resourceType, int amount) {
        levelData.contributeToUpgrade(resourceType, amount);
    }

    /**
     * Get the contribution progress for a specific resource
     * @param resourceType Type of resource
     * @param requiredAmount Required amount for next level
     * @return Progress percentage (0-100)
     */
    public double getResourceProgress(String resourceType, int requiredAmount) {
        return levelData.getResourceProgress(resourceType, requiredAmount);
    }

    /**
     * Calculate overall upgrade progress percentage
     * @param nextLevelRequirements Resource requirements for next level
     * @return Overall progress percentage (0-100)
     */
    public double getOverallUpgradeProgress(Map<String, Integer> nextLevelRequirements) {
        return levelData.getOverallUpgradeProgress(nextLevelRequirements);
    }

    /**
     * Reset upgrade progress for next level preparation
     */
    public void resetUpgradeProgress() {
        levelData.resetUpgradeProgress();
    }

    /**
     * Level up the town to the next level
     * @param newLevel New level to set
     * @param techPointsReward Tech points to add for this level
     */
    public void levelUp(int newLevel, int techPointsReward) {
        levelData.levelUp(newLevel, techPointsReward);
    }

    /**
     * Get the maximum number of assistant slots based on town level
     * @return Maximum assistant slots
     */
    public int getMaxAssistantSlots() {
        return levelData.getMaxAssistantSlots();
    }

    /**
     * Check if the town has reached its assistant limit
     * @return True if at assistant limit
     */
    public boolean isAtAssistantLimit() {
        return levelData.isAtAssistantLimit(assistants.size());
    }

    /**
     * Get the maximum claim limit based on town level
     * @return Maximum claim limit in chunks
     */
    public int getMaxClaimLimit() {
        return levelData.getMaxClaimLimit();
    }

    /**
     * Get daily income bonus based on town level
     * @return Daily income bonus
     */
    public double getDailyIncomeBonus() {
        return levelData.getDailyIncomeBonus();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Town town = (Town) obj;
        return id.equals(town.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Town{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", mayorUuid=" + mayorUuid +
                ", residentCount=" + getResidentCount() +
                ", balance=" + balance +
                ", townLevel=" + getTownLevel() +
                ", techPoints=" + getTechPoints() +
                ", isOpen=" + isOpen +
                '}';
    }
}