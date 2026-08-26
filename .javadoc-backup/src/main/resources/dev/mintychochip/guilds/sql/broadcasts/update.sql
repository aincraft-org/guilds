UPDATE broadcast_messages SET title = :title, content = :content, expires_at = :expires_at,
is_active = :is_active, priority = :priority, target_audience = :target_audience WHERE id = :id
