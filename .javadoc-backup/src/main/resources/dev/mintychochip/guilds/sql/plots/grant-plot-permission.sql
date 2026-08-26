INSERT INTO permissions (id, context, context_id, target_type, target_id, permissions_flags, granted_at, granted_by_uuid)
VALUES (:id, 'plot', :context_id, :target_type, :target_id, :permissions_flags, :granted_at, :granted_by_uuid)
ON CONFLICT (id) DO UPDATE SET permissions_flags = EXCLUDED.permissions_flags,
granted_at = EXCLUDED.granted_at, granted_by_uuid = EXCLUDED.granted_by_uuid
