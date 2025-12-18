package org.aincraft.database;

/**
 * SQL queries organized by database type.
 * Contains all database-specific SQL variations.
 */
public final class Sql {

    private Sql() {}

    // ==================== GUILDS ====================

    public static String createGuildsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guilds (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    description TEXT,
                    owner_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    max_members INTEGER NOT NULL,
                    members TEXT NOT NULL,
                    spawn_world TEXT,
                    spawn_x REAL,
                    spawn_y REAL,
                    spawn_z REAL,
                    spawn_yaw REAL,
                    spawn_pitch REAL,
                    color TEXT,
                    homeblock_world TEXT,
                    homeblock_chunk_x INTEGER,
                    homeblock_chunk_z INTEGER,
                    allow_explosions INTEGER DEFAULT 1,
                    allow_fire INTEGER DEFAULT 1,
                    is_public INTEGER DEFAULT 0
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS guilds (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    owner_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    max_members INT NOT NULL,
                    members TEXT NOT NULL,
                    spawn_world VARCHAR(255),
                    spawn_x DOUBLE,
                    spawn_y DOUBLE,
                    spawn_z DOUBLE,
                    spawn_yaw FLOAT,
                    spawn_pitch FLOAT,
                    color VARCHAR(32),
                    homeblock_world VARCHAR(255),
                    homeblock_chunk_x INT,
                    homeblock_chunk_z INT,
                    allow_explosions TINYINT(1) DEFAULT 1,
                    allow_fire TINYINT(1) DEFAULT 1,
                    is_public TINYINT(1) DEFAULT 0
                )
                """;
            case POSTGRESQL -> """
                CREATE TABLE IF NOT EXISTS guilds (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    owner_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    max_members INT NOT NULL,
                    members TEXT NOT NULL,
                    spawn_world VARCHAR(255),
                    spawn_x DOUBLE PRECISION,
                    spawn_y DOUBLE PRECISION,
                    spawn_z DOUBLE PRECISION,
                    spawn_yaw REAL,
                    spawn_pitch REAL,
                    color VARCHAR(32),
                    homeblock_world VARCHAR(255),
                    homeblock_chunk_x INT,
                    homeblock_chunk_z INT,
                    allow_explosions BOOLEAN DEFAULT TRUE,
                    allow_fire BOOLEAN DEFAULT TRUE,
                    is_public BOOLEAN DEFAULT FALSE
                )
                """;
            case H2 -> """
                CREATE TABLE IF NOT EXISTS guilds (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    owner_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    max_members INT NOT NULL,
                    members TEXT NOT NULL,
                    spawn_world VARCHAR(255),
                    spawn_x DOUBLE,
                    spawn_y DOUBLE,
                    spawn_z DOUBLE,
                    spawn_yaw REAL,
                    spawn_pitch REAL,
                    color VARCHAR(32),
                    homeblock_world VARCHAR(255),
                    homeblock_chunk_x INT,
                    homeblock_chunk_z INT,
                    allow_explosions BOOLEAN DEFAULT TRUE,
                    allow_fire BOOLEAN DEFAULT TRUE,
                    is_public BOOLEAN DEFAULT FALSE
                )
                """;
        };
    }

    public static String upsertGuild(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guilds
                (id, name, description, owner_id, created_at, max_members, members,
                 spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, color,
                 homeblock_world, homeblock_chunk_x, homeblock_chunk_z,
                 allow_explosions, allow_fire, is_public)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guilds
                (id, name, description, owner_id, created_at, max_members, members,
                 spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, color,
                 homeblock_world, homeblock_chunk_x, homeblock_chunk_z,
                 allow_explosions, allow_fire, is_public)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), description = VALUES(description), owner_id = VALUES(owner_id),
                max_members = VALUES(max_members), members = VALUES(members),
                spawn_world = VALUES(spawn_world), spawn_x = VALUES(spawn_x),
                spawn_y = VALUES(spawn_y), spawn_z = VALUES(spawn_z),
                spawn_yaw = VALUES(spawn_yaw), spawn_pitch = VALUES(spawn_pitch),
                color = VALUES(color), homeblock_world = VALUES(homeblock_world),
                homeblock_chunk_x = VALUES(homeblock_chunk_x), homeblock_chunk_z = VALUES(homeblock_chunk_z),
                allow_explosions = VALUES(allow_explosions), allow_fire = VALUES(allow_fire),
                is_public = VALUES(is_public)
                """;
            case POSTGRESQL -> """
                INSERT INTO guilds
                (id, name, description, owner_id, created_at, max_members, members,
                 spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, color,
                 homeblock_world, homeblock_chunk_x, homeblock_chunk_z,
                 allow_explosions, allow_fire, is_public)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, description = EXCLUDED.description, owner_id = EXCLUDED.owner_id,
                max_members = EXCLUDED.max_members, members = EXCLUDED.members,
                spawn_world = EXCLUDED.spawn_world, spawn_x = EXCLUDED.spawn_x,
                spawn_y = EXCLUDED.spawn_y, spawn_z = EXCLUDED.spawn_z,
                spawn_yaw = EXCLUDED.spawn_yaw, spawn_pitch = EXCLUDED.spawn_pitch,
                color = EXCLUDED.color, homeblock_world = EXCLUDED.homeblock_world,
                homeblock_chunk_x = EXCLUDED.homeblock_chunk_x, homeblock_chunk_z = EXCLUDED.homeblock_chunk_z,
                allow_explosions = EXCLUDED.allow_explosions, allow_fire = EXCLUDED.allow_fire,
                is_public = EXCLUDED.is_public
                """;
            case H2 -> """
                MERGE INTO guilds
                (id, name, description, owner_id, created_at, max_members, members,
                 spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, color,
                 homeblock_world, homeblock_chunk_x, homeblock_chunk_z,
                 allow_explosions, allow_fire, is_public)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== GUILD MEMBERS ====================

    public static String createGuildMembersTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    permissions INTEGER NOT NULL DEFAULT 0,
                    joined_at INTEGER,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    permissions INT NOT NULL DEFAULT 0,
                    joined_at BIGINT,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
            case POSTGRESQL -> """
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    permissions INT NOT NULL DEFAULT 0,
                    joined_at BIGINT,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
            case H2 -> """
                CREATE TABLE IF NOT EXISTS guild_members (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    permissions INT NOT NULL DEFAULT 0,
                    joined_at BIGINT,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
        };
    }

    public static String upsertGuildMember(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_members (guild_id, player_id, permissions, joined_at)
                VALUES (?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_members (guild_id, player_id, permissions, joined_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE permissions = VALUES(permissions), joined_at = VALUES(joined_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_members (guild_id, player_id, permissions, joined_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (guild_id, player_id) DO UPDATE SET
                permissions = EXCLUDED.permissions, joined_at = EXCLUDED.joined_at
                """;
            case H2 -> """
                MERGE INTO guild_members (guild_id, player_id, permissions, joined_at)
                KEY (guild_id, player_id)
                VALUES (?, ?, ?, ?)
                """;
        };
    }

    // ==================== PLAYER GUILDS ====================

    public static String createPlayerGuildsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS player_guilds (
                    player_id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS player_guilds (
                    player_id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL
                )
                """;
        };
    }

    public static String upsertPlayerGuild(DatabaseType type) {
        return switch (type) {
            case SQLITE -> "INSERT OR REPLACE INTO player_guilds (player_id, guild_id) VALUES (?, ?)";
            case MYSQL, MARIADB -> """
                INSERT INTO player_guilds (player_id, guild_id) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE guild_id = VALUES(guild_id)
                """;
            case POSTGRESQL -> """
                INSERT INTO player_guilds (player_id, guild_id) VALUES (?, ?)
                ON CONFLICT (player_id) DO UPDATE SET guild_id = EXCLUDED.guild_id
                """;
            case H2 -> "MERGE INTO player_guilds (player_id, guild_id) KEY (player_id) VALUES (?, ?)";
        };
    }

    // ==================== GUILD ROLES ====================

    public static String createGuildRolesTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_roles (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    permissions INTEGER NOT NULL DEFAULT 0,
                    priority INTEGER NOT NULL DEFAULT 0,
                    prefix TEXT,
                    color TEXT,
                    created_by TEXT,
                    created_at INTEGER
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS guild_roles (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    permissions INT NOT NULL DEFAULT 0,
                    priority INT NOT NULL DEFAULT 0,
                    prefix VARCHAR(64),
                    color VARCHAR(32),
                    created_by VARCHAR(36),
                    created_at BIGINT
                )
                """;
            case POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_roles (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    permissions INT NOT NULL DEFAULT 0,
                    priority INT NOT NULL DEFAULT 0,
                    prefix VARCHAR(64),
                    color VARCHAR(32),
                    created_by VARCHAR(36),
                    created_at BIGINT
                )
                """;
        };
    }

    public static String upsertGuildRole(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_roles (id, guild_id, name, permissions, priority, prefix, color, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_roles (id, guild_id, name, permissions, priority, prefix, color, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), permissions = VALUES(permissions), priority = VALUES(priority),
                prefix = VALUES(prefix), color = VALUES(color)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_roles (id, guild_id, name, permissions, priority, prefix, color, created_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, permissions = EXCLUDED.permissions, priority = EXCLUDED.priority,
                prefix = EXCLUDED.prefix, color = EXCLUDED.color
                """;
            case H2 -> """
                MERGE INTO guild_roles (id, guild_id, name, permissions, priority, prefix, color, created_by, created_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== MEMBER ROLES ====================

    public static String createMemberRolesTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS member_roles (
                    guild_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    PRIMARY KEY (guild_id, player_id, role_id)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS member_roles (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    role_id VARCHAR(36) NOT NULL,
                    PRIMARY KEY (guild_id, player_id, role_id)
                )
                """;
        };
    }

    // ==================== CHUNK CLAIMS (guild_chunks table) ====================

    public static String createChunkClaimsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_chunks (
                    world TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    guild_id TEXT NOT NULL,
                    claimed_at INTEGER NOT NULL,
                    claimed_by TEXT NOT NULL,
                    PRIMARY KEY (world, chunk_x, chunk_z)
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS guild_chunks (
                    world VARCHAR(255) NOT NULL,
                    chunk_x INT NOT NULL,
                    chunk_z INT NOT NULL,
                    guild_id VARCHAR(36) NOT NULL,
                    claimed_at BIGINT NOT NULL,
                    claimed_by VARCHAR(36) NOT NULL,
                    PRIMARY KEY (world, chunk_x, chunk_z)
                )
                """;
            case POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_chunks (
                    world VARCHAR(255) NOT NULL,
                    chunk_x INT NOT NULL,
                    chunk_z INT NOT NULL,
                    guild_id VARCHAR(36) NOT NULL,
                    claimed_at BIGINT NOT NULL,
                    claimed_by VARCHAR(36) NOT NULL,
                    PRIMARY KEY (world, chunk_x, chunk_z)
                )
                """;
        };
    }

    // ==================== GUILD RELATIONSHIPS ====================

    public static String createGuildRelationshipsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_relationships (
                    id TEXT PRIMARY KEY,
                    source_guild_id TEXT NOT NULL,
                    target_guild_id TEXT NOT NULL,
                    relation_type TEXT NOT NULL,
                    status TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by TEXT NOT NULL,
                    UNIQUE(source_guild_id, target_guild_id)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_relationships (
                    id VARCHAR(36) PRIMARY KEY,
                    source_guild_id VARCHAR(36) NOT NULL,
                    target_guild_id VARCHAR(36) NOT NULL,
                    relation_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_at BIGINT NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    UNIQUE(source_guild_id, target_guild_id)
                )
                """;
        };
    }

    // ==================== GUILD DEFAULT PERMISSIONS ====================

    public static String createGuildDefaultPermissionsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_default_permissions (
                    guild_id TEXT PRIMARY KEY,
                    ally_permissions INTEGER NOT NULL DEFAULT 4,
                    enemy_permissions INTEGER NOT NULL DEFAULT 0,
                    outsider_permissions INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_default_permissions (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    ally_permissions INT NOT NULL DEFAULT 4,
                    enemy_permissions INT NOT NULL DEFAULT 0,
                    outsider_permissions INT NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                )
                """;
        };
    }

    // ==================== INVITES ====================

    public static String createInvitesTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_invites (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    inviter_id TEXT NOT NULL,
                    invitee_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_invites (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    inviter_id VARCHAR(36) NOT NULL,
                    invitee_id VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """;
        };
    }

    // ==================== SUBREGIONS ====================

    public static String createSubregionsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS subregions (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    world TEXT NOT NULL,
                    min_x INTEGER NOT NULL,
                    min_y INTEGER NOT NULL,
                    min_z INTEGER NOT NULL,
                    max_x INTEGER NOT NULL,
                    max_y INTEGER NOT NULL,
                    max_z INTEGER NOT NULL,
                    owners TEXT NOT NULL,
                    type TEXT NOT NULL
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS subregions (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    min_x INT NOT NULL,
                    min_y INT NOT NULL,
                    min_z INT NOT NULL,
                    max_x INT NOT NULL,
                    max_y INT NOT NULL,
                    max_z INT NOT NULL,
                    owners TEXT NOT NULL,
                    type VARCHAR(64) NOT NULL
                )
                """;
        };
    }

    public static String upsertSubregion(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z, owners, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z, owners, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), min_x = VALUES(min_x), min_y = VALUES(min_y), min_z = VALUES(min_z),
                max_x = VALUES(max_x), max_y = VALUES(max_y), max_z = VALUES(max_z),
                owners = VALUES(owners), type = VALUES(type)
                """;
            case POSTGRESQL -> """
                INSERT INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z, owners, type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, min_x = EXCLUDED.min_x, min_y = EXCLUDED.min_y, min_z = EXCLUDED.min_z,
                max_x = EXCLUDED.max_x, max_y = EXCLUDED.max_y, max_z = EXCLUDED.max_z,
                owners = EXCLUDED.owners, type = EXCLUDED.type
                """;
            case H2 -> """
                MERGE INTO subregions
                (id, guild_id, name, world, min_x, min_y, min_z, max_x, max_y, max_z, owners, type)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== REGION PERMISSIONS ====================

    public static String createRegionPermissionsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS region_permissions (
                    id TEXT PRIMARY KEY,
                    region_id TEXT NOT NULL,
                    subject_id TEXT NOT NULL,
                    subject_type TEXT NOT NULL,
                    permissions INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by TEXT NOT NULL,
                    UNIQUE(region_id, subject_id, subject_type)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS region_permissions (
                    id VARCHAR(36) PRIMARY KEY,
                    region_id VARCHAR(36) NOT NULL,
                    subject_id VARCHAR(36) NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    permissions INT NOT NULL,
                    created_at BIGINT NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    UNIQUE(region_id, subject_id, subject_type)
                )
                """;
        };
    }

    public static String upsertRegionPermission(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                permissions = VALUES(permissions)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                permissions = EXCLUDED.permissions
                """;
            case H2 -> """
                MERGE INTO region_permissions
                (id, region_id, subject_id, subject_type, permissions, created_at, created_by)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== REGION ROLES ====================

    public static String createRegionRolesTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS region_roles (
                    id TEXT PRIMARY KEY,
                    region_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    permissions INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    created_by TEXT NOT NULL,
                    UNIQUE(region_id, name)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS region_roles (
                    id VARCHAR(36) PRIMARY KEY,
                    region_id VARCHAR(36) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    permissions INT NOT NULL,
                    created_at BIGINT NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    UNIQUE(region_id, name)
                )
                """;
        };
    }

    public static String upsertRegionRole(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                name = VALUES(name), permissions = VALUES(permissions)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name, permissions = EXCLUDED.permissions
                """;
            case H2 -> """
                MERGE INTO region_roles
                (id, region_id, name, permissions, created_at, created_by)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== MEMBER REGION ROLES ====================

    public static String createMemberRegionRolesTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS member_region_roles (
                    region_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    role_id TEXT NOT NULL,
                    PRIMARY KEY (region_id, player_id, role_id)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS member_region_roles (
                    region_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    role_id VARCHAR(36) NOT NULL,
                    PRIMARY KEY (region_id, player_id, role_id)
                )
                """;
        };
    }

    // ==================== REGION TYPE LIMITS ====================

    public static String createRegionTypeLimitsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS region_type_limits (
                    type_id TEXT PRIMARY KEY,
                    max_total_volume INTEGER NOT NULL
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS region_type_limits (
                    type_id VARCHAR(64) PRIMARY KEY,
                    max_total_volume BIGINT NOT NULL
                )
                """;
        };
    }

    public static String upsertRegionTypeLimit(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE max_total_volume = VALUES(max_total_volume)
                """;
            case POSTGRESQL -> """
                INSERT INTO region_type_limits (type_id, max_total_volume)
                VALUES (?, ?)
                ON CONFLICT (type_id) DO UPDATE SET max_total_volume = EXCLUDED.max_total_volume
                """;
            case H2 -> """
                MERGE INTO region_type_limits (type_id, max_total_volume)
                KEY (type_id) VALUES (?, ?)
                """;
        };
    }

    // ==================== VAULT ====================

    public static String createVaultsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_vaults (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL UNIQUE,
                    world TEXT NOT NULL,
                    origin_x INTEGER NOT NULL,
                    origin_y INTEGER NOT NULL,
                    origin_z INTEGER NOT NULL,
                    rotation TEXT NOT NULL,
                    created_by TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    storage_data TEXT
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS guild_vaults (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL UNIQUE,
                    world VARCHAR(255) NOT NULL,
                    origin_x INT NOT NULL,
                    origin_y INT NOT NULL,
                    origin_z INT NOT NULL,
                    rotation VARCHAR(32) NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    storage_data MEDIUMTEXT
                )
                """;
            case POSTGRESQL -> """
                CREATE TABLE IF NOT EXISTS guild_vaults (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL UNIQUE,
                    world VARCHAR(255) NOT NULL,
                    origin_x INT NOT NULL,
                    origin_y INT NOT NULL,
                    origin_z INT NOT NULL,
                    rotation VARCHAR(32) NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    storage_data TEXT
                )
                """;
            case H2 -> """
                CREATE TABLE IF NOT EXISTS guild_vaults (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL UNIQUE,
                    world VARCHAR(255) NOT NULL,
                    origin_x INT NOT NULL,
                    origin_y INT NOT NULL,
                    origin_z INT NOT NULL,
                    rotation VARCHAR(32) NOT NULL,
                    created_by VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    storage_data TEXT
                )
                """;
        };
    }

    public static String upsertVault(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                world = VALUES(world), origin_x = VALUES(origin_x), origin_y = VALUES(origin_y),
                origin_z = VALUES(origin_z), rotation = VALUES(rotation), storage_data = VALUES(storage_data)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                world = EXCLUDED.world, origin_x = EXCLUDED.origin_x, origin_y = EXCLUDED.origin_y,
                origin_z = EXCLUDED.origin_z, rotation = EXCLUDED.rotation, storage_data = EXCLUDED.storage_data
                """;
            case H2 -> """
                MERGE INTO guild_vaults
                (id, guild_id, world, origin_x, origin_y, origin_z, rotation, created_by, created_at, storage_data)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== VAULT TRANSACTIONS ====================

    public static String createVaultTransactionsTable(DatabaseType type) {
        String autoIncrement = switch (type) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL, MARIADB -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case H2 -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
        };

        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS vault_transactions (
                    id %s,
                    vault_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    item_type TEXT NOT NULL,
                    item_amount INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.formatted(autoIncrement);
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS vault_transactions (
                    id %s,
                    vault_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    item_type VARCHAR(255) NOT NULL,
                    item_amount INT NOT NULL,
                    timestamp BIGINT NOT NULL
                )
                """.formatted(autoIncrement);
        };
    }

    // ==================== CHUNK CLAIM LOG ====================

    public static String createChunkClaimLogTable(DatabaseType type) {
        String autoIncrement = switch (type) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL, MARIADB -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case H2 -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
        };

        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS chunk_claim_logs (
                    id %s,
                    guild_id TEXT NOT NULL,
                    world TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    player_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.formatted(autoIncrement);
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS chunk_claim_logs (
                    id %s,
                    guild_id VARCHAR(36) NOT NULL,
                    world VARCHAR(255) NOT NULL,
                    chunk_x INT NOT NULL,
                    chunk_z INT NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    timestamp BIGINT NOT NULL
                )
                """.formatted(autoIncrement);
        };
    }

    // ==================== GUILD PROGRESSION ====================

    public static String createGuildProgressionTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_progression (
                    guild_id TEXT PRIMARY KEY,
                    level INTEGER NOT NULL DEFAULT 1,
                    current_xp BIGINT NOT NULL DEFAULT 0,
                    total_xp_earned BIGINT NOT NULL DEFAULT 0,
                    last_levelup_time INTEGER
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_progression (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    level INT NOT NULL DEFAULT 1,
                    current_xp BIGINT NOT NULL DEFAULT 0,
                    total_xp_earned BIGINT NOT NULL DEFAULT 0,
                    last_levelup_time BIGINT
                )
                """;
        };
    }

    public static String upsertGuildProgression(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                level = VALUES(level), current_xp = VALUES(current_xp),
                total_xp_earned = VALUES(total_xp_earned), last_levelup_time = VALUES(last_levelup_time)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (guild_id) DO UPDATE SET
                level = EXCLUDED.level, current_xp = EXCLUDED.current_xp,
                total_xp_earned = EXCLUDED.total_xp_earned, last_levelup_time = EXCLUDED.last_levelup_time
                """;
            case H2 -> """
                MERGE INTO guild_progression (guild_id, level, current_xp, total_xp_earned, last_levelup_time)
                KEY (guild_id) VALUES (?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== XP CONTRIBUTIONS ====================

    public static String createXpContributionsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_xp_contributions (
                    guild_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    total_xp_contributed BIGINT NOT NULL DEFAULT 0,
                    last_contribution_time INTEGER,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_xp_contributions (
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    total_xp_contributed BIGINT NOT NULL DEFAULT 0,
                    last_contribution_time BIGINT,
                    PRIMARY KEY (guild_id, player_id)
                )
                """;
        };
    }

    public static String recordXpContribution(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(guild_id, player_id) DO UPDATE SET
                    total_xp_contributed = total_xp_contributed + excluded.total_xp_contributed,
                    last_contribution_time = excluded.last_contribution_time
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_xp_contributed = total_xp_contributed + VALUES(total_xp_contributed),
                    last_contribution_time = VALUES(last_contribution_time)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_xp_contributions (guild_id, player_id, total_xp_contributed, last_contribution_time)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (guild_id, player_id) DO UPDATE SET
                    total_xp_contributed = guild_xp_contributions.total_xp_contributed + EXCLUDED.total_xp_contributed,
                    last_contribution_time = EXCLUDED.last_contribution_time
                """;
            case H2 -> """
                MERGE INTO guild_xp_contributions AS t
                USING (VALUES (?, ?, ?, ?)) AS s(guild_id, player_id, total_xp_contributed, last_contribution_time)
                ON t.guild_id = s.guild_id AND t.player_id = s.player_id
                WHEN MATCHED THEN UPDATE SET
                    total_xp_contributed = t.total_xp_contributed + s.total_xp_contributed,
                    last_contribution_time = s.last_contribution_time
                WHEN NOT MATCHED THEN INSERT (guild_id, player_id, total_xp_contributed, last_contribution_time)
                    VALUES (s.guild_id, s.player_id, s.total_xp_contributed, s.last_contribution_time)
                """;
        };
    }

    // ==================== PROGRESSION LOG ====================

    public static String createProgressionLogTable(DatabaseType type) {
        String autoIncrement = switch (type) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL, MARIADB -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
            case POSTGRESQL -> "BIGSERIAL PRIMARY KEY";
            case H2 -> "BIGINT PRIMARY KEY AUTO_INCREMENT";
        };

        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS progression_logs (
                    id %s,
                    guild_id TEXT NOT NULL,
                    player_id TEXT,
                    action TEXT NOT NULL,
                    amount BIGINT NOT NULL,
                    details TEXT,
                    timestamp INTEGER NOT NULL
                )
                """.formatted(autoIncrement);
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS progression_logs (
                    id %s,
                    guild_id VARCHAR(36) NOT NULL,
                    player_id VARCHAR(36),
                    action VARCHAR(64) NOT NULL,
                    amount BIGINT NOT NULL,
                    details TEXT,
                    timestamp BIGINT NOT NULL
                )
                """.formatted(autoIncrement);
        };
    }

    // ==================== GUILD PROJECTS ====================

    public static String createGuildProjectsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_projects (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    project_definition_id TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
                    started_at INTEGER NOT NULL,
                    completed_at INTEGER
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_projects (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    project_definition_id VARCHAR(255) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
                    started_at BIGINT NOT NULL,
                    completed_at BIGINT
                )
                """;
        };
    }

    public static String createProjectQuestProgressTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS project_quest_progress (
                    project_id TEXT NOT NULL,
                    quest_id TEXT NOT NULL,
                    current_count INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (project_id, quest_id)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS project_quest_progress (
                    project_id VARCHAR(36) NOT NULL,
                    quest_id VARCHAR(255) NOT NULL,
                    current_count BIGINT NOT NULL DEFAULT 0,
                    PRIMARY KEY (project_id, quest_id)
                )
                """;
        };
    }

    public static String createProjectMaterialContributionsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS project_material_contributions (
                    project_id TEXT NOT NULL,
                    material TEXT NOT NULL,
                    amount INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (project_id, material)
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS project_material_contributions (
                    project_id VARCHAR(36) NOT NULL,
                    material VARCHAR(255) NOT NULL,
                    amount INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (project_id, material)
                )
                """;
        };
    }

    public static String createProjectPoolSeedTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS guild_project_pool_seed (
                    guild_id TEXT PRIMARY KEY,
                    seed INTEGER NOT NULL DEFAULT 0
                )
                """;
            case MYSQL, MARIADB, POSTGRESQL, H2 -> """
                CREATE TABLE IF NOT EXISTS guild_project_pool_seed (
                    guild_id VARCHAR(36) PRIMARY KEY,
                    seed INT NOT NULL DEFAULT 0
                )
                """;
        };
    }

    public static String upsertGuildProject(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                status = VALUES(status), completed_at = VALUES(completed_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status, completed_at = EXCLUDED.completed_at
                """;
            case H2 -> """
                MERGE INTO guild_projects
                (id, guild_id, project_definition_id, status, started_at, completed_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        };
    }

    public static String upsertQuestProgress(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON CONFLICT(project_id, quest_id) DO UPDATE SET current_count = excluded.current_count
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE current_count = VALUES(current_count)
                """;
            case POSTGRESQL -> """
                INSERT INTO project_quest_progress (project_id, quest_id, current_count)
                VALUES (?, ?, ?)
                ON CONFLICT (project_id, quest_id) DO UPDATE SET current_count = EXCLUDED.current_count
                """;
            case H2 -> """
                MERGE INTO project_quest_progress (project_id, quest_id, current_count)
                KEY (project_id, quest_id) VALUES (?, ?, ?)
                """;
        };
    }

    public static String upsertMaterialContribution(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON CONFLICT(project_id, material) DO UPDATE SET amount = excluded.amount
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                """;
            case POSTGRESQL -> """
                INSERT INTO project_material_contributions (project_id, material, amount)
                VALUES (?, ?, ?)
                ON CONFLICT (project_id, material) DO UPDATE SET amount = EXCLUDED.amount
                """;
            case H2 -> """
                MERGE INTO project_material_contributions (project_id, material, amount)
                KEY (project_id, material) VALUES (?, ?, ?)
                """;
        };
    }

    public static String incrementPoolSeed(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON CONFLICT(guild_id) DO UPDATE SET seed = seed + 1
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON DUPLICATE KEY UPDATE seed = seed + 1
                """;
            case POSTGRESQL -> """
                INSERT INTO guild_project_pool_seed (guild_id, seed)
                VALUES (?, 1)
                ON CONFLICT (guild_id) DO UPDATE SET seed = guild_project_pool_seed.seed + 1
                """;
            case H2 -> """
                MERGE INTO guild_project_pool_seed AS t
                USING (VALUES (?)) AS s(guild_id)
                ON t.guild_id = s.guild_id
                WHEN MATCHED THEN UPDATE SET seed = t.seed + 1
                WHEN NOT MATCHED THEN INSERT (guild_id, seed) VALUES (s.guild_id, 1)
                """;
        };
    }

    // ==================== ACTIVE BUFFS ====================

    public static String createActiveBuffsTable(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                CREATE TABLE IF NOT EXISTS active_buffs (
                    id TEXT PRIMARY KEY,
                    guild_id TEXT NOT NULL,
                    project_definition_id TEXT NOT NULL,
                    buff_category TEXT NOT NULL,
                    buff_value REAL NOT NULL,
                    activated_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                )
                """;
            case MYSQL, MARIADB -> """
                CREATE TABLE IF NOT EXISTS active_buffs (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    project_definition_id VARCHAR(255) NOT NULL,
                    buff_category VARCHAR(64) NOT NULL,
                    buff_value DOUBLE NOT NULL,
                    activated_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """;
            case POSTGRESQL -> """
                CREATE TABLE IF NOT EXISTS active_buffs (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    project_definition_id VARCHAR(255) NOT NULL,
                    buff_category VARCHAR(64) NOT NULL,
                    buff_value DOUBLE PRECISION NOT NULL,
                    activated_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """;
            case H2 -> """
                CREATE TABLE IF NOT EXISTS active_buffs (
                    id VARCHAR(36) PRIMARY KEY,
                    guild_id VARCHAR(36) NOT NULL,
                    project_definition_id VARCHAR(255) NOT NULL,
                    buff_category VARCHAR(64) NOT NULL,
                    buff_value DOUBLE NOT NULL,
                    activated_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL
                )
                """;
        };
    }

    public static String upsertActiveBuff(DatabaseType type) {
        return switch (type) {
            case SQLITE -> """
                INSERT OR REPLACE INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
            case MYSQL, MARIADB -> """
                INSERT INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                buff_value = VALUES(buff_value), activated_at = VALUES(activated_at), expires_at = VALUES(expires_at)
                """;
            case POSTGRESQL -> """
                INSERT INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                buff_value = EXCLUDED.buff_value, activated_at = EXCLUDED.activated_at, expires_at = EXCLUDED.expires_at
                """;
            case H2 -> """
                MERGE INTO active_buffs
                (id, guild_id, project_definition_id, buff_category, buff_value, activated_at, expires_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        };
    }

    // ==================== INDEX CREATION ====================

    public static String createIndex(DatabaseType type, String indexName, String tableName, String... columns) {
        String columnList = String.join(", ", columns);
        return "CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName + "(" + columnList + ")";
    }
}
