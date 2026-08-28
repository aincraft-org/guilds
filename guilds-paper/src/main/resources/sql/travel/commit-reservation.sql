UPDATE travel_currency_reservations
SET status = 'COMMITTED', completed_at = ?
WHERE reservation_id = ? AND status = 'RESERVED'
