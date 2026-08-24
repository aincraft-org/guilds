SELECT n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at,
       {membersAgg} as member_guilds,
       {ministersAgg} as ministers,
       {relationsAgg} as relations,
       {relationTypesAgg} as relation_types
FROM alliances n
LEFT JOIN alliance_members nm ON n.id = nm.alliance_id
LEFT JOIN alliance_ministers nmin ON n.id = nmin.alliance_id
LEFT JOIN alliance_relations nr ON n.id = nr.alliance_id
GROUP BY n.id, n.name, n.capital_guild_id, n.king_uuid, n.tax_rate, n.is_open, n.created_at
