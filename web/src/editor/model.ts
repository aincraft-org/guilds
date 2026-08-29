import type {
  BlockPoint,
  Boundary,
  EditorState,
  Selection,
  TerritoryDocument,
  TerritoryDraft,
  ZoneDocument,
  ZoneDraft,
} from './types';

const CHUNK_SIZE = 16;

type ApiChunkPoint = { cx: number; cz: number } | { x: number; z: number };
type BoundaryInput = {
  polygon?: readonly BlockPoint[];
  chunks?: readonly ApiChunkPoint[];
};
type TerritoryInput = Omit<TerritoryDocument, 'boundary' | 'zones'> & {
  boundary?: BoundaryInput;
  zones?: readonly (Omit<ZoneDocument, 'boundary'> & { boundary?: BoundaryInput })[];
};

/** Clone JSON-like values such as the optional government document. */
function cloneValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(cloneValue);
  if (value !== null && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
      result[key] = cloneValue(child);
    }
    return result;
  }
  return value;
}

function chunkFromApi(chunk: ApiChunkPoint): { x: number; z: number } {
  if ('cx' in chunk && 'cz' in chunk) return { x: chunk.cx, z: chunk.cz };
  return { x: chunk.x, z: chunk.z };
}

export function emptyBoundary(): Boundary {
  return { polygon: [], chunks: [] };
}

export function cloneBoundary(boundary?: BoundaryInput | null): Boundary {
  if (!boundary) return emptyBoundary();
  return {
    polygon: (boundary.polygon ?? []).map(({ x, z }) => ({ x, z })),
    chunks: (boundary.chunks ?? []).map(chunkFromApi),
  };
}

export function cloneTerritory(territory: TerritoryInput): TerritoryDraft {
  const draft: TerritoryDraft = {
    id: territory.id,
    name: territory.name,
    world: territory.world || 'world',
    defaultZoneType: territory.defaultZoneType || 'WILDERNESS',
    boundary: cloneBoundary(territory.boundary),
    zones: (territory.zones ?? []).map((zone): ZoneDraft => ({
      id: zone.id,
      name: zone.name,
      type: zone.type || 'CLAIMABLE',
      priority: zone.priority ?? 0,
      boundary: cloneBoundary(zone.boundary),
    })),
    dirty: false,
  };
  if (territory.governedByGuildId !== undefined) {
    draft.governedByGuildId = territory.governedByGuildId;
  }
  if (territory.government !== undefined) {
    draft.government = cloneValue(territory.government);
  }
  if (territory.policies !== undefined) {
    draft.policies = territory.policies.map(cloneValue);
  }
  return draft;
}

export function loadTerritories(
  response: { territories: readonly TerritoryInput[] } | readonly TerritoryInput[],
): EditorState {
  const source = 'territories' in response ? response.territories : response;
  return {
    territories: source.map(cloneTerritory),
    dirtyIds: new Set<string>(),
    selection: null,
  };
}

function cloneSelection(selection: Selection | null): Selection | null {
  return selection ? { ...selection } : null;
}

function cloneState(state: EditorState): EditorState {
  return {
    territories: state.territories.map(cloneTerritory),
    dirtyIds: new Set(state.dirtyIds),
    selection: cloneSelection(state.selection),
  };
}

export function markDirty(state: EditorState, territoryId: string): EditorState {
  const next = cloneState(state);
  const dirtyIds = new Set(next.dirtyIds);
  dirtyIds.add(territoryId);
  const territory = next.territories.find(({ id }) => id === territoryId);
  if (territory) territory.dirty = true;
  return { ...next, dirtyIds };
}

export function clearDirty(state: EditorState, territoryId: string): EditorState {
  const next = cloneState(state);
  const dirtyIds = new Set(next.dirtyIds);
  dirtyIds.delete(territoryId);
  const territory = next.territories.find(({ id }) => id === territoryId);
  if (territory) territory.dirty = false;
  return { ...next, dirtyIds };
}

