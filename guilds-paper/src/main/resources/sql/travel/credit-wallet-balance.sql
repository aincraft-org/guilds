UPDATE player_travel_wallets
SET balance = CASE
                 WHEN balance + ? > ? THEN ?
                 ELSE balance + ?
             END,
    updated_at = ?
WHERE player_uuid = ?
