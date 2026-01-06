package org.aincraft.messages;

/**
 * Centralized message keys for i18n support.
 * All user-facing messages should be defined here with corresponding entries in messages_en.yml.
 */
public enum MessageKey {
    // === Common Errors ===
    ERROR_PLAYER_ONLY("error.player-only"),
    ERROR_NOT_IN_GUILD("error.not-in-guild"),
    ERROR_NO_PERMISSION("error.no-permission"),
    ERROR_PLAYER_NOT_FOUND("error.player-not-found"),
    ERROR_GUILD_NOT_FOUND("error.guild-not-found"),
    ERROR_ALREADY_IN_GUILD("error.already-in-guild"),
    ERROR_GUILD_FULL("error.guild-full"),
    ERROR_NOT_GUILD_OWNER("error.not-guild-owner"),
    ERROR_USAGE("error.usage"),

    // === Guild Lifecycle ===
    GUILD_CREATED("guild.created"),
    GUILD_DISBANDED("guild.disbanded"),
    GUILD_LEFT("guild.left"),
    GUILD_JOINED("guild.joined"),
    GUILD_KICKED("guild.kicked"),
    GUILD_NAME_TAKEN("guild.name-taken"),
    GUILD_NAME_CHANGED("guild.name-changed"),
    GUILD_DESCRIPTION_SET("guild.description-set"),
    GUILD_DESCRIPTION_CLEARED("guild.description-cleared"),
    GUILD_COLOR_SET("guild.color-set"),
    GUILD_COLOR_CLEARED("guild.color-cleared"),
    GUILD_COLOR_INVALID("guild.color-invalid"),
    GUILD_TRANSFER_SUCCESS("guild.transfer-success"),
    GUILD_TRANSFER_CONFIRM("guild.transfer-confirm"),

    // === Chunk Claims ===
    CLAIM_SUCCESS("claim.success"),
    CLAIM_UNCLAIMED("claim.unclaimed"),
    CLAIM_ALREADY_OWNED("claim.already-owned"),
    CLAIM_ALREADY_CLAIMED("claim.already-claimed"),
    CLAIM_NOT_ADJACENT("claim.not-adjacent"),
    CLAIM_LIMIT_EXCEEDED("claim.limit-exceeded"),
    CLAIM_TOO_CLOSE("claim.too-close"),
    CLAIM_NOT_OWNED("claim.not-owned"),
    CLAIM_CANNOT_UNCLAIM_HOMEBLOCK("claim.cannot-unclaim-homeblock"),
    CLAIM_UNCLAIM_ALL("claim.unclaim-all"),
    CLAIM_NO_PERMISSION("claim.no-permission"),

    // === Auto Claim/Unclaim ===
    AUTO_ENABLED_CLAIM("auto.enabled-claim"),
    AUTO_ENABLED_UNCLAIM("auto.enabled-unclaim"),
    AUTO_DISABLED("auto.disabled"),
    AUTO_CURRENT_MODE("auto.current-mode"),
    AUTO_NOT_IN_GUILD("auto.not-in-guild"),

    // === Spawn ===
    SPAWN_SET("spawn.set"),
    SPAWN_CLEARED("spawn.cleared"),
    SPAWN_TELEPORTED("spawn.teleported"),
    SPAWN_NO_SPAWN("spawn.no-spawn"),
    SPAWN_NO_HOMEBLOCK("spawn.no-homeblock"),
    SPAWN_COOLDOWN("spawn.cooldown"),

    // === Roles ===
    ROLE_CREATED("role.created"),
    ROLE_DELETED("role.deleted"),
    ROLE_ASSIGNED("role.assigned"),
    ROLE_UNASSIGNED("role.unassigned"),
    ROLE_UPDATED("role.updated"),
    ROLE_RENAMED("role.renamed"),
    ROLE_COPIED("role.copied"),
    ROLE_ALREADY_EXISTS("role.already-exists"),
    ROLE_NOT_FOUND("role.not-found"),
    ROLE_NO_PERMISSION("role.no-permission"),
    ROLE_INVALID_PERMISSIONS("role.invalid-permissions"),
    ROLE_INVALID_PRIORITY("role.invalid-priority"),
    ROLE_PRIORITY_SET("role.priority-set"),
    ROLE_PERMISSION_ADDED("role.permission-added"),
    ROLE_PERMISSION_REMOVED("role.permission-removed"),
    ROLE_PREFIX_SET("role.prefix-set"),
    ROLE_PREFIX_CLEARED("role.prefix-cleared"),
    ROLE_COLOR_SET("role.color-set"),

    // === Invites ===
    INVITE_SENT("invite.sent"),
    INVITE_RECEIVED("invite.received"),
    INVITE_ACCEPTED("invite.accepted"),
    INVITE_DECLINED("invite.declined"),
    INVITE_EXPIRED("invite.expired"),
    INVITE_ALREADY_PENDING("invite.already-pending"),
    INVITE_TARGET_IN_GUILD("invite.target-in-guild"),
    INVITE_MAX_PENDING("invite.max-pending"),
    INVITE_NOT_FOUND("invite.not-found"),
    INVITE_NO_PERMISSION("invite.no-permission"),

    // === Chat ===
    CHAT_MODE_CHANGED("chat.mode-changed"),
    CHAT_NO_GUILD_RESET("chat.no-guild-reset"),
    CHAT_NO_PERMISSION("chat.no-permission"),
    CHAT_ALLY_ENABLED("chat.ally-enabled"),
    CHAT_ALLY_DISABLED("chat.ally-disabled"),
    CHAT_GUILD_ENABLED("chat.guild-enabled"),
    CHAT_GUILD_DISABLED("chat.guild-disabled"),
    CHAT_OFFICER_ENABLED("chat.officer-enabled"),
    CHAT_OFFICER_DISABLED("chat.officer-disabled"),

