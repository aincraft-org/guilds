SELECT COUNT(*) FROM guild_residents tr
JOIN guilds t ON tr.guild_id = t.id
WHERE t.name = :name AND tr.resident_uuid = :resident_uuid AND tr.role = 'assistant'
