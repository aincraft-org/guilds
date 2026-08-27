SELECT id, x, z, world, guild_id, owner_uuid, plot_type, price, permissions_flags, claimed_at, custom_name
FROM guild_blocks WHERE world = :world AND x >= :min_x AND x < :max_x AND z >= :min_z AND z < :max_z ORDER BY x, z
