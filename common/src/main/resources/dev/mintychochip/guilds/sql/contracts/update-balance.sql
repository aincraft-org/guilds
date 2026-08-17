UPDATE guilds SET balance = balance + :delta WHERE id = :guild_id RETURNING balance
