UPDATE player_travel_wallets
SET balance = CASE
                 WHEN ? <= 0 OR ? < ? THEN balance
                 WHEN balance > ? - ? THEN ?
                 ELSE balance + ?
             END,
    updated_at = ?
WHERE player_uuid = ?
