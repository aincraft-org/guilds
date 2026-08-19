CREATE TABLE IF NOT EXISTS guild_contracts (
    id TEXT PRIMARY KEY,
    contracting_guild_id TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    payment REAL NOT NULL,
    filled INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OPEN',
    fulfilled_by_guild_id TEXT,
    created_at TEXT NOT NULL,
    fulfilled_at TEXT,
    FOREIGN KEY (contracting_guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
-- +index idx_guild_contracts_status guild_contracts (status)
-- +index idx_guild_contracts_contracting guild_contracts (contracting_guild_id)
-- +index idx_guild_contracts_fulfilled_by guild_contracts (fulfilled_by_guild_id)
