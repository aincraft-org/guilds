CREATE TABLE IF NOT EXISTS {schema}.reconciliation_entries (idempotency_key VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)
