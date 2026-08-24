CREATE TABLE IF NOT EXISTS residents (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    guild_name TEXT,
    last_online INTEGER NOT NULL,
    is_online BOOLEAN DEFAULT FALSE,
    joined_at TEXT NOT NULL,
    permissions_flags INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS guilds (
    id TEXT PRIMARY KEY,
    name TEXT UNIQUE NOT NULL,
    mayor_uuid TEXT NOT NULL,
    balance REAL DEFAULT 0.0,
    home_block_x INTEGER,
    home_block_z INTEGER,
    home_block_world TEXT,
    is_open BOOLEAN DEFAULT TRUE,
    created_at TEXT NOT NULL,
    permissions_flags INTEGER DEFAULT 0,
    tax_rates TEXT,
    FOREIGN KEY (mayor_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
);
CREATE TABLE IF NOT EXISTS guild_residents (
    guild_id TEXT,
    resident_uuid TEXT,
    role TEXT DEFAULT 'resident',
    joined_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, resident_uuid),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS guild_blocks (
    id TEXT PRIMARY KEY,
    x INTEGER NOT NULL,
    z INTEGER NOT NULL,
    world TEXT NOT NULL,
    guild_id TEXT,
    owner_uuid TEXT,
    plot_type TEXT DEFAULT 'default',
    price REAL DEFAULT 0.0,
    permissions_flags INTEGER DEFAULT 0,
    claimed_at TEXT NOT NULL,
    custom_name TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE SET NULL,
    FOREIGN KEY (owner_uuid) REFERENCES residents(uuid) ON DELETE SET NULL,
    UNIQUE(x, z, world)
);
CREATE TABLE IF NOT EXISTS permissions (
    id TEXT PRIMARY KEY,
    context TEXT NOT NULL,
    context_id TEXT NOT NULL,
    target_type TEXT NOT NULL,
    target_id TEXT,
    permissions_flags INTEGER NOT NULL,
    granted_at TEXT NOT NULL,
    granted_by_uuid TEXT,
    FOREIGN KEY (granted_by_uuid) REFERENCES residents(uuid) ON DELETE SET NULL
);
-- +index idx_residents_guild residents (guild_name)
-- +index idx_guilds_name guilds (name)
-- +index idx_guild_blocks_location guild_blocks (x, z, world)
-- +index idx_guild_blocks_guild guild_blocks (guild_id)
-- +index idx_guild_blocks_owner guild_blocks (owner_uuid)
-- +index idx_permissions_context permissions (context, context_id)
-- +index idx_permissions_target permissions (target_type, target_id)
