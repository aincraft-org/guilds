SELECT player_uuid, balance, created_at, updated_at
FROM player_travel_wallets
WHERE player_uuid = ?
