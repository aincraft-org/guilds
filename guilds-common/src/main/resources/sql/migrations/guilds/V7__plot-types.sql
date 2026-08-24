CREATE TABLE IF NOT EXISTS plot_type_definitions (
    type_name TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    description TEXT,
    plugin_name TEXT,
    metadata TEXT,
    permissions TEXT,
    is_enabled BOOLEAN DEFAULT TRUE,
    created_at TEXT NOT NULL
);
-- +add-string-column guild_blocks.plot_type_definition
-- +index idx_plot_type_definitions_plugin plot_type_definitions (plugin_name)
-- +index idx_plot_type_definitions_enabled plot_type_definitions (is_enabled)
-- +index idx_guild_blocks_plot_type_def guild_blocks (plot_type_definition)
