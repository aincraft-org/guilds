-- +add-string-column guild_storage_operations.request_item_schema
-- +add-string-column guild_storage_operations.request_item_fingerprint
-- +add-string-column guild_storage_operations.request_item_payload
-- +widen-payload-column guild_storage_operations.request_item_payload

UPDATE guild_storage_operations
SET request_item_schema = result_item_schema,
    request_item_fingerprint = result_item_fingerprint,
    request_item_payload = result_item_payload
WHERE request_item_schema IS NULL
  AND result_item_schema IS NOT NULL;
