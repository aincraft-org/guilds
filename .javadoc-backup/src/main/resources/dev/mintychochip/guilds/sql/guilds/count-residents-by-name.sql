SELECT COUNT(*) FROM guild_residents WHERE guild_id = (SELECT id FROM guilds WHERE name = :name)
