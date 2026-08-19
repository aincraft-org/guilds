CREATE TABLE IF NOT EXISTS {schema}.expenses (idempotency_key TEXT PRIMARY KEY, doc JSONB NOT NULL)
