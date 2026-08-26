INSERT INTO guild_bank_enrollments (guild_id, player_uuid, active, enrolled_at, updated_at)
VALUES (:guild_id, :player_uuid, TRUE, :now, :now)
ON CONFLICT (guild_id, player_uuid) DO UPDATE SET active = TRUE, updated_at = EXCLUDED.updated_at
WHERE EXISTS (SELECT 1 FROM guild_residents WHERE guild_id = :guild_id AND resident_uuid = :player_uuid)
