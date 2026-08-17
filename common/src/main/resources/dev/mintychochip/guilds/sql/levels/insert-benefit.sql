INSERT INTO guild_level_benefits
    (id, guild_id, level, benefit_type, benefit_value, unlocked_at)
VALUES (:id, :guild_id, :level, :benefit_type, :benefit_value, :unlocked_at)
ON CONFLICT (guild_id, level, benefit_type) DO NOTHING
