UPDATE guild_bank_enrollments SET active = FALSE, updated_at = :updated_at WHERE guild_id = :guild_id AND player_uuid = :player_uuid AND active = TRUE
