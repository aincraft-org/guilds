INSERT INTO {schema}.{table} ({idColumn}, doc) VALUES (1, ?) ON DUPLICATE KEY UPDATE doc = VALUES(doc)
