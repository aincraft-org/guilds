SELECT reservation_id, trip_id, player_uuid, amount, status,
       expires_at, created_at, completed_at, released_at
FROM travel_currency_reservations
WHERE trip_id = ?
