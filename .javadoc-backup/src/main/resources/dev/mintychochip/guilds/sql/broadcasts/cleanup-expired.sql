UPDATE broadcast_messages SET is_active = FALSE
WHERE is_active = TRUE AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP
