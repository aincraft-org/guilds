INSERT INTO {schema}.{table} ({idColumn}, doc) VALUES (1, ?::jsonb) ON CONFLICT ({idColumn}) DO UPDATE SET doc = EXCLUDED.doc
