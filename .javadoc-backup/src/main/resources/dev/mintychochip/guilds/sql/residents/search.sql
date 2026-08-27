SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags
FROM residents WHERE name LIKE :name_prefix ORDER BY name LIMIT :limit
