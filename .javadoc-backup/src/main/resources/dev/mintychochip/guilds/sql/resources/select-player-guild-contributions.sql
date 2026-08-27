SELECT * FROM resource_contributions WHERE guild_id = :guild_id AND contributor_uuid = :contributor_uuid
ORDER BY contribution_time DESC, id DESC
