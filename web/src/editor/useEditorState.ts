import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, guildsApi } from './api';
import {
  clearDirty,
  cloneTerritory,
  emptyBoundary,
  findTerritory,
  getBoundaryTarget,
  hasGeometry,
  loadTerritories,
  newTerritoryId,
  toApiDocument,
  updateTerritory,
  updateZone,
} from './model';
import type {
  Boundary,
  EditorMeta,
  EditorState,
  GuildsApi,
  Selection,
  TerritoryDraft,
  TerritoryDocument,
  ZoneDraft,
} from './types';

export type EditorStatus = 'loading' | 'login-required' | 'ready' | 'offline';

type SelectedPatch = Record<string, unknown>;

export interface EditorController {
  status: EditorStatus;
  meta: EditorMeta | null;
  state: EditorState;
  selection: Selection | null;
  error: string | null;
  savingIds: ReadonlySet<string>;
  login(token: string): Promise<void>;
  logout(): Promise<void>;
  retry(): Promise<void>;
  select(selection: Selection | null): void;
  createTerritory(name: string, world?: string): void;
  createZone(name: string, territoryId?: string): void;
  updateSelected(patch: SelectedPatch): void;
  updateBoundary(boundary: Boundary): void;
  save(territoryId?: string): Promise<void>;
  removeSelected(): Promise<void>;
}

const EMPTY_STATE = loadTerritories([]);

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (typeof error.body === 'object' && error.body !== null) {
      const body = error.body as Record<string, unknown>;
      if (typeof body.message === 'string' && body.message.trim()) return body.message;
      if (typeof body.error === 'string' && body.error.trim()) return body.error;
    }
    if (error.message && !error.message.includes(String(error.status))) return error.message;
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

function isTerritoryDocument(value: unknown): value is TerritoryDocument {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as Partial<TerritoryDocument>;
  return typeof candidate.id === 'string'
    && typeof candidate.name === 'string'
    && typeof candidate.world === 'string'
    && candidate.boundary !== undefined
    && Array.isArray(candidate.zones);
}
function isTerritoryList(value: unknown): value is { territories: TerritoryDocument[] } {
  if (value === null || typeof value !== 'object' || !('territories' in value)) return false;
  return Array.isArray(value.territories) && value.territories.every(isTerritoryDocument);
}

function isEditorMeta(value: unknown): value is EditorMeta {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as Partial<EditorMeta>;
  return typeof candidate.authRequired === 'boolean'
    && typeof candidate.squaremapTileBaseUrl === 'string'
    && typeof candidate.sessionTtlSeconds === 'number'
    && typeof candidate.secure === 'boolean';
}


function mergeLoadedState(
  previous: EditorState,
  response: { territories: TerritoryDocument[] },
  preserveDrafts: boolean,
): EditorState {
  const loaded = loadTerritories(response);
  const localDrafts = new Map(
    previous.territories
      .filter((territory) => previous.dirtyIds.has(territory.id))
      .map((territory) => [territory.id, territory]),
  );
  if (preserveDrafts && localDrafts.size > 0) {
    const seen = new Set<string>();
    loaded.territories = loaded.territories.map((territory) => {
      const local = localDrafts.get(territory.id);
      seen.add(territory.id);
      if (!local) return territory;
      const draft = cloneTerritory(local);
      draft.dirty = true;
      return draft;
    });
    for (const local of localDrafts.values()) {
      if (seen.has(local.id)) continue;
      const draft = cloneTerritory(local);
      draft.dirty = true;
      loaded.territories.push(draft);
    }
    loaded.dirtyIds = new Set(previous.dirtyIds);
  }
  const selected = previous.selection;
  loaded.selection = selected && loaded.territories.some((territory) => {
    if (territory.id !== selected.territoryId) return false;
    return !selected.zoneId || territory.zones.some((zone) => zone.id === selected.zoneId);
  })
    ? { ...selected }
    : loaded.territories[0]
      ? { territoryId: loaded.territories[0].id }
      : null;
  return loaded;
}

function setSaving(set: ReadonlySet<string>, id: string, saving: boolean): Set<string> {
  const next = new Set(set);
  if (saving) next.add(id);
  else next.delete(id);
  return next;
}

