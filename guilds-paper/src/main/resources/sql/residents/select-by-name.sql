SELECT uuid, name, guild_name, last_online, is_online, joined_at, permissions_flags FROM residents WHERE LOWER(name) = LOWER(?)
