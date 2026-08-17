INSERT INTO guild_resources (id, guild_id, resource_type, amount, last_updated)
VALUES (:id, :guild_id, :resource_type, :amount, :last_updated)
ON CONFLICT (guild_id, resource_type) DO UPDATE SET
    amount = guild_resources.amount + EXCLUDED.amount,
    last_updated = EXCLUDED.last_updated
