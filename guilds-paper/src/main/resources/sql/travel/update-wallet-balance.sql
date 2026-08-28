UPDATE player_travel_wallets
SET balance = balance - ?, updated_at = ?
WHERE player_uuid = ? AND balance >= ?
