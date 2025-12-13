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

    /**
     * Check if town is bankrupt (balance below 0)
     * @return True if bankrupt
     */
    public boolean isBankrupt() {
        return balance < 0;
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
                ", isOpen=" + isOpen +
                '}';
    }
}