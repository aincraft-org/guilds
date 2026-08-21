CREATE TABLE IF NOT EXISTS guild_storage_banks (
    guild_id TEXT PRIMARY KEY,
    schema_version INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_tabs (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    ordinal INTEGER NOT NULL,
    capacity_slots INTEGER NOT NULL,
    unlocked BOOLEAN NOT NULL,
    PRIMARY KEY (guild_id, tab_id),
    UNIQUE (guild_id, ordinal),
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_slots (
    guild_id TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INTEGER NOT NULL,
    item_schema TEXT NOT NULL,
    item_fingerprint TEXT NOT NULL,
    item_payload TEXT NOT NULL,
    version BIGINT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, tab_id, slot_index),
    FOREIGN KEY (guild_id, tab_id) REFERENCES guild_storage_tabs(guild_id, tab_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_policies (
    guild_id TEXT PRIMARY KEY,
    deposit_role TEXT NOT NULL,
    withdraw_role TEXT NOT NULL,
    manage_role TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS guild_storage_audit (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    actor_uuid TEXT,
    operation TEXT NOT NULL,
    tab_id TEXT,
    slot_index INTEGER,
    fingerprint TEXT,
    facility_id TEXT,
    recorded_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);
-- +index idx_guild_storage_tabs_ordinal guild_storage_tabs (guild_id, ordinal)
-- +index idx_guild_storage_slots_tab guild_storage_slots (guild_id, tab_id)
-- +index idx_guild_storage_audit_guild_time guild_storage_audit (guild_id, recorded_at)
