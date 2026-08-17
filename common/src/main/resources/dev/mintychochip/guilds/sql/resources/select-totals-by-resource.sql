SELECT resource_type, SUM(amount) AS total FROM resource_contributions
WHERE guild_id = :guild_id GROUP BY resource_type ORDER BY resource_type
