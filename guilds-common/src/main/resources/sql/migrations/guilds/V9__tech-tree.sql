CREATE TABLE IF NOT EXISTS tech_tree_nodes (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    branch TEXT NOT NULL,
    cost INTEGER NOT NULL DEFAULT 1,
    prerequisites TEXT DEFAULT '[]',
    effects TEXT DEFAULT '{}',
    position_x INTEGER DEFAULT 0,
    position_y INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS guild_unlocked_nodes (
    guild_id TEXT NOT NULL,
    node_id TEXT NOT NULL,
    unlocked_at TEXT NOT NULL,
    PRIMARY KEY (guild_id, node_id),
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE,
    FOREIGN KEY (node_id) REFERENCES tech_tree_nodes(id) ON DELETE CASCADE
);
-- +index idx_tech_nodes_branch tech_tree_nodes (branch)
-- +index idx_guild_unlocked_guild guild_unlocked_nodes (guild_id)
-- +index idx_guild_unlocked_node guild_unlocked_nodes (node_id)
