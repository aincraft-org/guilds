SELECT t.id, t.name, t.mayor_uuid, t.balance, t.home_block_x, t.home_block_z, t.home_block_world,
          t.spawn_x, t.spawn_y, t.spawn_z, t.spawn_yaw, t.spawn_pitch, t.spawn_world,
          t.is_open, t.created_at, t.permissions_flags, t.tax_rates,
          t.guild_level, t.tech_points, t.active_project_id,
          COUNT(tr.resident_uuid) as resident_count
      FROM guilds t
      LEFT JOIN guild_residents tr ON t.id = tr.guild_id
      GROUP BY t.id
      ORDER BY resident_count DESC
