INSERT INTO guild_unlocked_nodes (guild_id, node_id, unlocked_at)
VALUES (:guild_id, :node_id, :unlocked_at)
ON CONFLICT (guild_id, node_id) DO UPDATE SET unlocked_at = EXCLUDED.unlocked_at
