SELECT * FROM resource_contributions WHERE guild_id = :guild_id
ORDER BY contribution_time DESC, id DESC
