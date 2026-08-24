SELECT * FROM broadcast_messages WHERE guild_id = ? AND message_type = ? AND is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) ORDER BY priority DESC, created_at DESC
