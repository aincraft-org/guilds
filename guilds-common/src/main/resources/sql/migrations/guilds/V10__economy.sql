CREATE TABLE IF NOT EXISTS economy_transactions (
    id TEXT PRIMARY KEY,
    guild_id TEXT,
    player_uuid TEXT,
    type TEXT NOT NULL,
    amount REAL NOT NULL,
    description TEXT,
    timestamp TEXT NOT NULL
);
-- +index idx_economy_tx_guild economy_transactions (guild_id)
-- +index idx_economy_tx_player economy_transactions (player_uuid)
-- +index idx_economy_tx_type economy_transactions (type)
-- +index idx_economy_tx_timestamp economy_transactions (timestamp)
