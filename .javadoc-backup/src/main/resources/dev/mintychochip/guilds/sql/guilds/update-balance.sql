UPDATE guilds SET balance = balance + :amount WHERE name = :name RETURNING balance
