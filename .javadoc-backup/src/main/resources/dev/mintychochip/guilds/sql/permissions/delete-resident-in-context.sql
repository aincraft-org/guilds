DELETE FROM permissions WHERE context = :context AND context_id = :context_id
AND target_type = 'resident' AND target_id = :target_id
