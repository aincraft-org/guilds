CREATE TABLE IF NOT EXISTS {schema}.expenses (idempotency_key VARCHAR(255) PRIMARY KEY, doc JSON NOT NULL)
