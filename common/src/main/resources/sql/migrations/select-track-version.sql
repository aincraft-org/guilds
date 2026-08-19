SELECT COALESCE(MAX(version), 0) FROM sql_schema_migrations WHERE track = ?
