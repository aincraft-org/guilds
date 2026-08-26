INSERT INTO tech_tree_nodes
    (id, name, branch, cost, prerequisites, effects, position_x, position_y)
VALUES (:id, :name, :branch, :cost, :prerequisites, :effects, :position_x, :position_y)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    branch = EXCLUDED.branch,
    cost = EXCLUDED.cost,
    prerequisites = EXCLUDED.prerequisites,
    effects = EXCLUDED.effects,
    position_x = EXCLUDED.position_x,
    position_y = EXCLUDED.position_y
