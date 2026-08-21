-- +add-string-column guild_storage_payout_obligations.delivery_token

CREATE TABLE IF NOT EXISTS guild_storage_deposit_restoration_obligations (
    deposit_operation_id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    actor_uuid TEXT NOT NULL,
    tab_id TEXT NOT NULL,
    slot_index INTEGER NOT NULL,
    facility_id TEXT NOT NULL,
    item_schema TEXT NOT NULL,
    item_fingerprint TEXT NOT NULL,
    item_payload TEXT NOT NULL,
    status TEXT NOT NULL,
    handoff_token TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (deposit_operation_id) REFERENCES guild_storage_operations(operation_id) ON DELETE CASCADE,
    FOREIGN KEY (guild_id) REFERENCES guild_storage_banks(guild_id) ON DELETE CASCADE
);
-- +index idx_guild_storage_deposit_restoration_status guild_storage_deposit_restoration_obligations (status)
-- +add-string-column guild_storage_deposit_restoration_obligations.handoff_token
