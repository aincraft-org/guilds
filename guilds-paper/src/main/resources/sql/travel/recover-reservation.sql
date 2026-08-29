UPDATE travel_currency_reservations
SET status = 'RELEASED', released_at = ?
WHERE reservation_id = ? AND status = 'RESERVED'
