SELECT id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid
FROM permissions WHERE context = :context AND context_id = :context_id ORDER BY target_type, target_id
