UPDATE guild_residents SET role = 'assistant' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?
