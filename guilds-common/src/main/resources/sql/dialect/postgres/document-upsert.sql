INSERT INTO {schema}.{table} ({keyColumn}, doc) VALUES (?, ?::jsonb) ON CONFLICT ({keyColumn}) DO UPDATE SET doc = EXCLUDED.doc
