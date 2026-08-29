SELECT source, event_id, player_uuid, amount, awarded_at
FROM travel_currency_awards
WHERE source = ? AND event_id = ?
