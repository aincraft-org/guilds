SELECT id, permissions_flags FROM permissions WHERE
context = 'town' AND context_id = :context_id AND target_type = :target_type AND target_id = :target_id
