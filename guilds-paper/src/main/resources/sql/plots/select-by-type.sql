SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name FROM guild_blocks WHERE plot_type = ? ORDER BY world, x, z
