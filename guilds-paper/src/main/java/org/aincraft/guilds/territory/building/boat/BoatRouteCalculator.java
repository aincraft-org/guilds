package org.aincraft.guilds.territory.building.boat;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Performs bounded, Paper-free connectivity analysis over immutable snapshots.
 * The search deliberately keeps no predecessor/path data.
 */
public class BoatRouteCalculator {
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public BoatRouteResult calculate(Collection<BoatWaterSnapshot> snapshots,
                                     BoatWaterMask.Cell origin,
                                     BoatWaterMask.Cell destination,
                                     int chunkBudget,
                                     int nodeBudget) {
        if (snapshots == null || origin == null || destination == null) {
            return BoatRouteResult.unavailable();
        }
        SnapshotIndex index = index(snapshots);
        if (index.worldId() == null || !index.valid()) {
            return BoatRouteResult.unavailable();
        }
        Node originNode = index.nodes().get(origin);
        Node destinationNode = index.nodes().get(destination);
        if (originNode == null || destinationNode == null
                || !index.clearAt(origin) || !index.clearAt(destination)) {
            return BoatRouteResult.unavailable();
        }
        if (origin.equals(destination)) {
            return BoatRouteResult.connected(0.0);
        }
        if (chunkBudget <= 0 || nodeBudget <= 0) {
            return BoatRouteResult.pending();
        }
        if (index.chunks().size() > chunkBudget) {
            return BoatRouteResult.pending();
        }

        Set<BoatWaterMask.Chunk> usedChunks = new HashSet<>();
        if (!useChunk(originNode, usedChunks, chunkBudget)
                || !useChunk(destinationNode, usedChunks, chunkBudget)) {
            return BoatRouteResult.pending();
        }

        PriorityQueue<SearchNode> frontier = new PriorityQueue<>(
                Comparator.comparingDouble(SearchNode::priority)
                        .thenComparingInt(node -> node.cell().x())
                        .thenComparingInt(node -> node.cell().z())
                        .thenComparingInt(node -> node.cell().y()));
        Map<BoatWaterMask.Cell, Double> bestDistances = new HashMap<>();
        frontier.add(new SearchNode(origin, 0.0,
                heuristic(origin, destination)));
        bestDistances.put(origin, 0.0);
        int discovered = 1;

        while (!frontier.isEmpty()) {
            SearchNode current = frontier.poll();
            double known = bestDistances.getOrDefault(current.cell(), Double.POSITIVE_INFINITY);
            if (current.distance() > known) {
                continue;
            }
            if (current.cell().equals(destination)) {
                return BoatRouteResult.connected(current.distance());
            }
            for (int[] direction : DIRECTIONS) {
                BoatWaterMask.Cell next = new BoatWaterMask.Cell(
                        current.cell().x() + direction[0],
                        current.cell().y(),
                        current.cell().z() + direction[1]);
                Node nextNode = index.nodes().get(next);
                if (nextNode == null) {
                    continue;
                }
                double nextDistance = current.distance() + 1.0;
                if (nextDistance >= bestDistances.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    continue;
                }
                if (!usedChunks.contains(nextNode.chunk())
                        && usedChunks.size() >= chunkBudget) {
                    return BoatRouteResult.pending();
                }
                if (discovered >= nodeBudget) {
                    return BoatRouteResult.pending();
                }
                useChunk(nextNode, usedChunks, chunkBudget);
                bestDistances.put(next, nextDistance);
                frontier.add(new SearchNode(next, nextDistance,
                        nextDistance + heuristic(next, destination)));
                discovered++;
            }
        }
        return BoatRouteResult.disconnected();
    }

    public BoatRouteResult calculate(List<BoatWaterSnapshot> snapshots,
                                     BoatWaterMask.Cell origin,
                                     BoatWaterMask.Cell destination,
                                     int chunkBudget,
                                     int nodeBudget) {
        return calculate((Collection<BoatWaterSnapshot>) snapshots,
                origin, destination, chunkBudget, nodeBudget);
    }

    public BoatRouteResult calculate(Map<BoatWaterMask.Chunk, BoatWaterSnapshot> snapshots,
                                     BoatWaterMask.Cell origin,
                                     BoatWaterMask.Cell destination,
                                     int chunkBudget,
                                     int nodeBudget) {
        return calculate(snapshots == null ? null : snapshots.values(),
                origin, destination, chunkBudget, nodeBudget);
    }

    private static boolean useChunk(Node node, Set<BoatWaterMask.Chunk> usedChunks,
                                    int chunkBudget) {
        if (usedChunks.contains(node.chunk())) {
            return true;
        }
        if (usedChunks.size() >= chunkBudget) {
            return false;
        }
        usedChunks.add(node.chunk());
        return true;
    }

    private static double heuristic(BoatWaterMask.Cell from, BoatWaterMask.Cell to) {
        return Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z());
    }

    private static SnapshotIndex index(Collection<BoatWaterSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return SnapshotIndex.invalid();
        }
        UUID worldId = null;
        Map<BoatWaterMask.Cell, Node> nodes = new HashMap<>();
        Map<BoatWaterMask.Chunk, BoatWaterSnapshot> chunks = new HashMap<>();
        Set<BoatWaterMask.Cell> clearCells = new HashSet<>();
        boolean valid = true;
        for (BoatWaterSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                valid = false;
                continue;
            }
            if (worldId == null) {
                worldId = snapshot.worldId();
            } else if (!worldId.equals(snapshot.worldId())) {
                valid = false;
            }
            BoatWaterMask.Chunk chunk = snapshot.chunk();
            if (chunks.putIfAbsent(chunk, snapshot) != null) {
                valid = false;
            }
            for (BoatWaterMask.Cell cell : snapshot.waterMask().navigableSurfaceCells()) {
                if (nodes.putIfAbsent(cell, new Node(cell.chunk())) != null) {
                    valid = false;
                }
            }
            clearCells.addAll(snapshot.endpointClearSpaceCells());
        }
        return new SnapshotIndex(worldId, Map.copyOf(nodes), Set.copyOf(clearCells),
                Set.copyOf(chunks.keySet()), valid);
    }

    private record Node(BoatWaterMask.Chunk chunk) {
    }

    private record SearchNode(BoatWaterMask.Cell cell, double distance, double priority) {
    }

    private record SnapshotIndex(UUID worldId,
                                 Map<BoatWaterMask.Cell, Node> nodes,
                                 Set<BoatWaterMask.Cell> clearCells,
                                 Set<BoatWaterMask.Chunk> chunks,
                                 boolean valid) {
        private static SnapshotIndex invalid() {
            return new SnapshotIndex(null, Map.of(), Set.of(), Set.of(), false);
        }

        private boolean clearAt(BoatWaterMask.Cell cell) {
            if (clearCells.isEmpty()) {
                return true;
            }
            return clearCells.contains(cell);
        }
    }
}
