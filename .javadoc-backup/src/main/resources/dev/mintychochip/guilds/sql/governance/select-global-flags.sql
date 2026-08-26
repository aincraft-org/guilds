SELECT permissions_flags FROM permissions WHERE context = 'global'
AND (target_type = 'all' OR (target_type = 'resident' AND target_id = :target_id))
