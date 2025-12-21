package org.aincraft.role;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.aincraft.GuildRole;
import org.aincraft.Role;
import org.aincraft.config.GuildsConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registry and factory for default guild roles (Flyweight pattern).
 * Loads role templates from config.yml and generates guild-specific instances on-demand.
 */
@Singleton
public class DefaultRoleRegistry {
    private static final String DEFAULT_ROLE_ID_PREFIX = "DEFAULT:";

    private final Map<String, DefaultRoleTemplate> templates = new HashMap<>();
    private final GuildsConfig config;
    private final Logger logger;

    @Inject
    public DefaultRoleRegistry(GuildsConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.logger = config.getPlugin().getLogger();
        loadTemplatesFromConfig();
    }

    /**
     * Load default role templates from config.yml.
     */
    void loadTemplatesFromConfig() {
        templates.clear();

        ConfigurationSection defaultRolesSection = config.getPlugin().getConfig().getConfigurationSection("default-roles");
        if (defaultRolesSection == null) {
            logger.warning("No default-roles section found in config.yml. No default roles will be available.");
            return;
        }

        for (String key : defaultRolesSection.getKeys(false)) {
            ConfigurationSection roleSection = defaultRolesSection.getConfigurationSection(key);
            if (roleSection == null) {
                logger.warning("Invalid default role configuration for key: " + key);
                continue;
            }

            try {
                DefaultRoleTemplate template = DefaultRoleTemplate.fromConfig(roleSection);
                templates.put(template.getName(), template);
                logger.info("Loaded default role: " + template.getName() +
                           " (priority=" + template.getPriority() +
                           ", permissions=" + template.getPermissions() +
                           ", auto-assign=" + template.isAutoAssign() + ")");
            } catch (IllegalArgumentException e) {
                logger.warning("Failed to load default role '" + key + "': " + e.getMessage());
            }
        }

        logger.info("Loaded " + templates.size() + " default role template(s)");
    }

    /**
     * Generate a flyweight Role for a specific guild.
     *
     * @param guildId the guild ID
     * @param roleName the role name (e.g., "Admin", "Member")
     * @return Role instance or empty if template not found
     */
    public Optional<Role> generateDefaultRole(UUID guildId, String roleName) {
        DefaultRoleTemplate template = templates.get(roleName);
        if (template == null) {
            return Optional.empty();
        }

        String roleId = DEFAULT_ROLE_ID_PREFIX + roleName;
        return Optional.of(new Role(
            roleId,
            guildId,
            template.getName(),
            template.getPermissions(),
            template.getPriority(),
            null,  // createdBy - null for system roles
            null   // createdAt - null for system roles
        ));
    }

    /**
     * Get all default roles for a guild.
     *
     * @param guildId the guild ID
     * @return list of all default roles (flyweights)
     */
    public List<Role> getAllDefaultRoles(UUID guildId) {
        List<Role> roles = new ArrayList<>();
        for (DefaultRoleTemplate template : templates.values()) {
            generateDefaultRole(guildId, template.getName()).ifPresent(roles::add);
        }
        // Sort by priority descending
        roles.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        return roles;
    }

    /**
     * Check if a role name is a default role.
     *
     * @param roleName the role name
     * @return true if this is a default role
     */
    public boolean isDefaultRole(String roleName) {
        return templates.containsKey(roleName);
    }

    /**
     * Check if a role ID is a default role ID.
     *
     * @param roleId the role ID
     * @return true if ID starts with DEFAULT:
     */
    public boolean isDefaultRoleId(String roleId) {
        return roleId != null && roleId.startsWith(DEFAULT_ROLE_ID_PREFIX);
    }

    /**
     * Extract role name from default role ID.
     *
     * @param roleId the role ID (e.g., "DEFAULT:Admin")
     * @return role name (e.g., "Admin") or empty if not a default role ID
     */
    public Optional<String> extractRoleName(String roleId) {
        if (!isDefaultRoleId(roleId)) {
            return Optional.empty();
        }
        return Optional.of(roleId.substring(DEFAULT_ROLE_ID_PREFIX.length()));
    }

    /**
     * Get the template for auto-assigned role (if any).
     *
     * @return the auto-assign role template or null if none configured
     */
    public DefaultRoleTemplate getAutoAssignTemplate() {
        return templates.values().stream()
            .filter(DefaultRoleTemplate::isAutoAssign)
            .findFirst()
            .orElse(null);
    }

    /**
     * Get all template names.
     *
     * @return set of all default role names
     */
    public Set<String> getDefaultRoleNames() {
        return Collections.unmodifiableSet(templates.keySet());
    }

    /**
     * Generate a flyweight GuildRole for a specific guild.
     *
     * @param guildId the guild ID
     * @param roleName the role name (e.g., "Admin", "Member")
     * @return GuildRole instance or empty if template not found
     */
    public Optional<GuildRole> generateDefaultGuildRole(UUID guildId, String roleName) {
        DefaultRoleTemplate template = templates.get(roleName);
        if (template == null) {
            return Optional.empty();
        }

        String roleId = DEFAULT_ROLE_ID_PREFIX + roleName;
        return Optional.of(new GuildRole(
            roleId,
            guildId,
            template.getName(),
            template.getPermissions(),
            template.getPriority(),
            null,  // createdBy - null for system roles
            null   // createdAt - null for system roles
        ));
    }

    /**
     * Get all default roles for a guild as GuildRole instances.
     *
     * @param guildId the guild ID
     * @return list of all default roles (flyweights)
     */
    public List<GuildRole> getAllDefaultGuildRoles(UUID guildId) {
        List<GuildRole> roles = new ArrayList<>();
        for (DefaultRoleTemplate template : templates.values()) {
            generateDefaultGuildRole(guildId, template.getName()).ifPresent(roles::add);
        }
        // Sort by priority descending
        roles.sort((r1, r2) -> Integer.compare(r2.getPriority(), r1.getPriority()));
        return roles;
    }

    /**
     * Reload templates from config.
     */
    public void reload() {
        loadTemplatesFromConfig();
    }
}
