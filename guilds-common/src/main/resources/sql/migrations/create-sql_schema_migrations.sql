CREATE TABLE IF NOT EXISTS sql_schema_migrations (
    track TEXT NOT NULL,
    version INTEGER NOT NULL,
    description TEXT NOT NULL,
    checksum TEXT,
    applied_at TEXT NOT NULL,
    PRIMARY KEY (track, version)
)
