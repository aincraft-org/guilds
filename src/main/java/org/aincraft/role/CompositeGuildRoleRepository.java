package org.aincraft.role;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.aincraft.GuildRole;
import org.aincraft.storage.GuildRoleRepository;

import java.util.*;

/**
 * Composite repository that merges flyweight default roles with persisted custom roles.
 * Implements the Decorator pattern to extend GuildRoleRepository functionality.
 */
@Singleton
public class CompositeGuildRoleRepository implements GuildRoleRepository {
    private final GuildRoleRepository persistedRepository;
    private final DefaultRoleRegistry defaultRoleRegistry;

    @Inject
    public CompositeGuildRoleRepository(
            @Named("persisted") GuildRoleRepository persistedRepository,
            DefaultRoleRegistry defaultRoleRegistry) {
        this.persistedRepository = Objects.requireNonNull(persistedRepository, "persistedRepository cannot be null");
        this.defaultRoleRegistry = Objects.requireNonNull(defaultRoleRegistry, "defaultRoleRegistry cannot be null");
    }

    @Override
    public void save(GuildRole role) {
        if (defaultRoleRegistry.isDefaultRoleId(role.getId())) {
            throw new UnsupportedOperationException("Cannot save default roles - they are immutable");
        }
        persistedRepository.save(role);
    }

    @Override
    public void delete(String roleId) {
        if (defaultRoleRegistry.isDefaultRoleId(roleId)) {
            throw new UnsupportedOperationException("Cannot delete default roles - they are immutable");
        }
        persistedRepository.delete(roleId);
    }

    @Override
    public Optional<GuildRole> findById(String roleId) {
        if (defaultRoleRegistry.isDefaultRoleId(roleId)) {
            throw new IllegalArgumentException(
                "Cannot find default role by ID alone - use findByIdAndGuild(roleId, guildId) instead");
        }
        return persistedRepository.findById(roleId);
    }

    /**
     * Find role by ID with guild context (supports flyweight default roles).
     *
     * @param roleId the role ID
     * @param guildId the guild ID
     * @return GuildRole if found
     */
    public Optional<GuildRole> findByIdAndGuild(String roleId, UUID guildId) {
        if (defaultRoleRegistry.isDefaultRoleId(roleId)) {
            // Generate flyweight default role
            Optional<String> roleName = defaultRoleRegistry.extractRoleName(roleId);
            if (roleName.isPresent()) {
                return defaultRoleRegistry.generateDefaultGuildRole(guildId, roleName.get());
            }
            return Optional.empty();
        }
        // Delegate to persisted repository
        return persistedRepository.findById(roleId);
    }

    @Override
    public List<GuildRole> findByGuildId(UUID guildId) {
        List<GuildRole> roles = new ArrayList<>();

        // Add flyweight default roles
        roles.addAll(defaultRoleRegistry.getAllDefaultGuildRoles(guildId));

        // Add persisted custom roles
        roles.addAll(persistedRepository.findByGuildId(guildId));

        // Sort by priority descending
        roles.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));

        return roles;
    }

    @Override
    public Optional<GuildRole> findByGuildAndName(UUID guildId, String name) {
        // Check if it's a default role first
        if (defaultRoleRegistry.isDefaultRole(name)) {
            return defaultRoleRegistry.generateDefaultGuildRole(guildId, name);
        }
        // Otherwise check persisted roles
        return persistedRepository.findByGuildAndName(guildId, name);
    }

    @Override
    public void deleteAllByGuild(UUID guildId) {
        // Only delete persisted roles (default roles are never in DB)
        persistedRepository.deleteAllByGuild(guildId);
    }
}
