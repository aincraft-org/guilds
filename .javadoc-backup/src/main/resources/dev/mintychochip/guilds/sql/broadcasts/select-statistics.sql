SELECT COUNT(*) as total,
SUM(CASE WHEN is_active = TRUE AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) THEN 1 ELSE 0 END) as active,
SUM(CASE WHEN is_active = TRUE AND expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP THEN 1 ELSE 0 END) as expired,
SUM(CASE WHEN message_type = 'announcement' THEN 1 ELSE 0 END) as announcements,
SUM(CASE WHEN message_type = 'alert' THEN 1 ELSE 0 END) as alerts,
SUM(CASE WHEN message_type = 'welcome' THEN 1 ELSE 0 END) as welcome,
MAX(created_at) as last_broadcast,
message_type as most_active_type
FROM broadcast_messages WHERE guild_id = :guild_id
GROUP BY message_type ORDER BY COUNT(*) DESC LIMIT 1
