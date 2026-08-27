SELECT COUNT(DISTINCT contributor_uuid), COUNT(*), MAX(contribution_time)
FROM resource_contributions WHERE guild_id = :guild_id
