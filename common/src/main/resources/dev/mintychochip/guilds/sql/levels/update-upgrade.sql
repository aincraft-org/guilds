UPDATE guilds
   SET guild_level = :guild_level, tech_points = :tech_points, upgrade_progress = :upgrade_progress
 WHERE id = :id AND guild_level = :expected_level
