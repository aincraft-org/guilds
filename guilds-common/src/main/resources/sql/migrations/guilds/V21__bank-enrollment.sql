CREATE TABLE IF NOT EXISTS guild_bank_enrollments (
    guild_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    enrolled_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, player_uuid),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (player_uuid) REFERENCES residents(uuid) ON DELETE CASCADE
);
-- +index idx_guild_bank_enrollments_player guild_bank_enrollments (player_uuid)
-- +index idx_guild_bank_enrollments_active guild_bank_enrollments (guild_id, player_uuid, active)
