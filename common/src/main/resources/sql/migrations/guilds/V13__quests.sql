CREATE TABLE IF NOT EXISTS guild_quests (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    quest_type TEXT NOT NULL,
    description TEXT NOT NULL,
    target_amount INTEGER NOT NULL DEFAULT 1,
    current_progress INTEGER NOT NULL DEFAULT 0,
    tech_point_reward INTEGER NOT NULL DEFAULT 0,
    is_active INTEGER NOT NULL DEFAULT 1,
    is_completed INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    completed_at TEXT,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
-- +index idx_guild_quests_guild_id guild_quests (guild_id)
