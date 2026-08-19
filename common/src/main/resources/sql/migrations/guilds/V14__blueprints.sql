CREATE TABLE IF NOT EXISTS blueprints (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    author_uuid TEXT NOT NULL,
    guild_id TEXT NOT NULL,
    schematic_data BYTEA,
    created_at TEXT NOT NULL
);
-- +index idx_blueprints_guild blueprints (guild_id)
-- +index idx_blueprints_name blueprints (name)
-- +index idx_blueprints_author blueprints (author_uuid)
