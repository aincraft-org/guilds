SELECT n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at,
       STRING_AGG(DISTINCT nm.guild_id, ',') as member_guilds,
       STRING_AGG(DISTINCT nmin.player_uuid, ',') as ministers,
       STRING_AGG(DISTINCT nr.other_alliance, ',') as relations,
       STRING_AGG(DISTINCT nr.relation_type, ',') as relation_types
FROM alliances n
LEFT JOIN alliance_members nm ON n.id = nm.alliance_id
LEFT JOIN alliance_ministers nmin ON n.id = nmin.alliance_id
LEFT JOIN alliance_relations nr ON n.id = nr.alliance_id
GROUP BY n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at
