UPDATE alliances
SET name = :name, capital_guild_id = :capital_guild_id, king_uuid = :king_uuid, tax_rate = :tax_rate, is_open = :is_open
WHERE id = :id
