INSERT INTO guilds (id, name, mayor_uuid, balance, is_open, created_at, permissions_flags, tax_rates,
home_block_x, home_block_z, home_block_world,
spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, spawn_world,
pvp_enabled, fire_enabled, explosions_enabled, mobs_enabled, public_enabled)
VALUES (:id, :name, :mayor_uuid, :balance, :is_open, :created_at, :permissions_flags, :tax_rates, :home_block_x, :home_block_z, :home_block_world, :spawn_x, :spawn_y, :spawn_z, :spawn_yaw, :spawn_pitch, :spawn_world, :pvp_enabled, :fire_enabled, :explosions_enabled, :mobs_enabled, :public_enabled)
