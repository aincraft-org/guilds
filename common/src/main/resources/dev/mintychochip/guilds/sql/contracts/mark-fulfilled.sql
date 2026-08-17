UPDATE guild_contracts
SET filled = amount, status = 'FULFILLED', fulfilled_by_guild_id = :fulfilling_guild_id, fulfilled_at = :fulfilled_at
WHERE id = :id
