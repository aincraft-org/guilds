import * as api from './api.js';
import {
  clearDirty,
  emptyBoundary,
  findTerritory,
  getBoundaryTarget,
  hasGeometry,
  isDirty,
  loadFromApi,
  markDirty,
  newTerritoryId,
  toApiDocument,
} from './model.js';
import { bukkitToSquaremapWorld, createEditorMap } from './map.js';
import { createTools } from './tools.js';
import {
  bindToolbar,
  fillWorlds,
  renderInspector,
  renderTree,
  setLoginVisible,
  setSessionStatus,
  showBanner,
} from './ui.js';

const state = { territories: [], dirtyIds: new Set() };
let selection = null;
let meta = { authRequired: false, squaremapTileBaseUrl: '' };
let mapApi;
let tools;

function currentWorld() {
  return document.getElementById('world-select').value || 'world';
}

function getTarget() {
  return getBoundaryTarget(state, selection);
}

function refreshMap() {
  mapApi.clearShapes();
  const world = currentWorld();
  for (const t of state.territories) {
    if (t.world !== world) continue;
    const selected = selection && selection.territoryId === t.id && !selection.zoneId;
    mapApi.renderBoundary(t.boundary, {
      color: selected ? '#7ec8ff' : '#5b9fd4',
      weight: selected ? 3 : 2,
      fillColor: '#5b9fd4',
      fillOpacity: selected ? 0.28 : 0.15,
    });
    for (const z of t.zones || []) {
      const zSel = selection && selection.territoryId === t.id && selection.zoneId === z.id;
      const claimable = z.type === 'CLAIMABLE';
      mapApi.renderBoundary(z.boundary, {
        color: zSel ? '#fff3a0' : claimable ? '#e6c15a' : '#7ec87e',
        weight: zSel ? 3 : 2,
        fillColor: claimable ? '#e6c15a' : '#7ec87e',
        fillOpacity: zSel ? 0.35 : 0.2,
      });
    }
  }
  if (!mapApi.isBasemapOk() && meta.squaremapTileBaseUrl) {
    showBanner('Basemap tiles unavailable — chunk grid still works', false);
  }
}

function refreshUi() {
  fillWorlds(state, currentWorld());
  renderTree(state, selection, (sel) => {
    selection = sel;
    refreshUi();
  });
  renderInspector(getTarget(), selection);
  refreshMap();
  setSessionStatus(
    isDirty(state)
      ? `Unsaved: ${[...state.dirtyIds].join(', ')}`
      : 'No unsaved changes'
  );
}

async function loadData() {
  const json = await api.listTerritories();
  const loaded = loadFromApi(json);
  state.territories = loaded.territories;
  state.dirtyIds = loaded.dirtyIds;
  selection = state.territories[0]
    ? { territoryId: state.territories[0].id, zoneId: null }
    : null;
  refreshUi();
}

