UPDATE guilds SET balance = balance - :amount WHERE id = :guild_id AND balance >= :amount
