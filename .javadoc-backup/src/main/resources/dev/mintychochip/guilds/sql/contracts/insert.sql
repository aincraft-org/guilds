INSERT INTO guild_contracts (id, contracting_guild_id, resource_type, amount, payment,
    filled, status, created_at)
VALUES (:id, :contracting_guild_id, :resource_type, :amount, :payment, 0, 'OPEN', :created_at)
