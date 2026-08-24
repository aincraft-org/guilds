UPDATE guild_blocks
SET permissions_flags = 15
WHERE owner_uuid IS NULL
AND (permissions_flags = 0 OR permissions_flags IS NULL);
UPDATE guild_blocks
SET permissions_flags = 65535
WHERE owner_uuid IS NOT NULL
AND (permissions_flags = 0 OR permissions_flags IS NULL);
UPDATE guild_blocks
SET permissions_flags = 13
WHERE plot_type = 'shop'
AND owner_uuid IS NULL
AND (permissions_flags = 15 OR permissions_flags = 0);
UPDATE guild_blocks
SET permissions_flags = 15
WHERE plot_type = 'farm'
AND owner_uuid IS NULL
AND (permissions_flags = 0 OR permissions_flags = 15);
UPDATE guild_blocks
SET permissions_flags = 15
WHERE permissions_flags IS NULL OR permissions_flags = 0;