export function isDirty(state: EditorState): boolean {
  return state.dirtyIds.size > 0;
}

export function findTerritory(state: EditorState, id: string): TerritoryDraft | null {
  return state.territories.find((territory) => territory.id === id) ?? null;
}

export function getBoundaryTarget(
  state: EditorState,
  selection: Selection | null,
): { territory: TerritoryDraft; zone: ZoneDraft | null; boundary: Boundary } | null {
  if (!selection) return null;
  const territory = findTerritory(state, selection.territoryId);
  if (!territory) return null;
  if (selection.zoneId) {
    const zone = territory.zones.find(({ id }) => id === selection.zoneId);
    return zone ? { territory, zone, boundary: zone.boundary } : null;
  }
  return { territory, zone: null, boundary: territory.boundary };
}

type TerritoryPatch = Partial<Omit<TerritoryDraft, 'zones' | 'boundary'>> & {
  boundary?: BoundaryInput;
  zones?: readonly ZoneDocument[];
};

export function updateTerritory(
  state: EditorState,
  territoryId: string,
  patch: TerritoryPatch | ((territory: TerritoryDraft) => TerritoryPatch),
): EditorState {
  const next = cloneState(state);
  const index = next.territories.findIndex(({ id }) => id === territoryId);
  if (index < 0) return state;
  const current = next.territories[index];
  const changes = typeof patch === 'function' ? patch(current) : patch;
  const updated: TerritoryDraft = {
    ...current,
    ...changes,
    boundary: changes.boundary ? cloneBoundary(changes.boundary) : current.boundary,
    zones: changes.zones ? changes.zones.map((zone) => ({
      id: zone.id,
      name: zone.name,
      type: zone.type,
      priority: zone.priority,
      boundary: cloneBoundary(zone.boundary),
    })) : current.zones,
    dirty: true,
  };
  if (changes.government !== undefined) updated.government = cloneValue(changes.government);
  if (changes.policies !== undefined) updated.policies = changes.policies.map(cloneValue);
  next.territories[index] = updated;
  const dirtyIds = new Set(next.dirtyIds);
  dirtyIds.add(territoryId);
  return { ...next, dirtyIds };
}

type ZonePatch = Partial<Omit<ZoneDraft, 'boundary'>> & { boundary?: BoundaryInput };

export function updateZone(
  state: EditorState,
  territoryId: string,
  zoneId: string,
  patch: ZonePatch | ((zone: ZoneDraft) => ZonePatch),
): EditorState {
  const next = cloneState(state);
  const territoryIndex = next.territories.findIndex(({ id }) => id === territoryId);
  if (territoryIndex < 0) return state;
  const territory = next.territories[territoryIndex];
  const zoneIndex = territory.zones.findIndex(({ id }) => id === zoneId);
  if (zoneIndex < 0) return state;
  const current = territory.zones[zoneIndex];
  const changes = typeof patch === 'function' ? patch(current) : patch;
  territory.zones[zoneIndex] = {
    ...current,
    ...changes,
    boundary: changes.boundary ? cloneBoundary(changes.boundary) : current.boundary,
  };
  territory.dirty = true;
  const dirtyIds = new Set(next.dirtyIds);
  dirtyIds.add(territoryId);
  return { ...next, dirtyIds };
}

function chunkKey(chunk: { x: number; z: number }): string {
  return `${chunk.x},${chunk.z}`;
}

export function toggleChunk(boundary: Boundary, x: number, z: number): Boundary {
  const chunks = boundary.chunks.map(({ x: cx, z: cz }) => ({ x: cx, z: cz }));
  const index = chunks.findIndex((chunk) => chunk.x === x && chunk.z === z);
  if (index >= 0) chunks.splice(index, 1);
  else chunks.push({ x, z });
  return { polygon: boundary.polygon.map(({ x: px, z: pz }) => ({ x: px, z: pz })), chunks };
}

