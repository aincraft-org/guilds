SELECT permissions_flags FROM permissions WHERE
context = 'town' AND context_id = :context_id AND
(target_type = 'all' OR (target_type = 'resident' AND target_id = :target_id))
