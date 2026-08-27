DELETE FROM permissions WHERE context = 'plot' AND context_id = :context_id AND target_type = :target_type AND target_id = :target_id AND permissions_flags = :permissions_flags
