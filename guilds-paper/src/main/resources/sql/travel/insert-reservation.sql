INSERT INTO travel_currency_reservations
    (reservation_id, trip_id, player_uuid, amount, status, expires_at, created_at)
VALUES (?, ?, ?, ?, 'RESERVED', ?, ?)
