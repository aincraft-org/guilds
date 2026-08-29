import { describe, expect, it } from 'vitest';
import {
  addChunkRect,
  boundaryStats,
  clearDirty,
  eraseChunk,
  hasGeometry,
  loadTerritories,
  markDirty,
  newTerritoryId,
  removePolygonVertexNear,
  toApiDocument,
  toggleChunk,
  updateTerritory,
  updateZone,
} from './model';
import type { Boundary, TerritoryDocument } from './types';

const boundary: Boundary = {
  polygon: [{ x: 0, z: 0 }, { x: 16, z: 0 }, { x: 16, z: 16 }],
  chunks: [{ x: -1, z: 2 }],
};

const document: TerritoryDocument = {
  id: 'town', name: 'Town', world: 'world', defaultZoneType: 'WILDERNESS', boundary,
  zones: [{ id: 'core', name: 'Core', type: 'CLAIMABLE', priority: 2, boundary: { polygon: [], chunks: [] } }],
  governedByGuildId: 'guild-1', government: { form: 'council', seats: [{ id: 1 }] },
  policies: [{ id: 'tax', effects: { rate: 0.1 } }],
};

describe('editor model', () => {
  it('loads cx/cz API chunks into internal x/z chunks without aliasing input', () => {
    const input = {
      territories: [{ ...document, boundary: { polygon: document.boundary.polygon, chunks: [{ cx: -2, cz: 4 }] } }],
    };
    const state = loadTerritories(input);
    expect(state.territories[0].boundary.chunks).toEqual([{ x: -2, z: 4 }]);
    expect(state.territories[0].dirty).toBe(false);
    state.territories[0].boundary.polygon[0].x = 99;
    expect(input.territories[0].boundary.polygon[0].x).toBe(0);
  });

  it('updates immutably and tracks dirty parent territories', () => {
    const state = loadTerritories({ territories: [document] });
    const next = updateTerritory(state, 'town', { name: 'New name' });
    expect(next).not.toBe(state);
    expect(next.territories[0].name).toBe('New name');
    expect(state.territories[0].name).toBe('Town');
    expect(next.dirtyIds.has('town')).toBe(true);

    const zoneNext = updateZone(next, 'town', 'core', { priority: 9 });
    expect(zoneNext.territories[0].zones[0].priority).toBe(9);
    expect(next.territories[0].zones[0].priority).toBe(2);
  });

  it('marks and clears dirty state immutably', () => {
    const state = loadTerritories({ territories: [document] });
    const dirty = markDirty(state, 'town');
    const clean = clearDirty(dirty, 'town');
    expect(dirty.dirtyIds.has('town')).toBe(true);
    expect(state.dirtyIds.size).toBe(0);
    expect(clean.dirtyIds.size).toBe(0);
    expect(clean.territories[0].dirty).toBe(false);
  });

  it('toggles chunks and a second toggle restores the boundary', () => {
    const once = toggleChunk(boundary, 3, 4);
    const twice = toggleChunk(once, 3, 4);
    expect(once.chunks).toContainEqual({ x: 3, z: 4 });
    expect(twice).toEqual(boundary);
    expect(boundary.chunks).toEqual([{ x: -1, z: 2 }]);
  });

  it('adds an inclusive rectangle and erases one chunk immutably', () => {
    const added = addChunkRect({ polygon: [], chunks: [] }, { x: 1, z: 2 }, { x: 3, z: 4 });
    expect(added.chunks).toHaveLength(9);
    const erased = eraseChunk(added, 2, 3);
    expect(erased.chunks).toHaveLength(8);
    expect(added.chunks).toHaveLength(9);
  });

  it('removes only the nearest vertex within the threshold', () => {
    const original = { polygon: [{ x: 0, z: 0 }, { x: 10, z: 0 }], chunks: [] };
    expect(removePolygonVertexNear(original, 3, 4, 5).polygon).toEqual([{ x: 10, z: 0 }]);
    expect(removePolygonVertexNear(original, 3, 4, 4.9).polygon).toHaveLength(2);
    expect(original.polygon).toHaveLength(2);
  });

  it('requires three polygon vertices or one chunk for geometry', () => {
    expect(hasGeometry({ polygon: [], chunks: [] })).toBe(false);
    expect(hasGeometry({ polygon: [{ x: 0, z: 0 }, { x: 1, z: 0 }], chunks: [] })).toBe(false);
    expect(hasGeometry({ polygon: [{ x: 0, z: 0 }, { x: 1, z: 0 }, { x: 0, z: 1 }], chunks: [] })).toBe(true);
    expect(hasGeometry({ polygon: [], chunks: [{ x: 0, z: 0 }] })).toBe(true);
    expect(boundaryStats(boundary)).toEqual({ chunks: 1, vertices: 3 });
  });

  it('serializes backend cx/cz chunks, omits dirty, and preserves optional fields', () => {
    const draft = loadTerritories({ territories: [document] }).territories[0];
    const apiDocument = toApiDocument(draft);
    expect(apiDocument.boundary.chunks).toEqual([{ cx: -1, cz: 2 }]);
    expect(apiDocument.zones[0].boundary.chunks).toEqual([]);
    expect(apiDocument).not.toHaveProperty('dirty');
    expect(apiDocument.governedByGuildId).toBe('guild-1');
    expect(apiDocument.government).toEqual(document.government);
    expect(apiDocument.policies).toEqual(document.policies);
    apiDocument.government = { form: 'changed' };
    expect(draft.government).toEqual(document.government);
  });

  it('creates a stable slug identifier', () => {
    expect(newTerritoryId('The High Hills!')).toBe('the-high-hills');
    expect(newTerritoryId('')).toBe('territory');
  });
});
