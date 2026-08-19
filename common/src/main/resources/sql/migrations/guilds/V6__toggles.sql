-- +add-column guilds pvp_enabled BOOLEAN DEFAULT FALSE
-- +add-column guilds fire_enabled BOOLEAN DEFAULT FALSE
-- +add-column guilds explosions_enabled BOOLEAN DEFAULT FALSE
-- +add-column guilds mobs_enabled BOOLEAN DEFAULT TRUE
-- +add-column guilds public_enabled BOOLEAN DEFAULT FALSE
UPDATE guilds
SET pvp_enabled = FALSE,
    fire_enabled = FALSE,
    explosions_enabled = FALSE,
    mobs_enabled = TRUE,
    public_enabled = FALSE
WHERE pvp_enabled IS NULL;
