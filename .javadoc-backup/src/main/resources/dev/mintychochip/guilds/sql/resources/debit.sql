UPDATE guild_resources SET amount = amount - :amount, last_updated = :last_updated
WHERE guild_id = :guild_id AND resource_type = :resource_type
