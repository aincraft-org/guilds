CREATE TABLE IF NOT EXISTS broadcast_messages (
    id TEXT PRIMARY KEY,
    guild_id TEXT NOT NULL,
    message_type TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    sender_uuid TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    expires_at TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 1,
    target_audience TEXT DEFAULT 'all',
    FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS broadcast_read_status (
    id TEXT PRIMARY KEY,
    broadcast_id TEXT NOT NULL,
    resident_uuid TEXT NOT NULL,
    read_at TEXT NOT NULL,
    FOREIGN KEY (broadcast_id) REFERENCES broadcast_messages(id) ON DELETE CASCADE,
    FOREIGN KEY (resident_uuid) REFERENCES residents(uuid) ON DELETE CASCADE,
    UNIQUE(broadcast_id, resident_uuid)
);
-- +index idx_broadcast_messages_guild broadcast_messages (guild_id)
-- +index idx_broadcast_messages_type broadcast_messages (message_type)
-- +index idx_broadcast_messages_active broadcast_messages (is_active)
-- +index idx_broadcast_messages_priority broadcast_messages (priority)
-- +index idx_broadcast_messages_expires broadcast_messages (expires_at)
-- +index idx_broadcast_messages_audience broadcast_messages (target_audience)
-- +index idx_broadcast_read_status_broadcast broadcast_read_status (broadcast_id)
-- +index idx_broadcast_read_status_resident broadcast_read_status (resident_uuid)
