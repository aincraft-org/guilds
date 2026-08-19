CREATE TABLE IF NOT EXISTS nations (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    king_uuid TEXT NOT NULL,
    capital_guild_id TEXT NOT NULL,
    tax_rate REAL DEFAULT 0.0,
    is_open INTEGER DEFAULT 0,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS nation_members (
    nation_id TEXT NOT NULL,
    guild_id TEXT NOT NULL,
    PRIMARY KEY (nation_id, guild_id)
);
CREATE TABLE IF NOT EXISTS nation_ministers (
    nation_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    PRIMARY KEY (nation_id, player_uuid)
);
CREATE TABLE IF NOT EXISTS nation_relations (
    nation_id TEXT NOT NULL,
    other_nation TEXT NOT NULL,
    relation_type TEXT NOT NULL,
    PRIMARY KEY (nation_id, other_nation)
);
-- +index idx_nations_capital nations (capital_guild_id)
-- +index idx_nations_king nations (king_uuid)
-- +index idx_nation_members_nation nation_members (nation_id)
-- +index idx_nation_members_guild nation_members (guild_id)
-- +index idx_nation_ministers_nation nation_ministers (nation_id)
-- +index idx_nation_relations_nation nation_relations (nation_id)
-- +index idx_nation_relations_type nation_relations (relation_type)
