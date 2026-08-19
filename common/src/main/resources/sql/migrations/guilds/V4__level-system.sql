-- +add-column guilds guild_level INTEGER DEFAULT 1
-- +add-column guilds tech_points INTEGER DEFAULT 0
-- +add-column guilds upgrade_progress TEXT DEFAULT '{}'
CREATE TABLE IF NOT EXISTS guild_levels (
    level INTEGER PRIMARY KEY,
    resource_costs_json TEXT NOT NULL DEFAULT '{}',
    tech_points_reward INTEGER NOT NULL DEFAULT 0,
    claim_limit_bonus INTEGER NOT NULL DEFAULT 0,
    assistant_slots_bonus INTEGER NOT NULL DEFAULT 0,
    daily_income_bonus REAL NOT NULL DEFAULT 0.0,
    unlocked_plot_types TEXT DEFAULT '[]',
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS guild_resources (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    amount INTEGER NOT NULL DEFAULT 0,
    last_updated TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    UNIQUE(guild_id, resource_type)
);
CREATE TABLE IF NOT EXISTS resource_contributions (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    contributor_uuid TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    contribution_time TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (contributor_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS guild_level_benefits (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    level INTEGER NOT NULL,
    benefit_type TEXT NOT NULL,
    benefit_value TEXT NOT NULL,
    unlocked_at TEXT NOT NULL,
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    UNIQUE(guild_id, level, benefit_type)
);
-- +index idx_guild_resources_guild guild_resources (guild_id)
-- +index idx_guild_resources_type guild_resources (resource_type)
-- +index idx_resource_contributions_guild resource_contributions (guild_id)
-- +index idx_resource_contributions_contributor resource_contributions (contributor_uuid)
-- +index idx_resource_contributions_time resource_contributions (contribution_time)
-- +index idx_guild_level_benefits_guild guild_level_benefits (guild_id)
-- +index idx_guild_level_benefits_level guild_level_benefits (level)
