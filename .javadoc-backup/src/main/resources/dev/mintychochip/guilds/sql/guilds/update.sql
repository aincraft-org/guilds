UPDATE guilds SET name = :name, mayor_uuid = :mayor_uuid, balance = :balance, home_block_x = :home_block_x,
home_block_z = :home_block_z, home_block_world = :home_block_world, spawn_x = :spawn_x, spawn_y = :spawn_y, spawn_z = :spawn_z,
spawn_yaw = :spawn_yaw, spawn_pitch = :spawn_pitch, spawn_world = :spawn_world, is_open = :is_open, permissions_flags = :permissions_flags, tax_rates = :tax_rates,
pvp_enabled = :pvp_enabled, fire_enabled = :fire_enabled, explosions_enabled = :explosions_enabled, mobs_enabled = :mobs_enabled, public_enabled = :public_enabled WHERE id = :id
