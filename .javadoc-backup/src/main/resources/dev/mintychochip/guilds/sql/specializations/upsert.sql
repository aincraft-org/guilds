INSERT INTO guild_specializations
(guild_id, specialization, set_at)
VALUES (:guild_id, :specialization, :set_at)
ON CONFLICT (guild_id) DO UPDATE SET
    specialization = EXCLUDED.specialization,
    set_at = EXCLUDED.set_at