async function bootstrap() {
  meta = await api.getMeta();
  mapApi = createEditorMap('map');
  mapApi.setTileBase(
    meta.squaremapTileBaseUrl || '',
    bukkitToSquaremapWorld(currentWorld())
  );
  mapApi.centerOn(0, 0, 3);

  tools = createTools(mapApi, getTarget, (tid) => {
    markDirty(state, tid);
    refreshUi();
  });

  bindToolbar((tool) => tools.setTool(tool));

  mapApi.map.on('mousemove', (e) => {
    const p = mapApi.toPoint(e.latlng);
    const cx = Math.floor(p.x / 16);
    const cz = Math.floor(p.y / 16);
    document.getElementById('coords').textContent =
      `block ${Math.floor(p.x)}, ${Math.floor(p.y)} · chunk ${cx}, ${cz} · snap on`;
  });

  document.getElementById('world-select').addEventListener('change', () => {
    mapApi.setTileBase(
      meta.squaremapTileBaseUrl || '',
      bukkitToSquaremapWorld(currentWorld())
    );
    refreshUi();
  });

  document.getElementById('insp-name').addEventListener('change', (e) => {
    const t = getTarget();
    if (!t) return;
    if (selection.zoneId) t.zone.name = e.target.value;
    else t.territory.name = e.target.value;
    markDirty(state, t.territory.id);
    refreshUi();
  });
  document.getElementById('insp-type').addEventListener('change', (e) => {
    const t = getTarget();
    if (!t || !t.zone) return;
    t.zone.type = e.target.value;
    markDirty(state, t.territory.id);
    refreshUi();
  });

  document.getElementById('btn-add-territory').addEventListener('click', () => {
    const name = prompt('Territory name?');
    if (!name) return;
    let id = newTerritoryId(name);
    if (findTerritory(state, id)) id = id + '-' + Date.now().toString(36);
    const t = {
      id,
      name,
      world: currentWorld(),
      defaultZoneType: 'WILDERNESS',
      boundary: emptyBoundary(),
      zones: [],
      dirty: true,
    };
    state.territories.push(t);
    markDirty(state, id);
    selection = { territoryId: id, zoneId: null };
    tools.setTool('polygon');
    document.querySelectorAll('#toolbar .tool').forEach((b) => {
      b.classList.toggle('active', b.dataset.tool === 'polygon');
    });
    refreshUi();
    showBanner('Draw the outer boundary (polygon / paint / rect), then Save');
  });

  document.getElementById('btn-add-zone').addEventListener('click', () => {
    if (!selection) {
      showBanner('Select a territory first', true);
      return;
    }
    const parent = findTerritory(state, selection.territoryId);
    if (!parent) return;
    const name = prompt('Zone name?');
    if (!name) return;
    let id = newTerritoryId(name);
    if (parent.zones.some((z) => z.id === id)) id = id + '-' + Date.now().toString(36);
    parent.zones.push({
      id,
      name,
      type: 'CLAIMABLE',
      priority: 10,
      boundary: emptyBoundary(),
    });
    markDirty(state, parent.id);
    selection = { territoryId: parent.id, zoneId: id };
    tools.setTool('paint');
    document.querySelectorAll('#toolbar .tool').forEach((b) => {
      b.classList.toggle('active', b.dataset.tool === 'paint');
    });
    refreshUi();
  });

  document.getElementById('btn-delete').addEventListener('click', async () => {
    const t = getTarget();
    if (!t) return;
    if (selection.zoneId) {
      if (!confirm(`Delete zone ${selection.zoneId}?`)) return;
      t.territory.zones = t.territory.zones.filter((z) => z.id !== selection.zoneId);
      selection = { territoryId: t.territory.id, zoneId: null };
      markDirty(state, t.territory.id);
      refreshUi();
      return;
    }
    if (!confirm(`Delete territory ${t.territory.id}? This calls the API immediately.`)) return;
    try {
      await api.deleteTerritory(t.territory.id);
      state.territories = state.territories.filter((x) => x.id !== t.territory.id);
      clearDirty(state, t.territory.id);
      selection = null;
      showBanner('Deleted');
      refreshUi();
    } catch (err) {
      if (err.status === 401) {
        setLoginVisible(true);
        showBanner('Session expired — sign in again', true);
      } else {
        showBanner(err.message || 'Delete failed', true);
      }
    }
  });

  document.getElementById('btn-logout').addEventListener('click', async () => {
    try {
      await api.logout();
    } catch {
      /* ignore */
    }
    if (meta.authRequired) {
      setLoginVisible(true);
      setSessionStatus('Signed out');
      showBanner('Signed out');
    }
  });

  document.getElementById('btn-save').addEventListener('click', async () => {
    // Prefer all dirty territories; fall back to current selection
    let ids = [...state.dirtyIds];
    if (ids.length === 0 && selection) ids = [selection.territoryId];
    if (ids.length === 0) {
      showBanner('Nothing to save');
      return;
    }
    for (const id of ids) {
      const t = findTerritory(state, id);
      if (!t) continue;
      if (!hasGeometry(t.boundary) && (!t.zones || t.zones.length === 0)) {
        showBanner(`Territory ${id}: draw at least one chunk or polygon`, true);
        return;
      }
      if (!hasGeometry(t.boundary)) {
        showBanner(`Territory ${id}: outer boundary is empty`, true);
        return;
      }
      for (const z of t.zones || []) {
        if (!hasGeometry(z.boundary)) {
          showBanner(`Zone ${z.id}: draw geometry before save`, true);
          return;
        }
      }
      try {
        await api.putTerritory(id, toApiDocument(t));
        clearDirty(state, id);
        showBanner(`Saved ${id}`);
      } catch (err) {
        if (err.status === 401) {
          setLoginVisible(true);
          showBanner('Session expired — sign in again', true);
          return;
        }
        showBanner(err.message || 'Save failed', true);
        return;
      }
    }
    refreshUi();
  });

  document.getElementById('btn-login').addEventListener('click', async () => {
    const token = document.getElementById('login-token').value;
    const errEl = document.getElementById('login-error');
    errEl.classList.add('hidden');
    try {
      await api.login(token);
      setLoginVisible(false);
      await loadData();
      showBanner('Signed in');
    } catch {
      errEl.textContent = 'Invalid token';
      errEl.classList.remove('hidden');
    }
  });

  window.addEventListener('beforeunload', (e) => {
    if (isDirty(state)) {
      e.preventDefault();
      e.returnValue = '';
    }
  });

  if (meta.authRequired) {
    setLoginVisible(true);
    setSessionStatus('Sign in required');
    // Try loading in case a valid cookie already exists
    try {
      await loadData();
      setLoginVisible(false);
    } catch (err) {
      if (err.status !== 401) showBanner(err.message, true);
    }
  } else {
    setLoginVisible(false);
    try {
      await loadData();
    } catch (err) {
      showBanner(err.message || 'Failed to load territories', true);
    }
  }
}

bootstrap().catch((e) => {
  console.error(e);
  showBanner(e.message || String(e), true);
});
