UPDATE guild_residents SET role = 'resident' WHERE guild_id = (SELECT id FROM guilds WHERE name = ?) AND resident_uuid = ?
