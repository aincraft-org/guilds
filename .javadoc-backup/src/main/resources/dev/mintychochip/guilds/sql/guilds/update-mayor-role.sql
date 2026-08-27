UPDATE guild_residents SET role = 'mayor' WHERE guild_id = (SELECT id FROM guilds WHERE name = :name) AND resident_uuid = :resident_uuid
