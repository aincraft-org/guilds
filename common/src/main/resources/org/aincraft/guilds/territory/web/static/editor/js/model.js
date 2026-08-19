/** Client-side draft tree for territories and zones (chunk-medium). */

const CHUNK = 16;

export function emptyBoundary() {
  return { polygon: [], chunks: [] };
}

export function cloneBoundary(b) {
  if (!b) return emptyBoundary();
  return {
    polygon: (b.polygon || []).map((p) => ({ x: p.x, z: p.z })),
    chunks: (b.chunks || []).map((c) => ({ x: c.x, z: c.z })),
  };
}

export function cloneTerritory(t) {
  return {
    id: t.id,
    name: t.name,
    world: t.world || 'world',
    defaultZoneType: t.defaultZoneType || 'WILDERNESS',
    boundary: cloneBoundary(t.boundary),
    zones: (t.zones || []).map((z) => ({
      id: z.id,
      name: z.name,
      type: z.type || 'CLAIMABLE',
      priority: z.priority ?? 0,
      boundary: cloneBoundary(z.boundary),
    })),
    // preserve optional fields if present
    governedByGuildId: t.governedByGuildId,
    government: t.government,
    dirty: false,
  };
}

export function loadFromApi(json) {
  const list = (json.territories || []).map(cloneTerritory);
  return { territories: list, dirtyIds: new Set() };
}

export function markDirty(state, territoryId) {
  state.dirtyIds.add(territoryId);
  const t = state.territories.find((x) => x.id === territoryId);
  if (t) t.dirty = true;
}

export function clearDirty(state, territoryId) {
  state.dirtyIds.delete(territoryId);
  const t = state.territories.find((x) => x.id === territoryId);
  if (t) t.dirty = false;
}

export function isDirty(state) {
  return state.dirtyIds.size > 0;
}

export function findTerritory(state, id) {
  return state.territories.find((t) => t.id === id) || null;
}

export function getBoundaryTarget(state, selection) {
  if (!selection) return null;
  const t = findTerritory(state, selection.territoryId);
  if (!t) return null;
  if (selection.zoneId) {
    const z = t.zones.find((z) => z.id === selection.zoneId);
    return z ? { territory: t, zone: z, boundary: z.boundary } : null;
  }
  return { territory: t, zone: null, boundary: t.boundary };
}

/** Snap block coords to chunk corner (floor toward -inf). */
export function snapBlockToChunkCorner(blockX, blockZ) {
  const cx = Math.floor(blockX / CHUNK);
  const cz = Math.floor(blockZ / CHUNK);
  return { x: cx * CHUNK, z: cz * CHUNK, cx, cz };
}

export function blockToChunk(blockX, blockZ) {
  return { x: Math.floor(blockX / CHUNK), z: Math.floor(blockZ / CHUNK) };
}

export function chunkKey(c) {
  return `${c.x},${c.z}`;
}

export function toggleChunk(boundary, cx, cz) {
  const key = `${cx},${cz}`;
  const set = new Map((boundary.chunks || []).map((c) => [chunkKey(c), c]));
  if (set.has(key)) set.delete(key);
  else set.set(key, { x: cx, z: cz });
  boundary.chunks = [...set.values()];
}

export function addChunkRect(boundary, c0, c1) {
  const minX = Math.min(c0.x, c1.x);
  const maxX = Math.max(c0.x, c1.x);
  const minZ = Math.min(c0.z, c1.z);
  const maxZ = Math.max(c0.z, c1.z);
  const set = new Map((boundary.chunks || []).map((c) => [chunkKey(c), c]));
  for (let x = minX; x <= maxX; x++) {
    for (let z = minZ; z <= maxZ; z++) {
      set.set(`${x},${z}`, { x, z });
    }
  }
  boundary.chunks = [...set.values()];
}

export function eraseChunk(boundary, cx, cz) {
  boundary.chunks = (boundary.chunks || []).filter((c) => !(c.x === cx && c.z === cz));
}

export function removePolygonVertexNear(boundary, blockX, blockZ, threshold = 8) {
  const poly = boundary.polygon || [];
  let best = -1;
  let bestD = threshold * threshold;
  for (let i = 0; i < poly.length; i++) {
    const dx = poly[i].x - blockX;
    const dz = poly[i].z - blockZ;
    const d = dx * dx + dz * dz;
    if (d <= bestD) {
      bestD = d;
      best = i;
    }
  }
  if (best >= 0) {
    poly.splice(best, 1);
    boundary.polygon = poly;
    return true;
  }
  return false;
}

export function boundaryStats(b) {
  return {
    chunks: (b.chunks || []).length,
    vertices: (b.polygon || []).length,
  };
}

export function hasGeometry(b) {
  return (b.polygon || []).length >= 3 || (b.chunks || []).length >= 1;
}

/** Serialize territory for PUT (omit dirty flag). */
export function toApiDocument(t) {
  const doc = {
    id: t.id,
    name: t.name,
    world: t.world,
    defaultZoneType: t.defaultZoneType || 'WILDERNESS',
    boundary: {
      polygon: t.boundary.polygon || [],
      chunks: t.boundary.chunks || [],
    },
    zones: (t.zones || []).map((z) => ({
      id: z.id,
      name: z.name,
      type: z.type,
      priority: z.priority ?? 0,
      boundary: {
        polygon: z.boundary.polygon || [],
        chunks: z.boundary.chunks || [],
      },
    })),
  };
  if (t.governedByGuildId != null) doc.governedByGuildId = t.governedByGuildId;
  if (t.government != null) doc.government = t.government;
  return doc;
}

export function newTerritoryId(name) {
  return String(name || 'territory')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '') || 'territory';
}
