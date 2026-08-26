SELECT resource_type, SUM(amount) AS total FROM resource_contributions
WHERE contributor_uuid = :contributor_uuid GROUP BY resource_type ORDER BY resource_type
