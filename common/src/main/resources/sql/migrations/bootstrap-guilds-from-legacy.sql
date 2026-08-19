INSERT INTO sql_schema_migrations (track, version, description, checksum, applied_at)
SELECT 'guilds', version, description, checksum, applied_at
FROM schema_migrations
WHERE NOT EXISTS (
    SELECT 1 FROM sql_schema_migrations existing
    WHERE existing.track = 'guilds' AND existing.version = schema_migrations.version
)
