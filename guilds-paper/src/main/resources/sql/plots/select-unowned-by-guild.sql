SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name FROM guild_blocks WHERE guild_id = ? AND owner_uuid IS NULL ORDER BY x, z
