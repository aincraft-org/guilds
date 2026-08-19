INSERT INTO {schema}.{table} ({keyColumn}, doc) VALUES (?, ?) ON DUPLICATE KEY UPDATE doc = VALUES(doc)