export function addChunkRect(boundary: Boundary, start: { x: number; z: number }, end: { x: number; z: number }): Boundary {
  const chunks = boundary.chunks.map(({ x, z }) => ({ x, z }));
  const present = new Set(chunks.map(chunkKey));
  const minX = Math.min(start.x, end.x);
  const maxX = Math.max(start.x, end.x);
  const minZ = Math.min(start.z, end.z);
  const maxZ = Math.max(start.z, end.z);
  for (let x = minX; x <= maxX; x += 1) {
    for (let z = minZ; z <= maxZ; z += 1) {
      const key = `${x},${z}`;
      if (!present.has(key)) {
        chunks.push({ x, z });
        present.add(key);
      }
    }
  }
  return {
    polygon: boundary.polygon.map(({ x, z }) => ({ x, z })),
    chunks,
  };
}

export function eraseChunk(boundary: Boundary, x: number, z: number): Boundary {
  return {
    polygon: boundary.polygon.map(({ x: px, z: pz }) => ({ x: px, z: pz })),
    chunks: boundary.chunks.filter((chunk) => chunk.x !== x || chunk.z !== z).map(({ x: cx, z: cz }) => ({ x: cx, z: cz })),
  };
}

export function removePolygonVertexNear(boundary: Boundary, x: number, z: number, threshold = 8): Boundary {
  const thresholdSquared = threshold * threshold;
  let bestIndex = -1;
  let bestDistance = thresholdSquared;
  for (let index = 0; index < boundary.polygon.length; index += 1) {
    const vertex = boundary.polygon[index];
    const dx = vertex.x - x;
    const dz = vertex.z - z;
    const distance = dx * dx + dz * dz;
    if (distance <= bestDistance) {
      bestDistance = distance;
      bestIndex = index;
    }
  }
  const polygon = boundary.polygon.map(({ x: px, z: pz }) => ({ x: px, z: pz }));
  if (bestIndex >= 0) polygon.splice(bestIndex, 1);
  return { polygon, chunks: boundary.chunks.map(({ x: cx, z: cz }) => ({ x: cx, z: cz })) };
}

export function boundaryStats(boundary: Boundary): { chunks: number; vertices: number } {
  return { chunks: boundary.chunks.length, vertices: boundary.polygon.length };
}

export function hasGeometry(boundary: Boundary): boolean {
  return boundary.polygon.length >= 3 || boundary.chunks.length >= 1;
}

export interface ApiChunkDocument {
  cx: number;
  cz: number;
}

export interface ApiBoundaryDocument {
  polygon: BlockPoint[];
  chunks: ApiChunkDocument[];
}

export type ApiTerritoryDocument = Omit<TerritoryDocument, 'boundary' | 'zones'> & {
  boundary: ApiBoundaryDocument;
  zones: Array<Omit<ZoneDocument, 'boundary'> & { boundary: ApiBoundaryDocument }>;
};

function toApiBoundary(boundary: Boundary): ApiBoundaryDocument {
  return {
    polygon: boundary.polygon.map(({ x, z }) => ({ x, z })),
    chunks: boundary.chunks.map(({ x, z }) => ({ cx: x, cz: z })),
  };
}

export function toApiDocument(territory: TerritoryDraft | TerritoryDocument): ApiTerritoryDocument {
  const document: ApiTerritoryDocument = {
    id: territory.id,
    name: territory.name,
    world: territory.world,
    defaultZoneType: territory.defaultZoneType || 'WILDERNESS',
    boundary: toApiBoundary(territory.boundary),
    zones: territory.zones.map((zone) => ({
      id: zone.id,
      name: zone.name,
      type: zone.type,
      priority: zone.priority ?? 0,
      boundary: toApiBoundary(zone.boundary),
    })),
  };
  if (territory.governedByGuildId !== undefined) document.governedByGuildId = territory.governedByGuildId;
  if (territory.government !== undefined) document.government = cloneValue(territory.government);
  if (territory.policies !== undefined) document.policies = territory.policies.map(cloneValue);
  return document;
}

export function newTerritoryId(name: string): string {
  return String(name || 'territory')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '') || 'territory';
}