    // === Allies/Enemies ===
    ALLY_REQUEST_SENT("ally.request-sent"),
    ALLY_REQUEST_RECEIVED("ally.request-received"),
    ALLY_ACCEPTED("ally.accepted"),
    ALLY_REJECTED("ally.rejected"),
    ALLY_BROKEN("ally.broken"),
    ALLY_ALREADY_EXISTS("ally.already-exists"),
    ALLY_NO_PENDING("ally.no-pending"),
    ALLY_CANNOT_SELF("ally.cannot-self"),
    ALLY_LIST_HEADER("ally.list-header"),
    ALLY_LIST_EMPTY("ally.list-empty"),
    ENEMY_DECLARED("enemy.declared"),
    ENEMY_REMOVED("enemy.removed"),
    ENEMY_ALREADY_EXISTS("enemy.already-exists"),
    NEUTRAL_SET("neutral.set"),

    // === Protection ===
    PROTECTION_BUILD_DENIED("protection.build-denied"),
    PROTECTION_BREAK_DENIED("protection.break-denied"),
    PROTECTION_INTERACT_DENIED("protection.interact-denied"),
    PROTECTION_CONTAINER_DENIED("protection.container-denied"),
    PROTECTION_ATTACK_DENIED("protection.attack-denied"),

    // === Vault ===
    VAULT_OPENED("vault.opened"),
    VAULT_DESTROYED("vault.destroyed"),
    VAULT_NOT_FOUND("vault.not-found"),
    VAULT_NO_PERMISSION("vault.no-permission"),
    VAULT_OWNER_ONLY("vault.owner-only"),
    VAULT_CONFIRM_DESTROY("vault.confirm-destroy"),
    VAULT_DEPOSIT_DENIED("vault.deposit-denied"),
    VAULT_WITHDRAW_DENIED("vault.withdraw-denied"),
    VAULT_ITEMS_DROPPED("vault.items-dropped"),

    // === Progression ===
    LEVEL_UP_SUCCESS("progression.level-up-success"),
    LEVEL_UP_MAX_LEVEL("progression.max-level"),
    LEVEL_UP_INSUFFICIENT_XP("progression.insufficient-xp"),
    LEVEL_UP_INSUFFICIENT_MATERIALS("progression.insufficient-materials"),
    XP_GAINED("progression.xp-gained"),

    // === Projects ===
    PROJECT_STARTED("project.started"),
    PROJECT_COMPLETED("project.completed"),
    PROJECT_ABANDONED("project.abandoned"),
    PROJECT_PROGRESS("project.progress"),
    PROJECT_CONTRIBUTED("project.contributed"),
    PROJECT_NO_ACTIVE("project.no-active"),
    PROJECT_BUFF_ACTIVATED("project.buff-activated"),

    // === Skills ===
    SKILL_UNLOCKED("skill.unlocked"),
    SKILL_ALREADY_UNLOCKED("skill.already-unlocked"),
    SKILL_INSUFFICIENT_SP("skill.insufficient-sp"),
    SKILL_PREREQUISITES_NOT_MET("skill.prerequisites-not-met"),

    // === Subregions ===
    REGION_CREATED("region.created"),
    REGION_DELETED("region.deleted"),
    REGION_NOT_FOUND("region.not-found"),
    REGION_POS1_SET("region.pos1-set"),
    REGION_POS2_SET("region.pos2-set"),
    REGION_CANCELLED("region.cancelled"),
    REGION_NO_CREATION("region.no-creation"),
    REGION_PERMISSION_ADDED("region.permission-added"),
    REGION_PERMISSION_REMOVED("region.permission-removed"),
    REGION_OWNER_ADDED("region.owner-added"),
    REGION_OWNER_REMOVED("region.owner-removed"),
    REGION_TYPE_UNKNOWN("region.type-unknown"),
    REGION_TYPE_LIMIT_SET("region.type-limit-set"),

    // === Outposts ===
    OUTPOST_CREATED("outpost.created"),
    OUTPOST_DELETED("outpost.deleted"),
    OUTPOST_NOT_FOUND("outpost.not-found"),
    OUTPOST_SPAWN_SET("outpost.spawn-set"),
    OUTPOST_TELEPORTED("outpost.teleported"),

    // === Map ===
    MAP_INVALID_SIZE("map.invalid-size"),
    MAP_SIZE_RANGE("map.size-range"),

    // === List/Info ===
    LIST_HEADER("list.header"),
    LIST_EMPTY("list.empty"),
    LIST_INVALID_PAGE("list.invalid-page"),
    INFO_HEADER("info.header"),

    // === Admin ===
    ADMIN_RELOAD_SUCCESS("admin.reload-success"),
    ADMIN_FORCE_DISBAND("admin.force-disband"),
    ADMIN_FORCE_CLAIM("admin.force-claim"),
    ADMIN_FORCE_UNCLAIM("admin.force-unclaim"),

    // === Misc ===
    CONFIRM_REQUIRED("misc.confirm-required"),
    ACTION_CANCELLED("misc.action-cancelled"),
    COOLDOWN_ACTIVE("misc.cooldown-active");

    private final String key;

    MessageKey(String key) {
        this.key = key;
    }

    /**
     * Gets the message key path for YAML lookup.
     *
     * @return the dot-separated key path
     */
    public String getKey() {
        return key;
    }
}
