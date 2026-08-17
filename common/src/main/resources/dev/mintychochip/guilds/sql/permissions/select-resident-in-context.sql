SELECT id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid
FROM permissions WHERE context = :context AND context_id = :context_id AND (target_type = 'all' OR (target_type = 'resident' AND target_id = :target_id))
