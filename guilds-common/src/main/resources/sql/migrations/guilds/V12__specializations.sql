CREATE TABLE IF NOT EXISTS guild_specializations (
    guild_id TEXT PRIMARY KEY,
    specialization TEXT NOT NULL,
    set_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
-- +index idx_guild_specialization guild_specializations (specialization)
