SELECT doc::jsonb
FROM items
WHERE owner_id = :owner_id
  AND tag IN (:tags)
  AND label = :owner_id
