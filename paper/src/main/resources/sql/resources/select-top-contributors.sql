SELECT contributor_uuid, SUM(amount) AS total FROM resource_contributions WHERE guild_id = ? GROUP BY contributor_uuid ORDER BY total DESC, contributor_uuid
