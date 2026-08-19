CREATE TABLE IF NOT EXISTS {schema}.reconciliation_entries (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)
