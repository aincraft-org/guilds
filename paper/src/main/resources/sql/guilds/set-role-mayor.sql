UPDATE guild_residents SET role = 'mayor' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?
