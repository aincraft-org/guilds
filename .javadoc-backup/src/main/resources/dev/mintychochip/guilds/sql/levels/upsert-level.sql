INSERT INTO guild_levels (
    level, resource_costs_json, tech_points_reward, claim_limit_bonus,
    assistant_slots_bonus, daily_income_bonus, unlocked_plot_types, created_at
) VALUES (:level, :resource_costs_json, :tech_points_reward, :claim_limit_bonus,
    :assistant_slots_bonus, :daily_income_bonus, :unlocked_plot_types, :created_at)
ON CONFLICT (level) DO UPDATE SET
    resource_costs_json = EXCLUDED.resource_costs_json,
    tech_points_reward = EXCLUDED.tech_points_reward,
    claim_limit_bonus = EXCLUDED.claim_limit_bonus,
    assistant_slots_bonus = EXCLUDED.assistant_slots_bonus,
    daily_income_bonus = EXCLUDED.daily_income_bonus,
    unlocked_plot_types = EXCLUDED.unlocked_plot_types,
    created_at = EXCLUDED.created_at
