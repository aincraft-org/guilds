package org.aincraft.subregion;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for subregion types.
 * Manages built-in and custom types registered by external plugins.
 */
public class SubregionTypeRegistry {
    private final Map<String, SubregionType> types = new ConcurrentHashMap<>();
    private final Set<String> builtInIds = new HashSet<>();

    public SubregionTypeRegistry() {
        registerBuiltInTypes();
    }

    private void registerBuiltInTypes() {
        registerBuiltIn(new SimpleSubregionType("generic", "Generic", "Standard region with no special designation"));
        registerBuiltIn(new SimpleSubregionType("bank", "Bank", "Secure storage area"));
        registerBuiltIn(new SimpleSubregionType("farm", "Farm", "Agricultural production area"));
        registerBuiltIn(new SimpleSubregionType("shop", "Shop", "Commercial trading zone"));
        registerBuiltIn(new SimpleSubregionType("arena", "Arena", "Combat or competition space"));
        registerBuiltIn(new SimpleSubregionType("residence", "Residence", "Living quarters"));
        registerBuiltIn(new SimpleSubregionType("industrial", "Industrial", "Manufacturing or production facility"));
        registerBuiltIn(new SimpleSubregionType("public", "Public Area", "Community shared space"));
    }

    private void registerBuiltIn(SubregionType type) {
        types.put(type.getId(), type);
        builtInIds.add(type.getId());
    }

    /**
     * Registers a custom subregion type.
     *
     * @param type the type to register
     * @throws IllegalArgumentException if a type with the same ID already exists
     */
    public void register(SubregionType type) {
        Objects.requireNonNull(type, "Type cannot be null");
        if (types.containsKey(type.getId())) {
            throw new IllegalArgumentException("Type already registered: " + type.getId());
        }
        types.put(type.getId(), type);
    }

    /**
     * Unregisters a custom type.
     * Built-in types cannot be unregistered.
     *
     * @param typeId the type ID to unregister
     * @return true if the type was removed, false if not found or is built-in
     */
    public boolean unregister(String typeId) {
        if (typeId == null || builtInIds.contains(typeId)) {
            return false;
        }
        return types.remove(typeId) != null;
    }

    /**
     * Gets a type by ID.
     *
     * @param typeId the type ID
     * @return the type, or empty if not found
     */
    public Optional<SubregionType> getType(String typeId) {
        if (typeId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(types.get(typeId));
    }

    /**
     * Checks if a type is registered.
     *
     * @param typeId the type ID
     * @return true if registered
     */
    public boolean isRegistered(String typeId) {
        return typeId != null && types.containsKey(typeId);
    }

    /**
     * Checks if a type is a built-in type.
     *
     * @param typeId the type ID
     * @return true if built-in
     */
    public boolean isBuiltIn(String typeId) {
        return typeId != null && builtInIds.contains(typeId);
    }

    /**
     * Gets all registered types.
     *
     * @return unmodifiable collection of all types
     */
    public Collection<SubregionType> getAllTypes() {
        return Collections.unmodifiableCollection(types.values());
    }

    /**
     * Gets all type IDs.
     *
     * @return unmodifiable set of type IDs
     */
    public Set<String> getTypeIds() {
        return Collections.unmodifiableSet(types.keySet());
    }

    /**
     * Gets the number of registered types.
     *
     * @return type count
     */
    public int size() {
        return types.size();
    }
}