export function useEditorState(api: GuildsApi = guildsApi): EditorController {
  const [status, setStatus] = useState<EditorStatus>('loading');
  const [meta, setMeta] = useState<EditorMeta | null>(null);
  const [state, setState] = useState<EditorState>(() => ({
    territories: EMPTY_STATE.territories,
    dirtyIds: new Set(EMPTY_STATE.dirtyIds),
    selection: null,
  }));
  const [error, setError] = useState<string | null>(null);
  const [savingIds, setSavingIds] = useState<ReadonlySet<string>>(() => new Set());
  const savingRef = useRef(new Set<string>());
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const load = useCallback(async (preserveDrafts: boolean) => {
    if (!mountedRef.current) return;
    setStatus('loading');
    setError(null);
    try {
      const nextMeta = await api.getMeta();
      if (!mountedRef.current) return;
      if (!isEditorMeta(nextMeta)) throw new Error('The Guilds API returned an invalid response.');
      setMeta(nextMeta);
      const response = await api.listTerritories();
      if (!mountedRef.current) return;
      if (!isTerritoryList(response)) throw new Error('The Guilds API returned an invalid response.');
      setState((previous) => mergeLoadedState(previous, response, preserveDrafts));
      setStatus('ready');
    } catch (loadError) {
      if (!mountedRef.current) return;
      if (loadError instanceof ApiError && loadError.status === 401) {
        setStatus('login-required');
        setError(null);
      } else {
        setStatus('offline');
        setError(errorMessage(loadError, 'The Guilds API is unavailable.'));
      }
    }
  }, [api]);

  useEffect(() => {
    void load(false);
  }, [load]);

  useEffect(() => {
    if (state.dirtyIds.size === 0) return undefined;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [state.dirtyIds]);

  const login = useCallback(async (token: string) => {
    try {
      await api.login(token);
    } catch (loginError) {
      if (mountedRef.current) {
        setStatus('login-required');
        setError('Invalid token');
      }
      throw loginError;
    }
    await load(true);
    if (mountedRef.current) setError(null);
  }, [api, load]);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // A session may already be expired; the local auth transition still applies.
    }
    if (!mountedRef.current) return;
    setStatus(meta?.authRequired ? 'login-required' : 'ready');
    setError(null);
  }, [api, meta?.authRequired]);

  const select = useCallback((selection: Selection | null) => {
    setState((previous) => ({
      ...previous,
      selection: selection ? { ...selection } : null,
    }));
  }, []);

  const createTerritory = useCallback((name: string, world = 'world') => {
    const cleanName = name.trim();
    if (!cleanName) return;
    setState((previous) => {
      const used = new Set(previous.territories.map((territory) => territory.id));
      const base = newTerritoryId(cleanName);
      let id = base;
      let suffix = 2;
      while (used.has(id)) id = `${base}-${suffix++}`;
      const territory: TerritoryDraft = {
        id,
        name: cleanName,
        world: world || 'world',
        defaultZoneType: 'WILDERNESS',
        boundary: emptyBoundary(),
        zones: [],
        dirty: true,
      };
      const dirtyIds = new Set(previous.dirtyIds);
      dirtyIds.add(id);
      return {
        territories: [...previous.territories, territory],
        dirtyIds,
        selection: { territoryId: id },
      };
    });
  }, []);

  const createZone = useCallback((name: string, territoryId?: string) => {
    const cleanName = name.trim();
    if (!cleanName) return;
    setState((previous) => {
      const parentId = territoryId ?? previous.selection?.territoryId;
      if (!parentId) return previous;
      const parent = findTerritory(previous, parentId);
      if (!parent) return previous;
      const used = new Set(parent.zones.map((zone) => zone.id));
      const base = newTerritoryId(cleanName);
      let id = base;
      let suffix = 2;
      while (used.has(id)) id = `${base}-${suffix++}`;
      const zone: ZoneDraft = {
        id,
        name: cleanName,
        type: 'CLAIMABLE',
        priority: 0,
        boundary: emptyBoundary(),
      };
      const next = updateTerritory(previous, parentId, {
        zones: [...parent.zones, zone],
      });
      return {
        ...next,
        selection: { territoryId: parentId, zoneId: id },
      };
    });
  }, []);

  const updateSelected = useCallback((patch: SelectedPatch) => {
    setState((previous) => {
      const selection = previous.selection;
      if (!selection) return previous;
      if (selection.zoneId) {
        return updateZone(previous, selection.territoryId, selection.zoneId, patch as never);
      }
      return updateTerritory(previous, selection.territoryId, patch as never);
    });
  }, []);

  const updateBoundary = useCallback((boundary: Boundary) => {
    setState((previous) => {
      const target = getBoundaryTarget(previous, previous.selection);
      if (!target) return previous;
      if (target.zone) {
        return updateZone(previous, target.territory.id, target.zone.id, { boundary });
      }
      return updateTerritory(previous, target.territory.id, { boundary });
    });
  }, []);

  const save = useCallback(async (territoryId?: string) => {
    const id = territoryId ?? state.selection?.territoryId;
    if (!id) {
      setError('Select a territory before saving.');
      return;
    }
    if (savingRef.current.has(id)) return;
    const territory = findTerritory(state, id);
    if (!territory) {
      setError('The selected territory no longer exists.');
      return;
    }
    if (!hasGeometry(territory.boundary)) {
      const validation = 'Outer boundary needs at least three polygon vertices or one chunk.';
      setError(validation);
      throw new Error(validation);
    }
    const emptyZone = territory.zones.find((zone) => !hasGeometry(zone.boundary));
    if (emptyZone) {
      const validation = `Zone ${emptyZone.name || emptyZone.id} needs geometry before saving.`;
      setError(validation);
      throw new Error(validation);
    }
    savingRef.current.add(id);
    setSavingIds((previous) => setSaving(previous, id, true));
    setError(null);
    try {
      const response = await api.putTerritory(toApiDocument(territory));
      if (!mountedRef.current) return;
      setState((previous) => {
        if (isTerritoryDocument(response)) {
          const replacement = cloneTerritory(response);
          replacement.dirty = false;
          return {
            ...clearDirty(previous, id),
            territories: previous.territories.map((item) => item.id === id ? replacement : item),
          };
        }
        return clearDirty(previous, id);
      });
    } catch (saveError) {
      if (mountedRef.current) {
        if (saveError instanceof ApiError && saveError.status === 401) setStatus('login-required');
        setError(errorMessage(saveError, 'Save failed.'));
      }
      throw saveError;
    } finally {
      savingRef.current.delete(id);
      if (mountedRef.current) setSavingIds((previous) => setSaving(previous, id, false));
    }
  }, [api, state]);

  const removeSelected = useCallback(async () => {
    const selection = state.selection;
    if (!selection) return;
    if (selection.zoneId) {
      setState((previous) => {
        const territory = findTerritory(previous, selection.territoryId);
        if (!territory) return previous;
        const nextZones = territory.zones.filter((zone) => zone.id !== selection.zoneId);
        return updateTerritory({
          ...previous,
          territories: previous.territories.map((item) => cloneTerritory(item)),
          dirtyIds: new Set(previous.dirtyIds),
        }, territory.id, { zones: nextZones });
      });
      select({ territoryId: selection.territoryId });
      return;
    }
    if (savingRef.current.has(selection.territoryId)) return;
    savingRef.current.add(selection.territoryId);
    setSavingIds((previous) => setSaving(previous, selection.territoryId, true));
    try {
      await api.deleteTerritory(selection.territoryId);
      if (!mountedRef.current) return;
      setState((previous) => {
        const territories = previous.territories.filter((territory) => territory.id !== selection.territoryId);
        const dirtyIds = new Set(previous.dirtyIds);
        dirtyIds.delete(selection.territoryId);
        return {
          territories,
          dirtyIds,
          selection: territories[0] ? { territoryId: territories[0].id } : null,
        };
      });
      setError(null);
    } catch (removeError) {
      if (mountedRef.current) {
        if (removeError instanceof ApiError && removeError.status === 401) setStatus('login-required');
        setError(errorMessage(removeError, 'Delete failed.'));
      }
      throw removeError;
    } finally {
      savingRef.current.delete(selection.territoryId);
      if (mountedRef.current) setSavingIds((previous) => setSaving(previous, selection.territoryId, false));
    }
  }, [api, select, state]);

  return {
    status,
    meta,
    state,
    selection: state.selection,
    error,
    savingIds,
    login,
    logout,
    retry: () => load(true),
    select,
    createTerritory,
    createZone,
    updateSelected,
    updateBoundary,
    save,
    removeSelected,
  };
}
