SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name
FROM guild_blocks WHERE price > 0 ORDER BY world, price, x, z
