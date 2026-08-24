CREATE TABLE IF NOT EXISTS guild_storage_operations (
    operation_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    operation_type TEXT NOT NULL,
    actor_uuid TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INTEGER NOT NULL,
    facility_id TEXT NOT NULL,
    status TEXT NOT NULL,
    result_status TEXT,
    result_error TEXT,
    result_item_schema TEXT,
    result_item_fingerprint TEXT,
    result_item_payload TEXT,
    result_slot_version BIGINT,
    result_slot_updated_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);
-- +index idx_guild_storage_operations_status guild_storage_operations (status)
-- +index idx_guild_storage_operations_guild guild_storage_operations (guild_id, status)
