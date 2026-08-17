SELECT * FROM resource_contributions WHERE guild_id = :guild_id AND contribution_time >= :since
ORDER BY contribution_time DESC, id DESC
