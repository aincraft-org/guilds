UPDATE guild_blocks SET owner_uuid = :owner_uuid, price = 0.0, permissions_flags = :permissions_flags
WHERE id = :id AND price = :price AND owner_uuid IS NULL
